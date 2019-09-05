// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.app.integration

import com.daml.ledger.javaapi.data.Record.Field
import com.daml.ledger.javaapi.data._
import com.digitalasset.app.LedgerClient
import com.digitalasset.app.utils.Cdm
import com.digitalasset.app.utils.Record._
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._

class MarketSetup(party: String, client: LedgerClient) {
  private def logger: Logger = LoggerFactory.getLogger("Integration - MarketSetup " + party)

  private val maps = new InMemoryDataStore(party, "MasterAgreementProposal", client)
  private val ctrs = new InMemoryDataStore(party, "CashTransferRequest", client)

  def run() = {
    val mapTid = client.getTemplateId("MasterAgreementProposal")
    val ctpTid  = client.getTemplateId("CashTransferRequest")

    // Accept MasterAgreementProposal
    val cmds1 =
      maps.getData().flatMap {
        case (cId, ma) if ma.get[Party]("p2").getValue == party =>
          logger.info("Accepting MasterAgreementProposal... ")
          Some(new ExerciseCommand(mapTid, cId, "Accept", Cdm.emptyArg))
        case _ => None
      }

    // Accept CashTransferProposal
    val cmds2 =
      ctrs.getData().flatMap {
        case (cId, ctp) =>
          logger.info("Accepting CashTransferRequest... ")
          val arg = new Record(List(
            new Field("accountTo", ctp.get[Text]("accountFrom"))
          ).asJava)
          Some(new ExerciseCommand(ctpTid, cId, "Accept", arg))
        case _ => None
      }

    client.sendCommands(party, cmds1 ++ cmds2)
  }
}