// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.app

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import java.util.{Optional, UUID, function}

import com.daml.ledger.rxjava.components.{Bot, LedgerViewFlowable}
import com.daml.ledger.rxjava.DamlLedgerClient
import com.daml.ledger.rxjava.components.helpers.{CommandsAndPendingSet}
import com.daml.ledger.javaapi.data.{Command, Event, FiltersByParty, Identifier, LedgerOffset, Record, Transaction}
import com.digitalasset.daml_lf_1_7.DamlLf
import com.digitalasset.daml_lf_1_7.DamlLf1
import com.digitalasset.ledger.api.v1.PackageServiceOuterClass.{GetPackageRequest, ListPackagesRequest}
import com.google.protobuf.{CodedInputStream, Timestamp}
import io.grpc.ManagedChannelBuilder
import com.digitalasset.ledger.api.v1.PackageServiceGrpc
import io.reactivex.Flowable

import scala.collection.JavaConverters._
import com.digitalasset.ledger.api.v1.testing.TimeServiceGrpc
import com.digitalasset.ledger.api.v1.testing.TimeServiceOuterClass.{GetTimeRequest, SetTimeRequest}

case class Config
  (
    appId: String,
    hostIp: String,
    hostPort: Integer,
    maxRecordOffset: Integer,
    useStaticTime: Boolean
  )

class LedgerClient(config: Config) {
  private val client = DamlLedgerClient.forHostWithLedgerIdDiscovery(config.hostIp, config.hostPort, Optional.empty())
  client.connect()
  private val templateName2id = loadTemplates()

  // Time client
  private val channel = ManagedChannelBuilder.forAddress(config.hostIp, config.hostPort).usePlaintext.build
  private val timeClient = if (config.useStaticTime) TimeServiceGrpc.newBlockingStub(channel) else null
  private val ledgerId = client.getLedgerId

  val maxRecordOffset: Integer = config.maxRecordOffset
  val appId: String = config.appId

  // Get template id by name
  def getTemplateId(name: String) = templateName2id(name)

  // Get current ledger time
  def getTime(): Instant = {
    if(config.useStaticTime) {
      val getRequest = GetTimeRequest.newBuilder()
        .setLedgerId(ledgerId)
        .build()
      val time = timeClient.getTime(getRequest).next.getCurrentTime
      Instant.ofEpochSecond(time.getSeconds, time.getNanos)
    } else {
      Instant.now()
    }
  }

  // Set current ledger time
  def setTime(newTime: Instant): Unit = {
    if(config.useStaticTime) {
      val currentTime = getTime()
      if (currentTime.isBefore(newTime)) {
        val currentTimestamp = Timestamp.newBuilder().setSeconds(currentTime.getEpochSecond).setNanos(currentTime.getNano).build
        val newTimestamp = Timestamp.newBuilder().setSeconds(newTime.getEpochSecond).setNanos(newTime.getNano).build

        val setRequest = SetTimeRequest.newBuilder()
          .setLedgerId(ledgerId)
          .setCurrentTime(currentTimestamp)
          .setNewTime(newTimestamp)
          .build()

        timeClient.setTime(setRequest);
        ()
      }
    } else
      throw new UnsupportedOperationException("can only set time if static time is used.")
  }

  // Send a list of commands
  def sendCommands(party: String, commands: List[Command]): Unit = {
    val currentTime = getTime()
    val maxRecordTime = currentTime.plusSeconds(30)
    client.getCommandClient.submitAndWait(
      UUID.randomUUID().toString,
      config.appId,
      UUID.randomUUID().toString,
      party,
      currentTime,
      maxRecordTime,
      commands.asJava
    )
    ()
  }

  // Send a list of commands and wait for transaction
  def sendCommandsAndWaitForTransaction(party: String, commands: List[Command]): Transaction = {
    val currentTime = getTime()
    val maxRecordTime = currentTime.plusSeconds(30)
    client.getCommandClient.submitAndWaitForTransaction(
      UUID.randomUUID().toString,
      config.appId,
      UUID.randomUUID().toString,
      party,
      currentTime,
      maxRecordTime,
      commands.asJava
    ).blockingGet()
  }

  // Wire new bot
  def wireBot(transactionFilter: FiltersByParty, run: LedgerViewFlowable.LedgerView[Record] => Flowable[CommandsAndPendingSet]): Unit = {
    val runImpl: function.Function[LedgerViewFlowable.LedgerView[Record], Flowable[CommandsAndPendingSet]] = view => run(view)

    Bot.wire(config.appId, client, transactionFilter, runImpl, x => x.getCreateArguments)
  }

  def buildInMemoryDataStore(transactionFilter: FiltersByParty, update: Event => Unit) = {
    val acsOffset = new AtomicReference[LedgerOffset](LedgerOffset.LedgerBegin.getInstance)

    client.getActiveContractSetClient.getActiveContracts(transactionFilter, true)
      .blockingForEach{response =>
        response.getOffset.ifPresent(offset => acsOffset.set(new LedgerOffset.Absolute(offset)))
        response.getCreatedEvents.forEach(e => update(e))
      }

    client.getTransactionsClient.getTransactions(acsOffset.get, transactionFilter, true).forEach{ t =>
      t.getEvents.forEach(e => update(e))
    }
  }

  // Load all existing templates
  private def loadTemplates(): Map[String, Identifier] = {
    val packages = getPackages()

    // Get all templates
    packages.flatMap {
      case (packageId, lfPackage) =>
        lfPackage
          .getModulesList.asScala
          .flatMap(m => {
            val moduleName = utils.LF.getModuleName(lfPackage, m)
            m.getTemplatesList.asScala.map(t => {
              val templateName = utils.LF.getTemplateName(lfPackage, t)
              (templateName, new Identifier(packageId, moduleName, templateName))
            })
          })
    }
  }

  // Get all package
  def getPackages(): Map[String, DamlLf1.Package] = {
    // Build Channel
    val cb = ManagedChannelBuilder.forAddress(config.hostIp, config.hostPort)
    cb.usePlaintext
    cb.maxInboundMessageSize(50 * 1024 * 1024)
    val channel = cb.build()

    // Create PackageService
    val packageService = PackageServiceGrpc.newBlockingStub(channel)
    val ledgerId = client.getLedgerId

    val packageIds = packageService
      .listPackages(ListPackagesRequest.newBuilder().setLedgerId(ledgerId).build)
      .getPackageIdsList
      .asByteStringList().asScala

    packageIds.map(packageId => {
      val pId = packageId.toStringUtf8
      val packageResponse =
        packageService
          .getPackage(GetPackageRequest.newBuilder().setLedgerId(ledgerId).setPackageId(pId).build)

      val cos = CodedInputStream.newInstance(packageResponse.getArchivePayload.toByteArray)
      cos.setRecursionLimit(1000)
      val payload = DamlLf.ArchivePayload.parseFrom(cos)
      (pId, payload.getDamlLf1)

  }).toMap
  }
}
