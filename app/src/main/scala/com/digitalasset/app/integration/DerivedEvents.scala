// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.app.integration

import java.time.LocalDate

import com.daml.ledger.javaapi.data.Record.Field
import com.daml.ledger.javaapi.data._
import com.digitalasset.app.{LedgerClient, utils}
import com.digitalasset.app.utils.Cdm
import com.digitalasset.app.utils.Record._
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._

class DerivedEvents(party: String, client: LedgerClient) {
  private def logger: Logger = LoggerFactory.getLogger("Integration - DerivedEvents " + party)

  private val workflows = new InMemoryDataStore(party, "DeriveEventsWorkflow", client)

  private val cis  = new InMemoryDataStore(party, "ContractInstance", client)
  private val eds = new InMemoryDataStore(party, "DerivedEvent", client)

  private val mas = new InMemoryDataStore(party, "MasterAgreementInstance", client)

  private val hcs = new InMemoryDataStore(party, "HolidayCalendarInstance", client)
  private val obs = new InMemoryDataStore(party, "ObservationInstance", client)

  // Derive events for a single contract
  def deriveEvents(contractRosettaKey: String, fromDate: Option[LocalDate], toDate: Option[LocalDate]) = {
    val contractIdt2eds = eds.getData().groupBy(_._2.get[Record]("contractIdentifier"))
    val rosettaKey2ci = cis.getData().map(ci => (ci._2.get[Record]("d").get[Text]("rosettaKey").getValue, ci)).toMap

    workflows.getData().headOption match {
      case Some(workflow) =>
        // Derive events if contract exists and archive previous results
        rosettaKey2ci.get(contractRosettaKey) match {
          case Some(ci) =>
            val contractIdtO = Cdm.getIdentifierByParty(party, ci._2.getList[Record]("ps"), ci._2.get[Record]("d").getList[Record]("contractIdentifier"))

            contractIdtO match {
              case Some(contractIdt) =>
                // Archive old results
                val archiveCmds =
                  contractIdt2eds.get(contractIdt) match {
                    case Some(edsGrouped) =>
                      val edTid = client.getTemplateId("DerivedEvent")
                      edsGrouped.map(ed => new ExerciseCommand(edTid, ed._1, "Archive", Cdm.archiveArg))
                    case None => List()
                  }

                val createCmd = {
                  // Build reference data - TODO: filter for index as well & look up holiday calendars (or wait for contract keys)
                  // Check if there is an existing event giving observation dates
                  val obsDates =
                  contractIdt2eds.find(_._1 == contractIdt) match {
                    case Some ((_, edsGrouped)) =>
                      val edsFiltered = edsGrouped.filter(_._2.get[Record]("d").getOptional[Text]("eventQualifier").map(_.getValue).contains("Reset"))
                      edsFiltered.map(_._2.get[Record]("d").get[Date]("eventDate").getValue)
                    case None => List()
                  }

                  val arg = new Record(List(
                    new Field("ciCid", new ContractId[Template](ci._1)),
                    new Field("fromDate", toDamlOptionalDate(fromDate)),
                    new Field("toDate", toDamlOptionalDate(toDate)),
                    new Field("refData", getRefData(obsDates))
                  ).asJava)

                  val workflowTid = client.getTemplateId("DeriveEventsWorkflow")
                  new ExerciseCommand(workflowTid, workflow._1, "Trigger", arg)
                }

                client.sendCommands(party, createCmd :: archiveCmds)

              case None =>
                logger.info("No contract identifier found for party " + party)
            }
          case None =>
            logger.info("'Contract' with rosetta key " + contractRosettaKey + " not found")
        }
      case None =>
        logger.info("'DeriveEventsWorkflow' not available")
     }
  }

  // Derive events for all trades
  def deriveEventsAll(fromDate: Option[LocalDate], toDate: Option[LocalDate]) = {
    val cisFiltered = cis.getData().filter(ci => ci._2.get[Record]("d").getOptional[Record]("closedState").isEmpty)
    cisFiltered.foreach { ci =>
      val rosettaKey = ci._2.get[Record]("d").get[Text]("rosettaKey").getValue
      deriveEvents(rosettaKey, fromDate, toDate)
    }
  }

