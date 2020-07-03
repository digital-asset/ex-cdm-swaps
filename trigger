#!/bin/bash

HOST=localhost
PORT=8088
H1="Content-type: application/json"
H2="Accept: application/json"

function trigger() {
  if [ "$1" == "" ]; then
    echo "Invalid arguments: $@"
    echo "Usage: trigger start|stop|upload_dar|list|status|health [args]"
    exit 1
  fi

  CMD=$1
  shift

  case $CMD in
    start)
      start "$@"
      ;;
    stop)
      stop "$@"
      ;;
    upload_dar)
      upload_dar "$@"
      ;;
    list)
      list "$@"
      ;;
    status)
      status "$@"
      ;;
    health)
      health "$@"
      ;;
    *)
      echo "trigger <command>"
      echo "  commands are:"
      echo "    start - start a trigger"
      echo "    stop - stop a trigger"
      echo "    upload_dar - upload a DAR"
      echo "    list - list running triggers"
      echo "    status - show trigger status"
      echo "    health - show service health"
      ;;
  esac
}

function start() {
  if [ "$1" == "" ] || [ "$2" == "" ]; then
    echo "Invalid arguments: $@"
    echo "Usage: trigger start <party> <trigger-name>"
    exit 1
  fi

  PARTY=$1
  TRIGGER=$2
  if [ "$PACKAGE_ID" == "" ]; then
    PACKAGE_ID=$(daml damlc inspect-dar $DAR_FILE --json | jq -r '.main_package_id')
  fi

  curl -s -H "$H1" -H "$H2" --user "$PARTY:secret" -X POST $HOST:$PORT/v1/start -d '{"triggerName":"'$PACKAGE_ID':'$TRIGGER'"}' | jq
}

function stop() {
  if [ "$1" == "" ] || [ "$2" == "" ]; then
    echo "Invalid arguments: $@"
    echo "Usage: trigger stop <party> <trigger-id>"
    exit 1
  fi

  PARTY=$1

  if [ "$2" == "all" ]; then
    list $PARTY | jq -r '.result.triggerIds | .[]' | xargs -L1 -I'{}' curl -s -H "$H1" -H "$H2" --user "$PARTY:secret" -X DELETE $HOST:$PORT/v1/stop/{} | jq
  else
    TRIGGER_ID=$2
    curl -s -H "$H1" -H "$H2" --user "$PARTY:secret" -X DELETE $HOST:$PORT/v1/stop/$TRIGGER_ID | jq
  fi
}

function upload_dar() {
  if [ "$DAR_FILE" == "" ] && [ "$1" == "" ]; then
    echo "Invalid arguments: $@"
    echo "Usage if $DAR_FILE is set:     trigger upload_dar"
    echo "Usage if $DAR_FILE is not set: trigger upload_dar <dar-file>"
    exit 1
  fi

  curl -s -F "dar=@$DAR" $HOST:$PORT/v1/upload_dar | jq
}

function list() {
  if [ "$1" == "" ]; then
    echo "Invalid arguments: $@"
    echo "Usage: trigger list <party>"
    exit 1
  fi

  PARTY=$1

  curl -s -H "$H1" -H "$H2" --user "$PARTY:secret" -X GET $HOST:$PORT/v1/list | jq
}

function status() {
  if [ "$1" == "" ] || [ "$2" == "" ]; then
    echo "Invalid arguments: $@"
    echo "Usage: trigger status <party> <trigger-id>"
    exit 1
  fi

  PARTY=$1
  TRIGGER_ID=$2

  curl -s -H "$H1" -H "$H2" --user "$PARTY:secret" -X GET $HOST:$PORT/v1/status/$TRIGGER_ID | jq
}

function health() {
  curl -s -X GET $HOST:$PORT/v1/health | jq
}

trigger "$@"
