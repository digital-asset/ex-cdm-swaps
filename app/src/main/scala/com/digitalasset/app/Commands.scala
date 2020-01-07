// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.app

import java.time.{Instant, LocalDate}

import com.daml.ledger.javaapi.data.{CreateCommand, Party, Record}
import com.digitalasset.app.integration.MarketSetup
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._
import scala.io.Source

object Commands {
  var host = ""
  var port = 0

  // Config
  private val config = ConfigFactory.load()
  private val parties = config.getStringList("parties").asScala.toList
  private val dataProvider = config.getStringList("dataProvider").asScala.toList
  private val centralBanks = config.getStringList("centralBanks").asScala.toList

  // Clients
  private lazy val client = initClient()
  private lazy val schema = SchemaBuilder.build(client, config.getStringList("typeModules").asScala.toList)
  private lazy val party2dataLoading =
    (dataProvider ++ centralBanks ++ parties)
      .map(p => (p, new integration.DataLoading(p, client, schema)))
      .toMap
  private lazy val party2derivedEvents =
    parties
      .map(p => (p, new integration.DerivedEvents(p, client)))
      .toMap

  private def initClient(): LedgerClient = {
    new LedgerClient(
      Config(
        config.getString("id"),
        host,
        port,
        config.getInt("platform.maxRecordOffset"),
        config.getBoolean("platform.useStaticTime")
      )
    )
  }

  def init() : Unit = {
    // Evaluate lazy variables
    party2dataLoading.map{case (x, _) => x}
    party2derivedEvents.map{case (x, _) => x}
  }

  def getTime(): Instant = {
    client.getTime()
  }

  // Data loading
  def initMarket(directory: String, time: String = ""): Unit = {
    if (time != "") client.setTime(Instant.parse(time))
    parties.foreach(createAllocateWorfklow)
    parties.foreach(createDeriveEventsWorkflow)
    loadMasterAgreements(directory + "/MasterAgreement.csv")
    loadHolidayCalendars(directory + "/HolidayCalendar.csv")
    loadCash(directory + "/Cash.csv")
    parties.foreach(p => new MarketSetup(p, client).run())
  }

  private def createAllocateWorfklow(party: String): Unit = {
    val arg = new Record(List(
      new Record.Field("sig", new Party(party))
    ).asJava)
    val cmd = new CreateCommand(client.getTemplateId("AllocateWorkflow"), arg)
    client.sendCommands(party, List(cmd))
  }

  private def createDeriveEventsWorkflow(party: String): Unit = {
    val arg = new Record(List(
      new Record.Field("sig", new Party(party))
    ).asJava)
    val cmd = new CreateCommand(client.getTemplateId("DeriveEventsWorkflow"), arg)
    client.sendCommands(party, List(cmd))
  }



  def publishRateFixing(publisher: String, date: String, rateIndex: String, tenor: String, value: Double): Unit = {
    party2dataLoading.get(publisher) match {
      case Some(dl) => dl.loadRateFixing(date, rateIndex, tenor, value, parties)
      case None => println("party " ++ publisher ++ " cannot load rate fixing.")
    }
  }

  def publishRateFixingSingleParty(publisher: String, date: String, rateIndex: String, tenor: String, value: String, party: String): Unit = {
    party2dataLoading.get(publisher) match {
      case Some(dl) => dl.loadRateFixing(date, rateIndex, tenor, value.toDouble, List(party))
      case None => println("party " ++ publisher ++ " cannot load rate fixing.")
    }
  }

  def publishRateFixings(file: String): Unit = {
    val bufferedSource = Source.fromFile(file)
    for (line <- bufferedSource.getLines) {
      val cols = line.split(",").map(_.trim)
      party2dataLoading.get(cols(1)) match {
        case Some(dl) => dl.loadRateFixing(cols(0), cols(2), cols(3), cols(4).toDouble, parties)
        case None => println("party " ++ cols(1) ++ " cannot load rate fixing.")
      }
    }
    bufferedSource.close
  }

  def loadMasterAgreements(file: String): Unit = {
    val bufferedSource = Source.fromFile(file)
    for (line <- bufferedSource.getLines.drop(1)) {
      val cols = line.split(",").map(_.trim)
      party2dataLoading.get(cols(0)) match {
        case Some(dl) => dl.loadMasterAgreement(cols(1))
        case None => println("party " ++ cols(0) ++ " cannot load master agreements.")
      }
    }
    bufferedSource.close
  }

  def loadHolidayCalendars(file: String): Unit = {
    val bufferedSource = Source.fromFile(file)
    for (line <- bufferedSource.getLines.drop(1)) {
      val cols = line.split(",").map(_.trim)
      party2dataLoading.get(cols(0)) match {
        case Some(dl) => dl.loadHolidayCalendar(cols(1), cols(2).split(";").toList, parties)
        case None => println("party " ++ cols(0) ++ " cannot load holiday calendars.")
      }
    }
    bufferedSource.close
  }

  def loadCash(file: String): Unit = {
    val bufferedSource = Source.fromFile(file)
    for (line <- bufferedSource.getLines.drop(1)) {
      val cols = line.split(",").map(_.trim)
      party2dataLoading.get(cols(0)) match {
        case Some(dl) => dl.loadCash(cols(1), cols(2), cols(3).toDouble, cols(4))
        case None => println("party " ++ cols(0) ++ " cannot load cash.")
      }
    }
    bufferedSource.close
  }

  def loadEvents(directory: String): Unit = {
    utils.Json.loadJsons(directory).foreach { json =>
      val party = json.getAsJsonObject("argument").getAsJsonArray("ps").iterator.next.getAsJsonObject.get("p").getAsString
      party2dataLoading.get(party) match {
        case Some(dl) => dl.loadEvent(json)
        case None => println("party " ++ party ++ " cannot load events.")
      }
    }
  }

  def deriveEvents(party: String, contractRosettaKey: String): Unit = {
    party2derivedEvents.get(party) match {
      case Some(de) => de.deriveEvents(contractRosettaKey, None, None)
      case None => println("party " ++ party ++ " cannot derive events.")
    }
  }

  def deriveEventsAll(party: String, fromDate: Option[String], toDate: Option[String]): Unit = {
    party2derivedEvents.get(party) match {
      case Some(de) => de.deriveEventsAll(fromDate.map(LocalDate.parse), toDate.map(LocalDate.parse))
      case None => println("party " ++ party ++ " cannot derive events.")
    }
  }

  def createNextDerivedEvent(party: String, contractRosettaKey: String, eventQualifier: String): Unit = {
    party2derivedEvents.get(party) match {
      case Some(de) => de.createNextDerivedEvent(contractRosettaKey, eventQualifier)
      case None => println("party " ++ party ++ " cannot derive events.")
    }
  }

  def removeDerivedEvents(party: String, contractRosettaKey: String): Unit = {
    party2derivedEvents.get(party) match {
      case Some(de) => de.removeDerivedEvents(contractRosettaKey)
      case None => println("party " ++ party ++ " cannot derive events.")
    }
  }
}
