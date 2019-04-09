// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.app.utils

import java.time.LocalDate
import java.util.Collections

import com.daml.ledger.javaapi.data.Record.Field
import com.daml.ledger.javaapi.data._
import Record._

object Cdm {

  def findMasterAgreement(ps: List[String], mas: List[(String, Record)]): Option[(String, Record)] = {
    ps.take(2) match {
      case List(p1, p2) =>
        mas.find(ma =>
          (ma._2.get[Party]("p1").getValue == p1 && ma._2.get[Party]("p2").getValue == p2)
            || (ma._2.get[Party]("p2").getValue == p1 && ma._2.get[Party]("p1").getValue == p2)
        )
      case _ => None
    }
  }

  def getEffectiveDate(eventData: Record): LocalDate = {
    val eventDate = eventData.get[Date]("eventDate").getValue
    val effectiveDate = eventData.getOptional[Date]("effectiveDate").map(_.getValue)

    effectiveDate match {
      case Some(d) => d
      case None => eventDate
    }
  }

  def getIdentifierByParty(party: String, partiesWithId: List[Record], idts: List[Record]): Option[Record] = {
    val partyWithId = partiesWithId.find(_.get[Party]("p").getValue == party)
    partyWithId match {
      case Some(pId) =>
        idts.find(idt => idt.getOptional[Record]("issuerReference").flatMap(_.getOptional[Text]("reference")).contains(pId.get[Text]("id")))
      case _ => None
    }
  }

  def identifierToString(identifier: Record): List[String] = {
    identifier.getList[Record]("assignedIdentifier").map(ai =>
      ai.get[Record]("identifier").get[Text]("value").getValue + "-" + ai.getOptional[Int64]("version").map(_.getValue).getOrElse(0)
    )
  }

  def findPartyByReference(reference: String, partiesWithId: List[Record]): Option[Party] = {
    partiesWithId.find(_.get[Text]("id").getValue == reference).map(_.get[Party]("p"))
  }

  def emptyArg = new Record(Collections.emptyList[Field])

  def archiveArg: Value = Unit.getInstance().asInstanceOf[Value]
}
