// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.app

import com.typesafe.config.ConfigFactory

import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.ILoop
import scala.collection.JavaConverters._

object REPL extends App {
  Commands.init()
  val settings = new Settings
  settings.embeddedDefaults[Commands.type]
  new sys.SystemProperties += (
    "scala.repl.autoruncode" -> "repl.init",
    )
  new SampleILoop().process(settings)
}

object Bots {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val parties = config.getStringList("parties").asScala.toList

    val includeDemo = if (args.isEmpty) throw new Exception("'includeDemo' required") else args(0).toBoolean
    val demoEventExclusionList = config.getStringList("bot.demoEventExclusionList").asScala.toList

    val client =
      new LedgerClient(
        Config(
          config.getString("id"),
          config.getString("platform.host"),
          config.getInt("platform.port"),
          config.getInt("platform.maxRecordOffset")
        )
      )

    parties.foreach(p => new bot.DerivedEvents(p, client))
    parties.foreach(p => new bot.Event(p, client))
    parties.foreach(p => new bot.Cash(p, client))
    if (includeDemo) parties.foreach(p => new bot.Demo(p, client, demoEventExclusionList))

    while(true) {Thread.sleep(10000)}
  }
}

// The REPL App can't be debugged directly
object Debug extends App {
  val t0 = System.nanoTime()
  val time = Commands.getTime()
  val t1 = System.nanoTime()

  println("Elapsed time: " + (t1 - t0) / 1000000 + "ms")
}


class SampleILoop extends ILoop {
  override def prompt = "DA $ "

  override def printWelcome() {
    echo("\nWelcome to the Digital Asset Scala REPL\n")
  }
}
