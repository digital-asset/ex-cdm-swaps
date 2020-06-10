#!/bin/bash

LH="--ledger-host localhost --ledger-port 6865 --dar .daml/dist/CdmSwaps-1.0.0.dar"
export JAVA_TOOL_OPTIONS="-Xmx256m"

echo "Starting Event triggers"
daml trigger --ledger-party CCP-P01    --trigger-name Main.Trigger.Event:eventTrigger $LH &
daml trigger --ledger-party DEALER-D01 --trigger-name Main.Trigger.Event:eventTrigger $LH &
daml trigger --ledger-party DEALER-D02 --trigger-name Main.Trigger.Event:eventTrigger $LH &
daml trigger --ledger-party DEALER-D03 --trigger-name Main.Trigger.Event:eventTrigger $LH &
daml trigger --ledger-party CLIENT-C01 --trigger-name Main.Trigger.Event:eventTrigger $LH &
daml trigger --ledger-party CLIENT-C02 --trigger-name Main.Trigger.Event:eventTrigger $LH &
daml trigger --ledger-party CLIENT-C03 --trigger-name Main.Trigger.Event:eventTrigger $LH &
daml trigger --ledger-party CLIENT-C04 --trigger-name Main.Trigger.Event:eventTrigger $LH &
daml trigger --ledger-party CLIENT-C05 --trigger-name Main.Trigger.Event:eventTrigger $LH &

echo "Starting DerivedEvent triggers"
daml trigger --ledger-party CCP-P01    --trigger-name Main.Trigger.DerivedEvents:derivedEventTrigger $LH &
daml trigger --ledger-party DEALER-D01 --trigger-name Main.Trigger.DerivedEvents:derivedEventTrigger $LH &
daml trigger --ledger-party DEALER-D02 --trigger-name Main.Trigger.DerivedEvents:derivedEventTrigger $LH &
daml trigger --ledger-party DEALER-D03 --trigger-name Main.Trigger.DerivedEvents:derivedEventTrigger $LH &
daml trigger --ledger-party CLIENT-C01 --trigger-name Main.Trigger.DerivedEvents:derivedEventTrigger $LH &
daml trigger --ledger-party CLIENT-C02 --trigger-name Main.Trigger.DerivedEvents:derivedEventTrigger $LH &
daml trigger --ledger-party CLIENT-C03 --trigger-name Main.Trigger.DerivedEvents:derivedEventTrigger $LH &
daml trigger --ledger-party CLIENT-C04 --trigger-name Main.Trigger.DerivedEvents:derivedEventTrigger $LH &
daml trigger --ledger-party CLIENT-C05 --trigger-name Main.Trigger.DerivedEvents:derivedEventTrigger $LH &

echo "Starting Cash triggers"
daml trigger --ledger-party CCP-P01    --trigger-name Main.Trigger.Cash:cashTrigger $LH &
daml trigger --ledger-party DEALER-D01 --trigger-name Main.Trigger.Cash:cashTrigger $LH &
daml trigger --ledger-party DEALER-D02 --trigger-name Main.Trigger.Cash:cashTrigger $LH &
daml trigger --ledger-party DEALER-D03 --trigger-name Main.Trigger.Cash:cashTrigger $LH &
daml trigger --ledger-party CLIENT-C01 --trigger-name Main.Trigger.Cash:cashTrigger $LH &
daml trigger --ledger-party CLIENT-C02 --trigger-name Main.Trigger.Cash:cashTrigger $LH &
daml trigger --ledger-party CLIENT-C03 --trigger-name Main.Trigger.Cash:cashTrigger $LH &
daml trigger --ledger-party CLIENT-C04 --trigger-name Main.Trigger.Cash:cashTrigger $LH &
daml trigger --ledger-party CLIENT-C05 --trigger-name Main.Trigger.Cash:cashTrigger $LH &

echo "Starting Demo triggers"
daml trigger --ledger-party CCP-P01    --trigger-name Main.Trigger.Demo:demoTrigger $LH &
daml trigger --ledger-party DEALER-D01 --trigger-name Main.Trigger.Demo:demoTrigger $LH &
daml trigger --ledger-party DEALER-D02 --trigger-name Main.Trigger.Demo:demoTrigger $LH &
daml trigger --ledger-party DEALER-D03 --trigger-name Main.Trigger.Demo:demoTrigger $LH &
daml trigger --ledger-party CLIENT-C01 --trigger-name Main.Trigger.Demo:demoTrigger $LH &
daml trigger --ledger-party CLIENT-C02 --trigger-name Main.Trigger.Demo:demoTrigger $LH &
daml trigger --ledger-party CLIENT-C03 --trigger-name Main.Trigger.Demo:demoTrigger $LH &
daml trigger --ledger-party CLIENT-C04 --trigger-name Main.Trigger.Demo:demoTrigger $LH &
daml trigger --ledger-party CLIENT-C05 --trigger-name Main.Trigger.Demo:demoTrigger $LH &

echo "All done"

wait
