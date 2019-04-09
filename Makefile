# Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

SHELL := /usr/bin/env bash
.SHELLFLAGS := -euo pipefail -c

# logic to force use docker builders
ifneq ($(FORCE_DOCKER),true)
	local_da := $(shell which da)
	local_sbt := $(shell which sbt)
endif


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
damlc_cmd := da run damlc --

sdk_version ?= $(shell cat da.yaml | grep sdk-version | tr -d ' ' | cut -d':' -f2)
damlc_docker_cmd := \
	docker run -t --rm \
	-v $(PWD):/usr/src/ \
	-w /usr/src \
	digitalasset/daml-sdk:$(sdk_version)-master $(damlc_cmd)

damlc := $(if $(local_da), $(damlc_cmd), $(damlc_docker_cmd))

# results
dar_test_result := target/DarTests.xml
dar_build_result := target/CdmSwaps.dar

# source
damlsrc := daml


# dar test
.PHONY: test-dar
test-dar: $(dar_test_result)

# TODO - move to junit files when new version of SDK comes out
$(dar_test_result): $(shell find $(damlsrc) -type f) da.yaml
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

# build

# maven command - use docker or local
sbt_cmd := sbt

sbt_version ?= 11.0.2_2.12.8_1.2.8
sbt_docker_cmd := \
  docker run -t --rm \
  -v $(PWD):/usr/src/ \
  -w /usr/src \
  hseeberger/scala-sbt:$(sbt_version) $(sbt_cmd)

sbt := $(if $(local_sbt), $(sbt_cmd), $(sbt_docker_cmd))

# results
app_build_result := app/target/scala-2.12/cdmswaps_2.12-0.1.jar

# source
appsrc := app/src

# app build
.PHONY: build-app
build-app: $(app_build_result)

$(app_build_result): $(shell find $(appsrc) -type f)
	@echo build triggered because these files changed: $?
	(cd app; $(sbt) package)

########
# clean
########

.PHONY: clean
clean:
	-rm -vfr target/*
