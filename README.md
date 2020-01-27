# Swaps: Example DAML Application
[![CircleCI](https://circleci.com/gh/digital-asset/ex-cdm-swaps.svg?style=svg)](https://circleci.com/gh/digital-asset/ex-cdm-swaps)

> Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved. SPDX-License-Identifier: Apache-2.0

This is an application for lifecycling interest rate swaps (IRS) and credit default swaps (CDS) across their lifetime using ISDA's [CDM](https://portal.cdm.rosetta-technology.io) including the [CDM event specification module](https://github.com/digital-asset/lib-cdm-event-specification-module). This covers derived events like a reset or interest rate payment as well as negotiated events like new trade, (partial) termination, and (partial) novation events.

In addition, a simple cash asset model was added to the application to illustrate how cash is moved between parties while applying events.

Fore more details, go to

* [DAML implementation](docs/daml.md): This section describes the DAML model behind the application.

* [Automation and Integration](docs/automation.md): This section describes how processes are automated and how the application can be used via a simple REPL.

* [Demo](docs/demo.md):  This section describes a demo flow for the application.


## Prerequisites

* [DAML SDK](https://daml.com/) for building and running DAML code
* [sbt](https://www.scala-sbt.org/) for building and running automation
* [Docker](https://www.docker.com/) for running the application (optional)


## Setting up the application

To set up the application:

* Build the DAML package by running:

      daml build

* Build the application by running:

      (cd app; sbt compile)

   The application consists of two parts:
    1. A customized REPL that allows to send commands to the ledger.
    2. Automation for certain processes like applying an event once it is due.


## Starting the application

There are two options:

### Option 1: Stand-Alone

Run each of the following commands in a separate shell:

* Start the sandbox by running:

      daml sandbox .daml/dist/CdmSwaps-1.0.0.dar

* Start the navigator by running:

      daml navigator server

   It is recommended to reduce the zoom of the browser to show all tables properly.

* Start the customized REPL by running:

      (cd app/; sbt "runMain com.digitalasset.app.REPL localhost 6865")

* Start the automation by running:

      (cd app/; sbt "runMain com.digitalasset.app.Bots localhost 6865 {includeDemo}")

   Set ``includeDemo`` to ``true`` or ``false`` depending on whether the application is run in [demo](docs/demo.md) mode.

### Option 2: Docker

* Start the sandbox, navigator, and automation by running:

      docker-compose up --build --scale cdm-swaps-repl=0

  Set ``INCLUDE_DEMO`` to ``true`` (default) or ``false`` in ``docker-compose.yml`` depending on whether the application is run in [demo](docs/demo.md) mode.

* Start the customized REPL by running:

      docker-compose run cdm-swaps-repl sh

Note: Running the app via Docker needs up to 16 GB of memory. If you run on Windows or MacOS, you may need to increase the memory limit of the Docker Engine in the preferences if you encounter a java.lang.OutOfMemoryError: GC overhead limit exceeded error.
