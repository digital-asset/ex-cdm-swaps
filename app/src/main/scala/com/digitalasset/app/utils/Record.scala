// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.app.utils

import java.time.LocalDate
import java.util.stream.Collectors

import com.daml.ledger.javaapi.data.{Record => RecordData}
import com.daml.ledger.javaapi.data.{Date, DamlOptional}

import scala.collection.JavaConverters._

object Record {

  implicit class RecordAccess(r: RecordData) {
    def get[T](label: String): T = {
      r
        .getFields.listIterator.asScala.toList.find(p => p.getLabel.get == label).get
        .getValue.asInstanceOf[T]
    }

    def getList[T](label: String): List[T] =
      r
        .getFields.listIterator.asScala.toList.find(p => p.getLabel.get == label).get
        .getValue.asList.get
        .getValues.stream
        .map[T](x => x.asInstanceOf[T]).collect(Collectors.toList())
        .asScala.toList

    def getOptional[T](label: String): Option[T] = {
      val optValue = r
        .getFields.listIterator.asScala.toList.find(p => p.getLabel.get == label).get
        .getValue.asInstanceOf[DamlOptional]
        .getValue


      if (optValue.isPresent) Some(optValue.get.asInstanceOf[T]) else None
    }

    }

  def toDamlOptionalDate(o: Option[LocalDate]): DamlOptional = {
    new DamlOptional(java.util.Optional.ofNullable(o.map(x => new Date(x.toEpochDay.toInt)).orNull))
  }

}
