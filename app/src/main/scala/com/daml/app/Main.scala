// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.app

import com.typesafe.config.ConfigFactory

import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.ILoop
import scala.collection.JavaConverters._

object REPL {
  def main(args: Array[String]): Unit = {
    if (args.length != 2) throw new Exception("arguments 'host port' required")
    Commands.host = args(0).toString
    Commands.port = args(1).toInt
    Commands.init()

    val settings = new Settings
    settings.embeddedDefaults[Commands.type]
    new sys.SystemProperties += (
      "scala.repl.autoruncode" -> "repl.init",
      )
    new SampleILoop().process(settings)
  }
}

// The REPL App can't be debugged directly
object Debug extends App {
  val t0 = System.nanoTime()
  val t1 = System.nanoTime()

  println("Elapsed time: " + (t1 - t0) / 1000000 + "ms")
}


class SampleILoop extends ILoop {
  override def prompt = "DA $ "

  override def printWelcome() {
    echo("\nWelcome to the Digital Asset Scala REPL\n")
  }
}
