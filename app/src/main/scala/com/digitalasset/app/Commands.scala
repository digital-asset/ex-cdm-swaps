// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.app

import java.time.{Instant, LocalDate}

import com.daml.ledger.javaapi.data.{CreateCommand, Party, Record}
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._
import scala.io.Source

object Commands {
  // Config
  private val config = ConfigFactory.load()
  private val parties = config.getStringList("parties").asScala.toList
  private val dataProvider = config.getString("dataProvider")
  private val centralBanks = config.getStringList("centralBanks").asScala.toList

  // Clients
  private val client = initClient()
  private val schema = client.loadSchemas(config.getStringList("typeModules").asScala.toList)
  private val party2dataLoading =
    (dataProvider :: centralBanks ++ parties)
      .map(p => (p, new integration.DataLoading(p, client, schema)))
      .toMap
  private val party2derivedEvents =
    parties
      .map(p => (p, new integration.DerivedEvents(p, client)))
      .toMap

  private def initClient(): LedgerClient = {
    new LedgerClient(
      Config(
        config.getString("id"),
        config.getString("platform.host"),
        config.getInt("platform.port"),
        config.getInt("platform.maxRecordOffset"),
        config.getBoolean("platform.useStaticTime")
      )
    )
  }

  def init(): Unit = {}

  def getTime(): Instant = {
    client.getTime()
  }

  // Data loading
  def initMarket(directory: String, time: String = ""): Unit = {
    if (time != "") client.setTime(Instant.parse(time))
    parties.foreach(createAllocateWorfklow)
    parties.foreach(createDeriveEventsWorkflow)
    Commands.loadMasterAgreements(directory + "/MasterAgreement.csv")
    Commands.loadHolidayCalendars(directory + "/HolidayCalendar.csv")
    Commands.loadCash(directory + "/Cash.csv")
    parties.foreach(p => new bot.MarketSetup(p, client))
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



  def publishRateFixing(date: String, rateIndex: String, tenor: String, value: Double): Unit = {
    party2dataLoading(dataProvider).loadRateFixing(date, rateIndex, tenor, value, parties)
  }

  def publishRateFixingSingleParty(date: String, rateIndex: String, tenor: String, value: String, party: String): Unit = {
    party2dataLoading(dataProvider).loadRateFixing(date, rateIndex, tenor, value.toDouble, List(party))
  }

  def publishRateFixings(file: String): Unit = {
    val bufferedSource = Source.fromFile(file)
    for (line <- bufferedSource.getLines) {
      val cols = line.split(",").map(_.trim)
      party2dataLoading(dataProvider).loadRateFixing(cols(0), cols(1), cols(2), cols(3).toDouble, parties)
    }
    bufferedSource.close
  }

  def loadMasterAgreements(file: String): Unit = {
    val bufferedSource = Source.fromFile(file)
    for (line <- bufferedSource.getLines.drop(1)) {
      val cols = line.split(",").map(_.trim)
      party2dataLoading(cols(0)).loadMasterAgreement(cols(1))
    }
    bufferedSource.close
  }

  def loadHolidayCalendars(file: String): Unit = {
    val bufferedSource = Source.fromFile(file)
    for (line <- bufferedSource.getLines.drop(1)) {
      val cols = line.split(",").map(_.trim)
      party2dataLoading(dataProvider).loadHolidayCalendar(cols(0), cols(1).split(";").toList, parties)
    }
    bufferedSource.close
  }

  def loadCash(file: String): Unit = {
    val bufferedSource = Source.fromFile(file)
    for (line <- bufferedSource.getLines.drop(1)) {
      val cols = line.split(",").map(_.trim)
      party2dataLoading(cols(0)).loadCash(cols(1), cols(2), cols(3).toDouble, cols(4))
    }
    bufferedSource.close
  }

  def loadEvents(directory: String): Unit = {
    utils.Json.loadJsons(directory).foreach { json =>
      val party = json.getAsJsonObject("argument").getAsJsonArray("ps").iterator.next.getAsJsonObject.get("p").getAsString
      party2dataLoading(party).loadEvent(json)
    }
  }

  def deriveEvents(party: String, contractRosettaKey: String): Unit = {
    party2derivedEvents(party).deriveEvents(contractRosettaKey, None, None)
  }

  def deriveEventsAll(party: String, fromDate: Option[String], toDate: Option[String]): Unit = {
    party2derivedEvents(party).deriveEventsAll(fromDate.map(LocalDate.parse), toDate.map(LocalDate.parse))
  }

  def createNextDerivedEvent(party: String, contractRosettaKey: String, eventQualifier: String): Unit = {
    party2derivedEvents(party).createNextDerivedEvent(contractRosettaKey, eventQualifier)
  }

  def removeDerivedEvents(party: String, contractRosettaKey: String): Unit = {
    party2derivedEvents(party).removeDerivedEvents(contractRosettaKey)
  }
}
