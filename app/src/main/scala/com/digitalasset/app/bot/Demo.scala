// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.app.bot

import java.time.{LocalDate, ZoneOffset}
import java.util.concurrent.ConcurrentHashMap

import com.daml.ledger.javaapi.components.LedgerViewFlowable
import com.daml.ledger.javaapi.data.Record.Field
import com.daml.ledger.javaapi.data._
import com.digitalasset.app.LedgerClient
import com.digitalasset.app.utils.Cdm
import com.digitalasset.app.utils.Record._

import scala.collection.JavaConverters._
import scala.collection.mutable

// Demo bot which accepts event proposals, creates new derived events, and auto-allocates cash
class Demo(party: String, ledgerClient: LedgerClient, eventExclusionList: List[String]) extends Bot(party, ledgerClient) {

  lazy val pendingEvent: mutable.Map[Record, Record] = new ConcurrentHashMap[Record, Record]().asScala

  def name: String = "Bot - Demo " + party
  def templateFilter =
    Set ( "ContractInstance"
        , "DerivedEvent"
        , "EventProposal"
        , "EventInstance"
        , "EventNotification"
        , "MasterAgreementInstance"
        , "ObservationInstance"
        , "HolidayCalendarInstance"
        )

  def run(getTid: String => Identifier, ledgerView: LedgerViewFlowable.LedgerView[Record]): List[Command] = {

    val ciTid = getTid("ContractInstance")
    val edTid = getTid("DerivedEvent")
    val epTid = getTid("EventProposal")
    val eiTid = getTid("EventInstance")
    val enTid = getTid("EventNotification")
    val maTid = getTid("MasterAgreementInstance")

    val cis = ledgerView.getContracts(ciTid).asScala.toList
    val eps = ledgerView.getContracts(epTid).asScala.toList
    val eis = ledgerView.getContracts(eiTid).asScala.toList
    val eds = ledgerView.getContracts(edTid).asScala.toList
    val ens = ledgerView.getContracts(enTid).asScala.toList
    val mas = ledgerView.getContracts(maTid).asScala.toList

    val ledgerTime = ledgerClient.getTime()

    // Mappings
    val contractIdt2eds = eds.groupBy(_._2.get[Record]("contractIdentifier"))
    val contractIdt2ci =
      cis.flatMap{ci =>
        val parties = ci._2.getList[Record]("ps")
        val idts = ci._2.get[Record]("d").getList[Record]("contractIdentifier")
        Cdm.getIdentifierByParty(party, parties, idts).map(x => (x, ci))
      }.toMap

    // Release pending event once it has been processed
    ens.foreach(en => {
      val parties = en._2.getList[Record]("ps")
      val idts = en._2.get[Record]("d").getList[Record]("eventIdentifier")
      val eventIdentifier = Cdm.getIdentifierByParty(party, parties, idts)
      eventIdentifier match {
        case Some(idt) if pendingEvent.contains(idt) => pendingEvent -= idt
        case _ => ()
      }})

    // Accept EventProposal
    val cmds1 =
      eps.flatMap {
        case (cId, ep) if !eventExclusionList.contains(ep.get[Record]("d").get[Text]("rosettaKey").getValue) =>
          val allSigs = ep.getList[Record]("ps").map(_.get[Party]("p").getValue)
          val existingSigs = ep.getList[Party]("sigs").map(_.getValue)
          val missingSigs = allSigs.toSet -- existingSigs.toSet

          if (missingSigs.nonEmpty && missingSigs.toList.head == party)
            if (allSigs(0) == party) {
              logger.info("Accepting EventProposal " + ep.get[Record]("d").get[Text]("rosettaKey").getValue + "...")
              Some(new ExerciseCommand(epTid, cId, "Accept1", Cdm.emptyArg))
            }

            else if (allSigs(1) == party) {
              logger.info("Accepting EventProposal " + ep.get[Record]("d").get[Text]("rosettaKey").getValue + "...")
              Some(new ExerciseCommand(epTid, cId, "Accept2", Cdm.emptyArg))
            }

            else if (allSigs(2) == party) {
              logger.info("Accepting EventProposal " + ep.get[Record]("d").get[Text]("rosettaKey").getValue + "...")
              Some(new ExerciseCommand(epTid, cId, "Accept3", Cdm.emptyArg))
            }

            else if (allSigs(3) == party) {
              logger.info("Accepting EventProposal " + ep.get[Record]("d").get[Text]("rosettaKey").getValue + "...")
              Some(new ExerciseCommand(epTid, cId, "Accept4", Cdm.emptyArg))
            }

            else
              None
          else
            None
        case _ => None
      }

    // Create next event
    val cmds2 =
      contractIdt2eds.flatMap {
        case (contractIdt, edsByIdt) =>
          val nextEd = edsByIdt.minBy(_._2.get[Record]("d").get[Date]("eventDate").getValue.toEpochDay)
          val eventTime = nextEd._2.get[Record]("d").get[Date]("eventDate").getValue.atStartOfDay.toInstant(ZoneOffset.UTC)

          contractIdt2ci.get(contractIdt) match {
            case Some(ci) =>
              val parties = ci._2.getList[Record]("ps")
              val maO = Cdm.findMasterAgreement(parties.map(_.get[Party]("p").getValue), mas)
              val eventIdtO = Cdm.getIdentifierByParty(party, parties, nextEd._2.get[Record]("d").getList[Record]("eventIdentifier"))

              (maO, eventIdtO) match {
                case (Some(ma), Some(eventIdt))
                  if !pendingEvent.values.exists(_ == contractIdt)
                      && eis.isEmpty
                      && (ledgerTime.isAfter(eventTime) || ledgerTime.equals(eventTime))
                      && !eventExclusionList.contains(nextEd._2.get[Record]("d").get[Text]("rosettaKey").getValue) =>

                  val obsDates =
                    if (nextEd._2.get[Record]("d").getOptional[Text]("eventQualifier").get.getValue == "Reset")
                      List(nextEd._2.get[Record]("d").get[Date]("eventDate").getValue)
                    else
                      List()

                  val arg = new Record(List(
                    new Field("maCid", new ContractId(ma._1)),
                    new Field("ciCid", new ContractId(ci._1)),
                    new Field("refData", getRefData(getTid, ledgerView, obsDates))
                  ).asJava)

                  val newElement = (eventIdt, contractIdt)
                  pendingEvent += newElement
                  logger.info("Creating event " + nextEd._2.get[Record]("d").get[Text]("rosettaKey").getValue + "...")
                  Some(new ExerciseCommand(edTid, nextEd._1, "CreateEvent", arg))

                case _ => None
              }
            case _ => None
          }

        case _ => None
      }

    cmds1 ++ cmds2
  }

  private def getRefData(getTid: String => Identifier, ledgerView: LedgerViewFlowable.LedgerView[Record], obsDates: List[LocalDate]): Record = {
    val obsiTid = getTid("ObservationInstance")
    val hciTid = getTid("HolidayCalendarInstance")

    val hcCids = ledgerView.getContracts(hciTid).asScala.toList.map(_._1)
    val obs = if (obsDates.isEmpty) List() else ledgerView.getContracts(obsiTid).asScala.toList

    val obsCids = obs.filter(obs => obsDates.contains(obs._2.get[Record]("d").get[Date]("date").getValue)).map(_._1)

    new Record(List(
      new Field("holidayCalendarCids", new DamlList(hcCids.map(hcCid => new ContractId(hcCid)).asJava)),
      new Field("observationCids", new DamlList(obsCids.map(obsCid => new ContractId(obsCid)).asJava)),
    ).asJava)
  }
}
