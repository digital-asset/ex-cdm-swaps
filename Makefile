# Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

SHELL := /usr/bin/env bash
.SHELLFLAGS := -euo pipefail -c

######
# all
######

.PHONY: all
all: build test

.PHONY: build
build: build-dar build-app

.PHONY: test
test: test-dar test-app test-integration


################
# dar pipeline
################

# test -> build

# damlc command - use docker or local
damlc := daml damlc --

# results
dar_test_result := dist/DarTests.xml
dar_build_result := dist/CdmSwaps.dar

# source
damlsrc := daml


# dar test
.PHONY: test-dar
test-dar: $(dar_test_result)

# TODO - move to junit files when new version of SDK comes out
$(dar_test_result): $(shell find $(damlsrc) -type f) daml.yaml
	@echo test triggered because these files changed: $?
	$(damlc) test --junit $@ $(damlsrc)/Test/Event.daml


# dar build
.PHONY: build-dar
build-dar: $(dar_build_result)

$(dar_build_result): $(dar_test_result)
	@echo build triggered because these files changed: $?
	$(damlc) package $(damlsrc)/Main.daml CdmSwaps


################
# app pipeline
################

# results
app_build_result := app/target/scala-2.12/cdmswaps_2.12-0.1.jar

# source
appsrc := app/src

# app build
.PHONY: build-app
build-app: $(app_build_result)

$(app_build_result): $(shell find $(appsrc) -type f)
	@echo build triggered because these files changed: $?
	(cd app; sbt package)

########
# clean
########

.PHONY: clean
clean:
	-rm -vfr dist/*
