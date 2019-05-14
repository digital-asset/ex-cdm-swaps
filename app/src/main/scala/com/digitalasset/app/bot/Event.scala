// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.app.bot

import java.time.{Instant, ZoneOffset}
import java.util.concurrent.ConcurrentHashMap

import com.daml.ledger.javaapi.components.LedgerViewFlowable
import com.daml.ledger.javaapi.data._
import com.daml.ledger.javaapi.data.Record.Field
import com.digitalasset.app.LedgerClient
import com.digitalasset.app.utils.Cdm
import com.digitalasset.app.utils.Record._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import concurrent.Future

// Bot which auto-instructs cash transfers and auto-lifecycles event once time is due
class Event(party: String, ledgerClient: LedgerClient) extends Bot(party, ledgerClient) {

  lazy val delayedCmds : mutable.Set[(Instant, Command)] = ConcurrentHashMap.newKeySet[(Instant, Command)]().asScala
  lazy val delayedEiCids : mutable.Set[String] = ConcurrentHashMap.newKeySet[String]().asScala

  // Periodically check if command can be executed
  Future {
    while(true) {
      val ledgerTime = ledgerClient.getTime()
      val cmds =
        delayedCmds.flatMap {
          case delayedCmd if ledgerTime.isAfter(delayedCmd._1) || ledgerTime.equals(delayedCmd._1) =>
            logger.info("Executing delayed command...")
            delayedCmds -= delayedCmd
            Some(delayedCmd._2)
          case _ => None
        }.toList
      if(cmds.nonEmpty)
        // Batch?
        ledgerClient.sendCommands(party, cmds)
      Thread.sleep(1000)
    }
  }

  def name: String = "Bot - AutoLifecycle " + party
  def templateFilter = Set("EventInstance", "ContractInstance", "CashTransferInstruction")

  def run(getTid: String => Identifier, ledgerView: LedgerViewFlowable.LedgerView[Record]): List[Command] = {
    val eiTid  = getTid("EventInstance")
    val ciTid  = getTid("ContractInstance")
    val ctiTid = getTid("CashTransferInstruction")

    val eis       = ledgerView.getContracts(eiTid).asScala.toList
    val rk2ciCid  = ledgerView.getContracts(ciTid).asScala.toList.map(ci => (ci._2.get[Record]("d").get[Text]("rosettaKey").getValue, ci._1)).toMap
    val id2ctis   = ledgerView.getContracts(ctiTid).asScala.toList.groupBy(_._2.get[Record]("d").get[Text]("eventReference").getValue)

    val ledgerTime = ledgerClient.getTime()

    val cmds =
      eis.flatMap {
        case (eiCid, ei) if !delayedEiCids.contains(eiCid) =>
          val parties = ei.getList[Record]("ps").map(_.get[Party]("p").getValue)

          if (isPending(ei) && parties.head == party) {
            val arg = new Record(List(new Field("exerciser", new Party(party))).asJava)
            val cmd = new ExerciseCommand(eiTid, eiCid, "Instruct", arg)

            val eventTime = ei.get[Record]("d").get[Date]("eventDate").getValue.atStartOfDay.toInstant(ZoneOffset.UTC)
            if (ledgerTime.isAfter(eventTime) || ledgerTime.equals(eventTime)) {
              logger.info("Instructing " + ei.get[Record]("d").get[Text]("rosettaKey").getValue + "...")
              Some (cmd)
            } else {
              val delayedCmd = (eventTime, cmd)
              delayedCmds += delayedCmd
              delayedEiCids += eiCid
              logger.info("Delaying instruction " + ei.get[Record]("d").get[Text]("rosettaKey").getValue + "...")
              None
            }
          }

          else if (parties.head == party) {
            // Collect allocated cash transfer instructions for lifecycling
            val ctis = id2ctis.getOrElse(ei.get[Record]("d").get[Text]("rosettaKey").getValue, List())
            val cashTransfersV = ei.get[Record]("d").get[Record]("primitive").getList[Record]("transfer").map(_.getList[Record]("cashTransfer"))
            val ctiCidsO = collectCtiCids(ctis, cashTransfersV)

            // Collect effected contracts
            val ciCidsO = collectCiCids(ei, rk2ciCid)

            (ctiCidsO, ciCidsO) match {
              case (Some(ctiCids), Some(ciCids)) =>
                val arg = new Record(List(
                  new Field("exerciser", new Party(party)),
                  new Field("ciCids", new DamlList(ciCids.map(x => new ContractId(x)).asJava)),
                  new Field("ctiCids", new DamlList(ctiCids.map(x => new DamlList(x.asJava)).asJava))
                ).asJava)
                val cmd = new ExerciseCommand(eiTid, eiCid, "Lifecycle", arg)

                val eventEffectiveTime = Cdm.getEffectiveDate(ei.get[Record]("d")).atStartOfDay.toInstant(ZoneOffset.UTC)
                if(ledgerTime.isAfter(eventEffectiveTime) || ledgerTime.equals(eventEffectiveTime)) {
                  logger.info("Lifecycling " + ei.get[Record]("d").get[Text]("rosettaKey").getValue + "...")
                  Some (cmd)
                } else {
                  val delayedCmd = (eventEffectiveTime, cmd)
                  delayedCmds += delayedCmd
                  delayedEiCids += eiCid
                  logger.info("Delaying lifecycling " + ei.get[Record]("d").get[Text]("rosettaKey").getValue  + "...")
                  None
                }
              case _ => None
            }
          }
          else None
        case _ => None
      }

    delayedEiCids.map{ eiCid => if (!eis.exists(_._1 == eiCid)) delayedEiCids -= eiCid }

    cmds
  }

