// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.app.bot

import java.util.{Collections, UUID}

import com.daml.ledger.javaapi.components.LedgerViewFlowable
import com.daml.ledger.javaapi.components.helpers.CommandsAndPendingSet
import com.daml.ledger.javaapi.data._
import com.digitalasset.app.LedgerClient
import io.reactivex.Flowable
import org.pcollections.{HashTreePMap, HashTreePSet}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._

abstract class Bot(party: String, ledgerClient: LedgerClient) {
  ledgerClient.wireBot(transactionFilter, runWithErrorHandling)

  def logger: Logger = LoggerFactory.getLogger(name)

  def name: String
  def templateFilter: Set[String]
  def run(getTemplateId: String => Identifier, ledgerView: LedgerViewFlowable.LedgerView[Record]): List[Command]

  private def runWithErrorHandling(ledgerView: LedgerViewFlowable.LedgerView[Record]): Flowable[CommandsAndPendingSet] = {
    try {
      run(ledgerClient.getTemplateId, ledgerView) match {
        case l if l.nonEmpty => Flowable.just(createCommandsAndPendingSet(l))
        case _ => Flowable.just(CommandsAndPendingSet.empty)
      }
    } catch {
      case e: Throwable =>
        logger.error("Error: ", e)
        Flowable.error(e)
    }
  }

  private def transactionFilter: FiltersByParty = {
    val inclusiveFilter: Filter = new InclusiveFilter(templateFilter.map(ledgerClient.getTemplateId).asJava)
    val filter = Collections.singletonMap(party, inclusiveFilter)
    new FiltersByParty(filter)
  }

  private def createCommandsAndPendingSet(commands: List[Command]): CommandsAndPendingSet = {
    val cId = UUID.randomUUID().toString
    val currentTime = ledgerClient.getTime()
    val cmds =
      new SubmitCommandsRequest(
        UUID.randomUUID().toString,
        ledgerClient.appId,
        cId,
        party,
        currentTime,
        currentTime.plusSeconds(ledgerClient.maxRecordOffset.longValue),
        commands.asJava
      )

    val pendingSet = commands
      .map(_.asExerciseCommand())
      .filter(_.isPresent)
      .map(c => (c.get.getTemplateId, c.get.getContractId))
      .groupBy(_._1)
      .mapValues(x => HashTreePSet.from(x.map(_._2).asJava))

    new CommandsAndPendingSet(cmds, HashTreePMap.from(pendingSet.asJava))
  }
}
