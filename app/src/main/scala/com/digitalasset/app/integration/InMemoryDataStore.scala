// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.app.integration

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

import com.daml.ledger.javaapi.data.{ArchivedEvent, CreatedEvent, Event, Filter, FiltersByParty, InclusiveFilter, Record}
import com.digitalasset.app.LedgerClient

import scala.collection.JavaConverters._
import scala.collection.mutable

class InMemoryDataStore(party: String, template: String, ledgerClient: LedgerClient) {
  ledgerClient.buildInMemoryDataStore(transactionFilter, update)

  def getData(): List[(String, Record)] = data.toList

  private lazy val data: mutable.Map[String, Record] = new ConcurrentHashMap[String, Record]().asScala

  private def update(e: Event): Unit = {
      e match {
        case ce: CreatedEvent => data += ((ce.getContractId, ce.getArguments))

        case ae: ArchivedEvent if data.contains(ae.getContractId) => data -= ae.getContractId

        case _ => ()
      }
  }

  private def transactionFilter: FiltersByParty = {
    val inclusiveFilter: Filter = new InclusiveFilter(Set(template).map(ledgerClient.getTemplateId).asJava)
    val filter = Collections.singletonMap(party, inclusiveFilter)
    new FiltersByParty(filter)
  }
}
