// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.app.bot

import java.util.concurrent.ConcurrentHashMap

import com.daml.ledger.rxjava.components.LedgerViewFlowable
import com.daml.ledger.javaapi.data.Record.Field
import com.daml.ledger.javaapi.data._
import com.digitalasset.app.LedgerClient
import com.digitalasset.app.utils.Cdm
import com.digitalasset.app.utils.Record._

import scala.collection.JavaConverters._
import scala.collection.mutable

// Bot which allocates cash to cash transfer instructions
class Cash(party: String, ledgerClient: LedgerClient) extends Bot(party, ledgerClient) {

  lazy val pendingEvent: mutable.Map[Record, Record] = new ConcurrentHashMap[Record, Record]().asScala

  def name: String = "Bot - Cash " + party
  def templateFilter = Set ("AllocateWorkflow", "CashTransferInstruction", "Cash")

  def run(getTid: String => Identifier, ledgerView: LedgerViewFlowable.LedgerView[Record]): List[Command] = {
    val wAllocTid   = getTid("AllocateWorkflow")
    val ctiTid      = getTid("CashTransferInstruction")
    val cashTid     = getTid("Cash")

    val wAlloc    = ledgerView.getContracts(wAllocTid).asScala.find(_._2.get[Party]("sig").getValue == party)
    val ctis      = ledgerView.getContracts(ctiTid).asScala.toList
    val cash      = ledgerView.getContracts(cashTid).asScala.toList

    // Allocate cash
    val cmds = {
      val ctisFiltered = ctis.filter { cti =>
        val cashTransfer = cti._2.get[Record]("d").get[Record]("cashTransfer")
        val payerReference = cashTransfer.get[Record]("payerReceiver").get[Record]("payerPartyReference").getOptional[Text]("reference").get.getValue
        val payer = Cdm.findPartyByReference(payerReference, cti._2.getList[Record]("ps"))
        val isPayer = payer.exists(_.getValue == party)
        isPayer && cti._2.getOptional[Value]("allocatedCashCid").isEmpty
      }

      val cashFiltered = cash.filter { c =>
        !ctis.exists(cti => cti._2.getOptional[ContractId]("allocatedCashCid").exists(_.getValue == c._1)) &&
          c._2.get[Party]("owner").getValue == party
      }

      wAlloc match {
        case Some(w) if ctisFiltered.nonEmpty && cashFiltered.nonEmpty =>
          val arg = new Record(List(
            new Field("ctiCids", new DamlList(ctisFiltered.map(x => new ContractId(x._1).asInstanceOf[Value]).asJava)),
            new Field("cashCids", new DamlList(cashFiltered.map(x => new ContractId(x._1).asInstanceOf[Value]).asJava))
          ).asJava)
          logger.info("Allocating...")
          List(new ExerciseCommand(wAllocTid, w._1, "Trigger", arg))
        case _ =>
          List()
      }
    }

    cmds
  }
}