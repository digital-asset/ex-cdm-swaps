// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.app.bot

import com.daml.ledger.javaapi.components.LedgerViewFlowable
import com.daml.ledger.javaapi.data.Record.Field
import com.daml.ledger.javaapi.data._
import com.digitalasset.app.LedgerClient
import com.digitalasset.app.utils.Cdm
import com.digitalasset.app.utils.Record._

import scala.collection.JavaConverters._

class MarketSetup(party: String, ledgerClient: LedgerClient) extends Bot(party, ledgerClient) {

  def name: String = "Bot - MarketSetup " + party
  def templateFilter = Set("MasterAgreementProposal", "CashTransferRequest")

  def run(getTid: String => Identifier, ledgerView: LedgerViewFlowable.LedgerView[Record]): List[Command] = {
    val mapTid = getTid("MasterAgreementProposal")
    val ctpTid  = getTid("CashTransferRequest")

    val maps = ledgerView.getContracts(mapTid).asScala.toList
    val ctps = ledgerView.getContracts(ctpTid).asScala.toList

    // Accept MasterAgreementProposal
    val cmds1 =
      maps.flatMap {
        case (cId, ma) if ma.get[Party]("p2").getValue == party =>
          logger.info("Accepting MasterAgreementProposal...")
          Some(new ExerciseCommand(mapTid, cId, "Accept", Cdm.emptyArg))
        case _ => None
      }

    // Accept CashTransferProposal
    val cmds2 =
      ctps.flatMap {
        case (cId, ctp) =>
          logger.info("Accepting CashTransferRequest...")
          val arg = new Record(List(
            new Field("accountTo", ctp.get[Text]("accountFrom"))
          ).asJava)
          Some(new ExerciseCommand(ctpTid, cId, "Accept", arg))
        case _ => None
      }

    cmds1 ++ cmds2
  }
}