  // Create the next derived event
  def createNextDerivedEvent(contractRosettaKey: String, eventQualifier: String) = {

    val nextEdO = eds.getData()
      .filter{ ed =>
        val edEventQualifier =
          ed._2.get[Record]("d")
            .getOptional[Text]("eventQualifier")
            .map(_.getValue)
        val edContractRosettaKeys =
          ed._2.get[Record]("d")
            .getOptional[Record]("lineage")
            .map(_.getList[Record]("contractReference").map(_.getOptional[Text]("reference").get.getValue))
            .getOrElse(List())
        edEventQualifier.contains(eventQualifier) && edContractRosettaKeys.contains(contractRosettaKey)
      }
      .sortBy(_._2.get[Record]("d").get[Date]("eventDate").getValue.toEpochDay)
      .headOption

    nextEdO match {
      case Some(ed) =>
        val ciO = cis.getData().find{ ci =>
          val parties = ci._2.getList[Record]("ps")
          val idts = ci._2.get[Record]("d").getList[Record]("contractIdentifier")
          val idtO = utils.Cdm.getIdentifierByParty(party, parties, idts)
          idtO.contains(ed._2.get[Record]("contractIdentifier"))
        }

        val parties = ed._2.getList[Record]("ps").map(_.get[Party]("p").getValue)
        val maO = utils.Cdm.findMasterAgreement(parties,  mas.getData())

        (ciO, maO) match {
          case (Some(ci), Some(ma)) =>
            val arg =
              new Record(List(
                new Field("maCid", new ContractId(ma._1)),
                new Field("ciCid", new ContractId(ci._1)),
                new Field("refData", getRefData(List(ed._2.get[Record]("d").get[Date]("eventDate").getValue)))
              ).asJava)

            val cmd = new ExerciseCommand(client.getTemplateId("DerivedEvent"), ed._1, "CreateEvent", arg)
            client.sendCommands(party, List(cmd))
          case _ =>
            logger.info("contract and / or master agreement not found")
        }
      case None =>
        logger.info("event not found")
    }
  }

  def removeDerivedEvents(contractRosettaKey: String) = {
    val contractIdt2eds = eds.getData().groupBy(_._2.get[Record]("contractIdentifier"))
    val rosettaKey2ci = cis.getData().map(ci => (ci._2.get[Record]("d").get[Text]("rosettaKey").getValue, ci)).toMap

    rosettaKey2ci.get(contractRosettaKey) match {
      case Some(ci) =>
        val contractIdtO = Cdm.getIdentifierByParty(party, ci._2.getList[Record]("ps"), ci._2.get[Record]("d").getList[Record]("contractIdentifier"))

        contractIdtO match {
          case Some(contractIdt) =>
            // Archive old results
            val archiveCmds =
              contractIdt2eds.get(contractIdt) match {
                case Some(edsGrouped) =>
                  val edTid = client.getTemplateId("DerivedEvent")
                  edsGrouped.map(ed => new ExerciseCommand(edTid, ed._1, "Archive", Cdm.archiveArg))
                case None => List()
              }
            client.sendCommands(party, archiveCmds)
          case None =>
            logger.info("No contract identifier found for party " + party)
        }
      case None =>
        logger.info("'Contract' with rosetta key " + contractRosettaKey + "not found")
    }
  }

  private def getRefData(obsDates : List[LocalDate]): Record = {
    val obsFiltered =
      if (obsDates.isEmpty) List()
      else obs.getData().filter(o => obsDates.contains(o._2.get[Record]("d").get[Date]("date").getValue))

    new Record(List(
      new Field("holidayCalendarCids", new DamlList(hcs.getData().map(x => new ContractId(x._1)).asJava)),
      new Field("observationCids", new DamlList(obsFiltered.map(x => new ContractId(x._1)).asJava)),
    ).asJava)
  }
}
