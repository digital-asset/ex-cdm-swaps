# Swaps: Example DAML Application
[![CircleCI](https://circleci.com/gh/DACH-NY/ex-cdm-swaps.svg?style=svg&circle-token=770cc8e608a1260764040bacfe5e6c4031f4b5ea)](https://circleci.com/gh/DACH-NY/ex-cdm-swaps)

> Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved. SPDX-License-Identifier: Apache-2.0

This is an application for lifecycling interest rate swaps (IRS) and credit default swaps (CDS) across their lifetime using ISDA's [CDM](https://portal.cdm.rosetta-technology.io) including the [CDM event specification module](TBA). This covers derived events like a reset or interest rate payment as well as negotiated events like new trade, (partial) termination, and (partial) novation events.

In addition, a simple cash asset model was added to the application to illustrate how cash is moved between parties while applying events.

Fore more details, go to

* [DAML implementation](docs/daml.md): This section describes the DAML model behind the application.

* [Automation and Integration](docs/automation.md): This section describes how processes are automated and how the application can be used via a simple REPL.

* [Demo](docs/demo.md):  This section describes a demo flow for the application.


## Prerequisites

* [DAML SDK](https://daml.com/) for building and running DAML code
* [sbt](https://www.scala-sbt.org/) for building and running automation


## Setting up the application

To set up the application:

* Build the DAML package by running:

      da compile

* Build the application by running:

      (cd app; sbt compile)

   The application consists of two parts:
    1. A customized REPL that allows to send commands to the ledger.
    2. Automation for certain processes like applying an event once it is due.


## Starting the application

To start the application, run each of the following commands in a separate shell:

* Start the sandbox by running:

      da run sandbox -- target/CdmSwaps.dar --scenario Setup:empty --port 7600 &> sandbox.log


* Start the navigator by running:

      java -Xmx6g -Xss1024k -d64 -jar $(da path navigator) server --port 7500 localhost 7600 &> navigator.log

   It is recommended to reduce the zoom of the browser to show all tables properly.

* Start the customized REPL by running:

      (cd app/; sbt "runMain com.digitalasset.app.REPL")


* Start the automation by running:

      (cd app/; sbt "runMain com.digitalasset.app.Bots {includeDemo}")

   Set ``includeDemo`` to ``true`` or ``false`` depending on whether the application is run in [demo](docs/demo.md) mode.
