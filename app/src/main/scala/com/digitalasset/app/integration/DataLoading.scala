// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.app.integration

import java.util.Collections

import com.daml.ledger.javaapi.data.Record.Field
import com.daml.ledger.javaapi.data.{CreateCommand, DamlList, Date, Decimal, ExerciseCommand, Party, Record, Text, Variant, Unit => DataUnit}
import com.digitalasset.app.LedgerClient
import com.digitalasset.app.Schema.Schema
import com.digitalasset.app.utils.Cdm
import com.digitalasset.app.utils.Json.JsonBuilder
import com.digitalasset.app.utils.Record._
import com.google.gson.JsonObject
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._

class DataLoading(party: String, client: LedgerClient, schema: Schema) {
  private def logger: Logger = LoggerFactory.getLogger("Integration - DataLoading " + party)

  private val decoder = new Decoder(schema)

  private val mas = new InMemoryDataStore(party, "MasterAgreementInstance", client)
  private val cis  = new InMemoryDataStore(party, "ContractInstance", client)

  def loadRateFixing
  (
    date: String,
    rateIndex: String,
    tenor: String,
    value: Double,
    copyTo: List[String]
  ): Unit = {

    // build json
    val period = tenor.substring(tenor.length - 1, tenor.length)
    val periodMultiplier = tenor.substring(0, tenor.length - 1).toInt
    val obs =
      new JsonObject()
        .addGeneric("date", date)
        .addGeneric("observation", value)
        .addGeneric("source", new JsonObject()
          .addGeneric("curve", new JsonObject()
            .addGeneric("interestRateCurve", new JsonObject()
              .addGeneric("floatingRateIndex", new JsonObject()
                  .addGeneric("value", rateIndex)
              )
              .addGeneric("tenor", new JsonObject()
                .addGeneric("period", period)
                .addGeneric("periodMultiplier", periodMultiplier)
              )
            )
          )
        )

    val obsRecord = decoder.decode(obs, "ObservationPrimitive")
    val obsInstance =
      new Record(List(
        new Field("d", obsRecord),
        new Field("publisher", new Party(party)),
        new Field("observers", new DamlList(copyTo.map(p => new Party(p)).asJava))
      ).asJava)

    val cmd = new CreateCommand(client.getTemplateId("ObservationInstance"), obsInstance)
    client.sendCommands(party, List(cmd))
  }

  def loadMasterAgreement(counterParty: String): Unit = {
    val map =
      new Record(List(
        new Field("p1", new Party(party)),
        new Field("p2", new Party(counterParty))
      ).asJava)

    val cmd = new CreateCommand(client.getTemplateId("MasterAgreementProposal"), map)
    client.sendCommands(party, List(cmd))
  }

  def loadHolidayCalendar(label: String, weekend: List[String], observer: List[String]): Unit = {
    val hcd =
      new Record(List(
        new Field("label", new Text(label)),
        new Field("weekend", new DamlList(weekend.map(w => new Variant(w, DataUnit.getInstance)).asJava)),
        new Field("holidays", new DamlList(Collections.emptyList[Date]()))
      ).asJava)

    val hci =
      new Record(List(
        new Field("d", hcd),
        new Field("publisher", new Party(party)),
        new Field("observers", new DamlList(observer.map(p => new Party(p)).asJava))
      ).asJava)

    val cmd = new CreateCommand(client.getTemplateId("HolidayCalendarInstance"), hci)
    client.sendCommands(party, List(cmd))
  }

  def loadCash(receiver: String, currency: String, amount: BigDecimal, account: String): Unit = {
    val arg =
      new Record(List(
        new Field("issuer", new Party(party)),
        new Field("sender", new Party(party)),
        new Field("receiver", new Party(receiver)),
        new Field("accountFrom", new Text(account)),
        new Field("currency", new Text(currency)),
        new Field("amount", new Decimal(amount.bigDecimal)),
      ).asJava)

    val cmd = new CreateCommand(client.getTemplateId("CashTransferRequest"), arg)
    client.sendCommands(party, List(cmd))
  }

  // Load event from json file
  def loadEvent(data: JsonObject): Unit = {
    // Look up ciCid and add it if contractReference is part of argument
    val arg = data.get("argument").getAsJsonObject
    val argEnriched = enrichArg(arg)

    val choice = data.get("choice").getAsString
    val argRecord = decoder.decode(argEnriched, choice + "1")

    val parties = argRecord.getList[Record]("ps").map(_.get[Party]("p").getValue)
    val maO = Cdm.findMasterAgreement(parties, mas.getData())

    maO match {
      case Some(ma) =>
        val cmd =
          if (party == ma._2.get[Party]("p1").getValue)
            new ExerciseCommand(client.getTemplateId("MasterAgreementInstance"), ma._1, choice + "1", argRecord)
          else if (party == ma._2.get[Party]("p2").getValue)
            new ExerciseCommand(client.getTemplateId("MasterAgreementInstance"), ma._1, choice + "2", argRecord)
          else
            throw new Exception("calling party not part of master agreement")

        client.sendCommands(party, List(cmd))

      case None =>
        logger.info("master agreement not found")
    }
  }

  private def enrichArg(arg: JsonObject): JsonObject = {
    val argCloned = arg.deepCopy()
    if (arg.has("contractReference")) {
      val contractReference = arg.get("contractReference").getAsString
      val ciO = cis.getData().find(_._2.get[Record]("d").get[Text]("rosettaKey").getValue == contractReference)
      ciO match {
        case Some(ci) => argCloned.addProperty("ciCid", ci._1)
        case None => logger.info("contract not found")
      }
    }
    argCloned
  }
}
