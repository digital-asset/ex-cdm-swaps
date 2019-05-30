// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.app.bot

import com.daml.ledger.rxjava.components.LedgerViewFlowable
import com.daml.ledger.javaapi.data._
import com.digitalasset.app.LedgerClient
import com.digitalasset.app.utils.Cdm
import com.digitalasset.app.utils.Record._

import scala.collection.JavaConverters._

// Bot which cleans up old derived events
class DerivedEvents(party: String, ledgerClient: LedgerClient) extends Bot(party, ledgerClient) {

  def name: String = "Bot - DerivedEvents " + party
  def templateFilter = Set("ContractInstance",  "DerivedEvent")

  def run(getTid: String => Identifier, ledgerView: LedgerViewFlowable.LedgerView[Record]): List[Command] = {
    val ciTid       = getTid("ContractInstance")
    val edTid       = getTid("DerivedEvent")

    val cis             = ledgerView.getContracts(ciTid).asScala.toList
    val contractIdt2eds = ledgerView.getContracts(edTid).asScala.toList.groupBy(_._2.get[Record]("contractIdentifier"))

    val ciCid2contractIdt =
      cis.flatMap { ci =>
        val parties = ci._2.getList[Record]("ps")
        val idts = ci._2.get[Record]("d").getList[Record]("contractIdentifier")
        Cdm.getIdentifierByParty(party, parties, idts).map(x => (ci._1, x))
      }.toMap

    // Remove results if corresponding contract does not exist anymore
    val cmds =
      contractIdt2eds.flatMap{
        case (contractIdt, edsGrouped) if !ciCid2contractIdt.values.toList.contains(contractIdt) =>
          logger.info("Cleaning up result ...")
          edsGrouped.map(ed => new ExerciseCommand(edTid, ed._1, "Archive", Cdm.archiveArg))

        case _ => List()
      }.toList

    cmds
  }
}
