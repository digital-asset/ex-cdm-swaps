// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.app.integration

import java.time.{LocalDate, LocalDateTime, ZoneOffset, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.Collections

import com.daml.ledger.javaapi.data._
import com.daml.ledger.javaapi.data.Record.Field
import com.digitalasset.app.Schema
import com.google.gson.{JsonElement, JsonObject}

import scala.collection.JavaConverters._

class Decoder(schema: Schema.Schema) {
  def decode(json: JsonObject, targetType: String): Record = {
    val fields =
      schema.get(targetType) match {
        case Some(t) => t
        case None => throw new IllegalArgumentException("Schema not found for type " + targetType)
      }

    val recordFields = fields
      .map(field => new Field(field.name, decodeOptValue(getJsonElement(field.name, json), field)))
      .asJava

    new Record(recordFields)
  }

  private def getJsonElement(name: String, json: JsonObject): Option[JsonElement] = {
    if (json.has(name)) Some(json.get(name))
    else None
  }

  private def decodeOptValue(element: Option[JsonElement], field: Schema.Field): Value = {
    try {
      element match {
        case None if field.cardinality == Schema.Cardinality.OPTIONAL =>
          new DamlOptional(java.util.Optional.empty())

        case Some(e) if field.cardinality == Schema.Cardinality.OPTIONAL =>
          new DamlOptional(java.util.Optional.of(decodeValueWithMeta(e, field)))

        case None if field.cardinality == Schema.Cardinality.LISTOF =>
          new DamlList(Collections.emptyList[Value]())

        case Some(e) if field.cardinality == Schema.Cardinality.LISTOF =>
          new DamlList(e.getAsJsonArray.iterator.asScala.toList.map(decodeValueWithMeta(_, field)).asJava)

        case Some(e) => decodeValueWithMeta(e, field)

        case _ => throw new IllegalArgumentException(field.name + " missing but non-optional")
      }
    } catch {
      case e: Throwable => throw new Exception("Failed decoding '" + field.name + "'; " + e.getMessage)
    }
  }

  private def decodeValueWithMeta(element: JsonElement, field: Schema.Field): Value = {
    if (field.withReference) {
      val reference = getJsonElement("reference", element.getAsJsonObject).get
      new Record(List(
        new Field("reference", new DamlOptional[Value](java.util.Optional.of(decodeValue(reference, "PrimText")))),
        new Field("value", damlNone),
        new Field("meta", new Record(List(
          new Field("reference", damlNone),
          new Field("scheme", damlNone),
          new Field("id", damlNone)
        ).asJava))
      ).asJava)
    }
    else if(field.withMeta) {
      val value = getJsonElement("value", element.getAsJsonObject).get
      new Record(List(
        new Field("value", decodeValue(value, field.`type`)),
        new Field("meta", new Record(List(
          new Field("reference", damlNone),
          new Field("scheme", damlNone),
          new Field("id", damlNone)
        ).asJava))
      ).asJava)
    }
    else
      decodeValue(element, field.`type`)
  }

  private def decodeValue(element: JsonElement, typ: String): Value = {
    typ match {
      case "PrimInt64" => new Int64(element.getAsInt)
      case "PrimDecimal" => new Decimal(element.getAsBigDecimal)
      case "PrimText" => new Text(element.getAsString)
      case "PrimBool" => new Bool(element.getAsBoolean)
      case "PrimDate" =>
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        new Date(LocalDate.parse(element.getAsString, dateFormatter).toEpochDay.toInt)
      case "PrimTimestamp" =>
        val time = LocalDateTime.parse(element.getAsString, DateTimeFormatter.ISO_DATE_TIME)
        new Timestamp(time.atZone(ZoneOffset.UTC).toEpochSecond)
      case "PrimParty" => new Party(element.getAsString)
      case "PrimContractId" => new ContractId(element.getAsString)
      case _ if typ.contains("Enum") =>
        val enumValue = typ + "_" + element.getAsString
        new Variant(enumValue, Unit.getInstance)
      case "ZonedDateTime" =>
        val time = ZonedDateTime.parse(element.getAsString)
        new Record(List(
          new Field("dateTime", new Timestamp(time.toEpochSecond)),
          new Field("timezone", new Text(time.getZone.getId))
        ).asJava)
      case _ => decode(element.getAsJsonObject, typ)
    }
  }

  private def damlNone = new DamlOptional[Value](java.util.Optional.empty())
}
