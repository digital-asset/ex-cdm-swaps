// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.app.utils

import java.io.File

import com.google.gson.{JsonElement, JsonObject, JsonParser}

import scala.io.Source

object Json {

  def loadJsons(dir: String): List[JsonObject] = {
    val d = new File(dir)
    if (d.exists && d.isDirectory) {
      d.listFiles.filter(_.isFile).toList.filter(x => x.getName.endsWith("json")).flatMap(loadJson)
    } else {
      List()
    }
  }

  def loadJson(file: File): Option[JsonObject] = {
    if (file.exists) {
      try {
        val content = Source.fromFile(file).getLines.mkString
        Some(new JsonParser().parse(content).getAsJsonObject)
      } catch {
        case _: Throwable => println("Error reading file " + file.getName); None
      }
    } else {
      println("File does not exist " + file.getName)
      None
    }
  }

  implicit class JsonBuilder(j: JsonObject) {
    def addGeneric(k: String, v: Any): JsonObject = {
      v match {
        case s: String => j.addProperty(k, s); j
        case c: Character => j.addProperty(k, c); j
        case b: Boolean => j.addProperty(k, b); j
        case n: Number => j.addProperty(k, n); j
        case e: JsonElement => j.add(k, e); j
      }
    }
  }
}