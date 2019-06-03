# Demo

Make sure that the automation (bots) is started with the demo mode enabled. Each command needs to be executed in the REPL.

## Parties

The following parties are involved in the demo:
* A central counter party (CCP-P01) with one account.
* Three dealers (DEALER-D01, ..., DEALER-D03) with five accounts each.
* Five clients (CLIENT-C01, ..., CLIENT-C05) with three accounts each.
* FED and ECB for issuing USD and EUR, respectively.
* REUTERS for publishing reference data.

Note that the central counter party, the dealers, and clients can all perform the same actions. The application does not grant them any specific roles.

## Market Setup

In order to initialize the market, run

    initMarket("../examples/Market", "2018-10-26T12:00:00Z")

to

  * set the ledger time to 2018-10-26T12:00:00Z,
  * load a set of master agreements between the central counter party, dealers, and clients,
  * distribute cash by the ECB and FED,
  * publish some holiday calendars by REUTERS.

## Negotiated events

The first part of the demo deals with negotiated events where all parties involved in an event need to agree first before it can be applied.

### New Trade

1. Run

        loadEvents("../examples/NewTrade")

    to load around 50 new trade events. The `DemoBot` auto-accepts all events except two.

2. Log in as CCP-P01, go to `Event_NewTrade`, and accept the proposed new trade event for contract `CAXMKVEHOV-1` by exercising the `Accept` choice. This changes the status of the event to `Applied`.

3. Note that the new trade event for contract `QZPRYFPGA1-1`was proposed by CPP-P01 but is pending the acceptance of CLIENT-C04. Log in as CLIENT-C04 and accept the event by exercising the `Accept` choice. Note that all contracts are now available in the `Market_Contract` tab.

4. Log in as CCP-P01 again and notice that the event is applied as well.

### Termination and Novation

1. Set the ledger time 2018-11-12 and run

        loadEvents("../examples/TradeEvents")

      This loads a few (partial) termination and (partial) novation events. As before, the `DemoBot` auto-accepts all events except two.

2. Log in as CCP-P01 and inspect the termination (`Event_Termination`) and novation (`Event_Novation`) events.

3. Log in as DEALER-D03 and exercise the `Accept` choice of the novation event. Note that the event is still in the proposed status because it has not been accepted by all parties yet.

3. Log in as CLIENT-C03 and exercise the `Accept` choice of the termination and novation event. Both events are now accepted by all parties. Note though that it has not been applied yet because the ledger time is still before the event`s effective time.

5. Move the ledger time by one day and notice that the events get applied. Go to the `Market_Contract` tab to see that contract `BHNAFG4NEC` is terminated and has version 2.

## Derived events

The second part of the demo deals with derived events like resets or cash transfers which are implied from the contract like an interest rate swap. Those events are different in a sense that they do not have to be accepted by all parties involved anymore.

1. Log in as CCP-P01 and run

        deriveEvents("CCP-P01", "1b78d43e")
        deriveEvents("CCP-P01", "1d74ef54")

2. Go to the `Event_Reset` and `Event_CashTransfer` tabs and notice that all future events for contract `ADPC2T37KM` (a USD 1M vs. 3M basis swap with key `1b78d43e`) and `N7TN6F2V7O` (a credit default swap with key `1d74ef54`) are displayed. They are neither proposed nor accepted by any party yet but serve as pure information only. Note that some of the cash transfers already have an indicative value because their amount is known (apart from possible changes in e.g. the underlying holiday calendars).

3. Set the ledger time to `2018-11-15`, log in as REUTERS, go to tab `RefData_IrFixing`, and run

          publishRateFixing("2018-11-15", "USD_LIBOR_BBA", "1M", 0.0002)

      This publishes a rate fixing. Note that the value appears in the tab.

4. Log in as CCP-P01 again to see the same rate fixing and re-run

          deriveEvents("CCP-P01", "1b78d43e")

      Go to `Event_Reset` to see that the reset has a value now.

5. Run

          createNextDerivedEvent("CCP-P01", "1b78d43e", "Reset")

      This creates the first reset event. Because it is derived from a contract, an event is created without proposing it the other parties first. Move the ledger time to `2018-11-19` and notice that the event gets applied.

6. Go to `Event_CashTransfer` and run

          createNextDerivedEvent("CCP-P01", "1b78d43e", "CashTransfer")

      Notice that the cash transfer for `1b78d43e` gets applied and the status of the transfer changes to settled.

Finally, all derived events for the next year will be applied:
1. Remove the (future) derived events which are not applied yet by running

          removeDerivedEvents("CCP-P01", "1b78d43e")
          removeDerivedEvents("CCP-P01", "1d74ef54")

2. Set the ledger time to `2019-09-26`.

3. Publish all rate fixings by running

          publishRateFixings("../examples/Fixings.csv")

4. Run

          deriveEventsAll("CCP-P01", None, Some("2019-09-24"))

      Notice that all reset and cash transfers get created. Log in as ECB to see that it sees all cash balances (tab `Market_Cash`), but it does not see where it is coming from, i.e. it does not see any cash transfer events.