  private def collectCtiCids(ctis: List[(String, Record)], cashTransfersV: List[List[Record]]) = {
    val allocCtis = ctis.filter(_._2.getOptional[ContractId[Template]]("allocatedCashCid").isDefined)

    val matched =
      cashTransfersV.foldRight[Option[(List[List[ContractId[Template]]], List[(String, Record)])]](Some(List(), allocCtis)) {
        case (cashTransfers, accOuter) if accOuter.isDefined =>
          val (allocCidsMatchedOuter, allocRestOuter) = accOuter.get
          val resInner = cashTransfers.foldRight[Option[(List[ContractId[Template]], List[(String, Record)])]](Some(List(), allocRestOuter)) {
            case (cashTransfer, accInner) if accInner.isDefined =>
              val (allocCidsMatchedInner, allocRestInner) = accInner.get
              val (matched, rest) = allocRestInner.partition(_._2.get[Record]("d").get[Record]("cashTransfer") == cashTransfer)
              if (matched.isEmpty)
                None
              else
                Some((new ContractId[Template](matched.head._1) :: allocCidsMatchedInner, matched.tail ++ rest))
            case _ => None
          }
          resInner match {
            case Some((allocCidsMatchedInner, allocRestInner)) => Some(allocCidsMatchedInner :: allocCidsMatchedOuter, allocRestInner)
            case None => None
          }
        case _ => None
      }

    matched.map(_._1)
  }

  private def collectCiCids(ei: Record, rk2ciCid: Map[String, String]): Option[List[String]] = {
    val referencedContracts =
      ei
        .get[Record]("d")
        .getOptional[Record]("lineage")
        .map(_.getList[Record]("contractReference").map(_.getOptional[Text]("reference").get.getValue))
        .getOrElse(List())
    val ciCids = referencedContracts.map(rk => rk2ciCid.get(rk))
    if (ciCids.contains(None)) None else Some(ciCids.flatten)
  }

  private def isPending(ei: Record): Boolean = {
    val statuses =
      ei.get[Record]("d")
        .get[Record]("primitive")
        .getList[Record]("transfer")
        .map(_.getOptional[Variant]("status").map(_.getConstructor).getOrElse("TransferStatusEnum_Pending"))

    val isPending = statuses.nonEmpty && statuses.forall(x => x == "TransferStatusEnum_Pending")
    if (statuses.nonEmpty && !isPending && statuses.contains("TransferStatusEnum_Pending"))
      logger.info("Warning: inconsistent statuses - skipping event")

    isPending
  }
}
