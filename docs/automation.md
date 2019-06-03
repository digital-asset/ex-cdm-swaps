# Automation and Integration

The Java Reactive Components Ledger Bindings library is used to integrate with the ledger and build automation via bots. Full details of this library, as well as other examples, can be found in its documentation [Java Bindings](https://docs.daml.com/packages/bindings-java-tutorial/index.html).

## Automation

### MarketSetupBot

The **MarketSetup** bot is run as part of initializing the market. It auto-accepts all master agreement proposals and cash transfer requests.

### EventBot

The **Event** bot has two main purposes:

1. It instructs cash transfers from an `EventInstance` once the ledger time is after the event date of the event.

2. It triggers the lifecylcing of an event once the ledger time is after the effective date of the event and all cash transfer instructions are allocated.

### CashBot

The **Cash** bot auto-allocates cash to any cash transfer instruction that is available.

### DerivedEventsBot

The **DerivedEvents** bot cleans up old derived events if the corresponding contract does not exist anymore.

###  DemoBot

The **Demo** bot is special in a sense that it automates processes that typically happen manually. For the sake of showing workflows as part of a demo, it is possible to automate them though. This includes the auto-acceptance of event proposals and the creation of derived events whenever possible. Note that the [configuration](/app/src/main/resources/application.conf#L27-L33>) of the app allows to exclude certain events from being auto-processed.


## Integration

The application comes with a simple REPL that allows to send commands via the shell. This includes:

### Initializing the market
The market is initialized by running

    initMarket(directory, time)

It sets the ledger time accordingly and loads master agreements from `MasterAgreement.csv`, holiday calendar reference data from `HolidayCalendar.csv`, and an initial set of cash from `Cash.csv`. The `MarketSetup` bot ensures that all master agreement proposals and cash transfers are accepted. See `examples/Market` for some example data.

### Proposing new events
A list of events (in json format) can be proposed by running

    loadEvents(directory)

The proposal is triggered by the first party through a corresponding MasterAgreementInstance. `examples/NewTrade` and `examples/TradeEvents` include some examples.


### Publishing rate fixings
A single rate fixing can be published by Reuters by running

    publishRateFixing(publisher, date, rateIndex, tenor, value)

It is also possible to publish a list of rate fixings from a csv file by running

    publishRateFixings(file)


### Deriving events
Future events for a single contract can be derived by running

    deriveEvents(party, contractRosettaKey)

The next derived event can then be created by

    createNextDerivedEvent(party, contractRosettaKey, eventQualifier)

Go to [Demo](demo.md) to see the commands in action.
