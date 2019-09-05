// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

import * as React from 'react';
import * as UICore from '@da/ui-core';

// ------------------------------------------------------------------------------------------------
// Schema version (all config files will need to export this name)
// ------------------------------------------------------------------------------------------------
export const version = {
  schema: 'navigator-config',
  major: 2,
  minor: 0,
};

// ------------------------------------------------------------------------------------------------
// Table helper functions
// ------------------------------------------------------------------------------------------------

function keyRight(x) {
  return ({
    key: x['key'],
    title: x['title'],
    createCell: x['createCell'],
    sortable: x['sortable'],
    width: x['width'],
    weight: x['weight'],
    alignment: 'right',
  });
}

function keyColumn(title, width, key, op = simpleWrap) {
  return keyColumns(title, width, [key], (xs, c) => op(xs[0], c))
}

function keyColumns(title, width, keys, op) {
  return ({
    key: keys[0],
    title,
    createCell: ({cellData}) => {
      try {
        return op(keys.map((key) => getValue(cellData, key)), cellData);
      } catch (e) {
        console.log(e);
        return <i>Error</i>;
      }
    },
    sortable: true,
    width,
    weight: 1,
    alignment: 'left',
  });
}

function simpleWrap(s) {
  if (s === Object(s))
    debugger; // throw Error('Got an Object to keyColumn');
  return { type: 'react', value: <span>{s}</span> };
}

function rowsWrap(xs)
{
  return { type: 'react', value:
    <div>
      {xs.map((x) => <div key={x}>{x}</div>)}
    </div>
  }
}

function getValue(c, key) {
  const ks = key.split('.');
  function f(v, i)
  {
    if (i === ks.length)
      switch (v['type']) {
        case 'party': return v['value'];
        case 'list': return v.value.map(x => f(x, i));
        case 'text': return v['value'];
        case 'int64': return v['value'];
        case 'decimal': return v['value'];
        case 'numeric': return v['value']
        case 'date': return v['value'];
        case 'optional': return (v['value'] === null) ? {'None' : {}} : {'Some' : v['value']};
        case 'enum': return v['constructor'];
        case 'variant': {
          const res = {};
          res[v['constructor']] = v['value'];
          return res
        }
        default: {
          return v
        }
      }
    else if (ks[i] === '')
      return f(v, i + 1);
    else {
      if(ks[i] === 'argument')
        return f(v[ks[i]], i + 1);
      else if (ks[i] === 'templateName')
        return v['argument']['id']['name']
      else
        switch (v['type']) {
          case 'record': return f(v['fields'].filter((field) => field['label'] === ks[i])[0].value, i+1);
          case 'list': return f(v['value'][ks[i]], i+1);
          case 'optional':
            if (ks[i] == 'Some')
              return f(v['value'], i+1)
          case 'variant': {
            if (v['constructor'] === ks[i])
              return f(v['value'], i+1)
          }
        }
    }
  }
  return f(c, 0);
}

var formatDate = function(days) {
  return days
};

function flatten(xs)
{
    let res = [];
    for (let i = 0; i < xs.length; i++)
        res = res.concat(xs[i]);
    return res;
}

// ------------------------------------------------------------------------------------------------
// Helper Functions
// ------------------------------------------------------------------------------------------------

function assIdtsByParty(party, partiesWithId, identifier) {
  const partyWithId = partiesWithId.filter((pId) => getValue(pId, "p") === party);
  if (partyWithId.length === 0) {
    return []
  } else {
    const identifierMatch = identifier.filter((x) => 'Some' in getValue(x, "issuerReference") && getValue(x, "issuerReference.Some.reference.Some") === getValue(partyWithId[0], "id"));
    if (identifierMatch.length === 0) {
      return []
    } else {
      const assIdts = getValue(identifierMatch[0], "assignedIdentifier");
      return assIdts.map(assIdtToString)
    }
  }
}

function assIdtToString(assIdt) {
  const version = ('Some' in getValue(assIdt, "version")) ? getValue(assIdt, "version.Some") : 0;
  return (assIdt.length === 0) ? 'Unknown' : getValue(assIdt, "identifier.value") + '-' + version
}

function toEnumString(x) {
  return x.replace(RegExp('.*Enum_'), '');
}

function toCommas(value) { // From StackOverflow
  return value.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

function formatDate(date) {
  return new Date(date).toLocaleDateString('en-GB')
}

function status(name) {
  switch (name) {
    case 'EventInstance': return 'Accepted';
    case 'EventProposal': return 'Proposed';
    case 'EventNotification': return 'Applied';
    case 'DerivedEvent': return 'Unrealised';
    default: return 'Unknown';
  }
}

function contractRefRosettaKeys(argument) {
  return ('Some' in getValue(argument, "d.lineage")) ? getValue(argument, "d.lineage.Some.contractReference").map(x => getValue(x, "reference.Some")) : []
}

function findParty(partyReference, partiesWithId) {
  let result = '???';
  partiesWithId.forEach((pId) => {
    if (getValue(pId, "id") === partyReference)
      result = getValue(pId, "p").split('-')[1];
  });
  return result;
}

function findAccount(partyReference, partyData, accounts) {
  let result = `???`
  partyData.forEach((p) => {
    if ('Some' in getValue(p, "id") && getValue(p, "id.Some") === partyReference)
      if ('Some' in getValue(p, "legalEntity") && 'Some' in getValue(p, "legalEntity.Some.id"))
        accounts.forEach((a) => {
          if ('Some' in getValue(a, "servicingParty") && getValue(a, "servicingParty.Some") === getValue(p, "legalEntity.Some.id.Some"))
            result = getValue(a, "accountNumber")
        })
  });
  return result;
}

function eventButton(data, party) {
  if (data.rowData.template.id.includes("Main.Event.Proposal:EventProposal")) {
    const alreadySigned = getValue(data.rowData, "argument.sigs");
    if (alreadySigned.indexOf(party) !== -1)
      return { type: 'text', value: 'Signed' }
    else
      return { type: 'choices-button'}
  } else if(data.rowData.template.id.includes("Main.Event.Instance:EventInstance")) {
    return { type: 'choices-button'}
  }
  else
    return { type: 'text', value: '' }
}


// ------------------------------------------------------------------------------------------------
// Views
// ------------------------------------------------------------------------------------------------

// 0. RefData
// 0.1 RefData_IrFixing
const refDataIrFixingView = {
  type: 'table-view',
  title: "RefData_IrFixing",
  source: {
    type: 'contracts',
    filter: [
      { field: "template.id", value: "Main.ReferenceData:ObservationInstance" }
    ],
    search: "",
    sort: [ { field: "id", direction: "ASCENDING" } ]
  },
  columns: [
    keyColumn(
      'RateIndex', 100,
      'argument.d.source.curve.Some.interestRateCurve.Some.floatingRateIndex.value',
      (x) => simpleWrap(toEnumString(x))
    ),
    keyColumn(
      'Tenor', 100,
      'argument.d.source.curve.Some.interestRateCurve.Some.tenor',
      (x) => simpleWrap(getValue(x, "periodMultiplier") + toEnumString(getValue(x, "period")))
    ),
    keyColumn(
      'Date', 100,
      'argument.d.date',
      (x) => simpleWrap(formatDate(x))
    ),
    keyColumn('Value', 100,
      'argument.d.observation',
      (x) => simpleWrap((x * 100).toFixed(2))
    ),
  ]
}


// 1. User
// 1.1 MasterAgreement
const masterAgreementView = {
  type: 'table-view',
  title: "User_MasterAgreement",
  source: {
    type: 'contracts',
    filter: [
      { field: "template.id", value: "Main.MasterAgreement:MasterAgreementInstance" }
    ],
    search: "",
    sort: [ { field: "id", direction: "ASCENDING" } ]
  },
  columns: [
    keyColumn('Party A', 30, 'argument.p1'),
    keyColumn('Party B', 30, 'argument.p2'),
  ]
}

// 2. Market
// 2.1. Contract
function marketContractView(party) {
  return {
    type: 'table-view',
    title: "Market_Contract",
    source: {
      type: 'contracts',
      filter: [
        { field: "template.id", value: "Main.Market.Contract:ContractInstance" }
      ],
      search: "",
      sort: [ { field: "argument.d.rosettaKey", direction: "ASCENDING" } ]
    },
    columns: [
      keyColumns(
        'Contract Id', 60,
        ['argument.ps', 'argument.d.contractIdentifier'],
        (xs) => rowsWrap(assIdtsByParty(party, xs[0], xs[1]))
      ),
      keyColumn(
        'Contract Key', 60,
        'argument.d.rosettaKey'
      ),
      keyColumn(
        'Party A', 20,
        'argument.ps.0.p',
        (x) => simpleWrap(x.split('-')[1])
      ),
      keyColumn(
        'Party B', 20,
        'argument.ps.1.p',
        (x) => simpleWrap(x.split('-')[1])
      ),
      keyColumn(
        'Qualifier', 30,
        'argument.d.contractualProduct.productIdentification.Some.productQualifier.Some',
        (x) => simpleWrap(shortQualifier(x))
      ),
      keyColumn(
        'Underlyings', 180,
        'argument.d',
        (x) => rowsWrap(getUnderlyings(x))
      ),
      keyRight(keyColumn(
        'Notional', 60,
        'argument.d.contractualProduct.economicTerms.payout.interestRatePayout.0.quantity.Some.notionalSchedule.Some.notionalStepSchedule.initialValue',
        (x) => simpleWrap(toCommas(x))
      )),
      keyColumn(
        'Currency', 60,
        'argument.d.contractualProduct.economicTerms.payout.interestRatePayout.0.quantity.Some.notionalSchedule.Some.notionalStepSchedule.currency.value'
      ),
      keyColumn(
        'Effective', 60,
        'argument.d.contractualProduct.economicTerms.payout.interestRatePayout.0.calculationPeriodDates.effectiveDate.Some.adjustableDate.Some',
        (x) => simpleWrap(formatDate(getDate(x)))
      ),
      keyColumn(
        'Termination', 60,
        'argument.d.contractualProduct.economicTerms.payout.interestRatePayout.0.calculationPeriodDates.terminationDate.Some.adjustableDate.Some',
        (x) => simpleWrap(formatDate(getDate(x)))
      ),
    ]
  }
}

function shortQualifier(x)
{
  switch (x) {
    case 'InterestRate_IRSwap_FixedFloat': return 'IRS';
    case 'InterestRate_IRSwap_Basis': return 'IRSBasis';
    case 'Credit_Default_Swap_Index': return 'CDS';
    default: return x;
    }
}

function getUnderlyings(cd) {
  const irps = getValue(cd, "contractualProduct.economicTerms.payout.interestRatePayout")
  return irps.map(getIrpUnderlying).filter(String)
}

function getIrpUnderlying(irp) {
  if ('Some' in getValue(irp, "rateSpecification.floatingRate")) {
    const fr = getValue(irp, "rateSpecification.floatingRate.Some");
    const tenor = getValue(fr, "indexTenor.Some");
    return toEnumString(getValue(fr, "floatingRateIndex.value")) + '-' + getValue(tenor, "periodMultiplier") + toEnumString(getValue(tenor, "period"))
  } else return undefined
}

function getDate(x) {
  return 'Some' in getValue(x, 'adjustedDate') ? getValue(x, "adjustedDate.Some.value") : getValue(x, "unadjustedDate");
}

// 2.2. Cash
function marketCashView(party) {
  return {
    type: 'table-view',
    title: "Market_Cash",
    source: {
      type: 'contracts',
      filter: [
        { field: "template.id", value: "Main.Market.Cash:Cash" },
        (party === "ECB" || party === "FED") ? { field: "argument.owner", value: "" } : { field: "argument.owner", value: party },
      ],
      search: "",
      sort: [ { field: "id", direction: "ASCENDING" } ]
    },
    columns: [
      keyColumn(
        'User', 40,
        'argument.owner'
      ),
      keyColumn(
        'Account', 40,
        'argument.account'
      ),
      keyRight(keyColumn(
        'Amount', 40,
        'argument.amount',
        (x) => simpleWrap(toCommas(Math.round(x)))
      )),
      keyColumn('Ccy', 40,
        'argument.currency'
      ),
    ]
  }
}

// 3. Event
// 3.1. New Trade
function eventsNewTradeView(party) {
  return {
    type: 'table-view',
    title: "Event_NewTrade",
    source: {
      type: 'contracts',
      filter: [
        { field: "template.id", value: "Main" },
        { field: "argument.d.eventQualifier.Some", value: 'NewTrade' }
      ],
      search: "",
      sort: [ { field: "argument.d.eventDate", direction: "ASCENDING" } ]
    },
    columns: [
      {
        key: "AcceptButton",
        title: "AcceptButton",
        sortable: false,
        width: 45,
        weight: 0,
        alignment: "left",
        createCell: (data) => eventButton(data, party)
      },
      keyColumn(
        'Status', 20,
        'templateName',
        (name) => simpleWrap(status(name))
      ),
      keyColumns(
        'Contract Id', 60,
        ['argument.ps', 'argument.d.primitive.inception.0.after.contract.contractIdentifier'],
        (xs) => rowsWrap(assIdtsByParty(party, xs[0], xs[1]))
      ),
      keyColumn(
        'Contract Key', 60,
        'argument.d.primitive.inception.0.after.contract.rosettaKey'
      ),
      keyColumn(
        'Party A', 20,
        'argument.ps.0.p',
        (x) => simpleWrap(x.split('-')[1])
      ),
      keyColumn(
        'Party B', 20,
        'argument.ps.1.p',
        (x) => simpleWrap(x.split('-')[1])
      ),
      keyColumn(
        'Qualifier', 30,
        'argument.d.primitive.inception.0.after.contract.contractualProduct.productIdentification.Some.productQualifier.Some',
        (x) => simpleWrap(shortQualifier(x))
      ),
      keyColumn(
        'Underlyings', 180,
        'argument.d.primitive.inception.0.after.contract',
        (x) => rowsWrap(getUnderlyings(x))
      ),
      keyRight(keyColumn(
        'Notional', 60,
        'argument.d.primitive.inception.0.after.contract.contractualProduct.economicTerms.payout.interestRatePayout.0.quantity.Some.notionalSchedule.Some.notionalStepSchedule.initialValue',
        (x) => simpleWrap(toCommas(x))
      )),
      keyColumn(
        'Currency', 60,
        'argument.d.primitive.inception.0.after.contract.contractualProduct.economicTerms.payout.interestRatePayout.0.quantity.Some.notionalSchedule.Some.notionalStepSchedule.currency.value'
      ),
      keyColumn(
        'Effective', 60,
        'argument.d.primitive.inception.0.after.contract.contractualProduct.economicTerms.payout.interestRatePayout.0.calculationPeriodDates.effectiveDate.Some.adjustableDate.Some',
        (x) => simpleWrap(formatDate(getDate(x)))
      ),
      keyColumn(
        'Termination', 60,
        'argument.d.primitive.inception.0.after.contract.contractualProduct.economicTerms.payout.interestRatePayout.0.calculationPeriodDates.terminationDate.Some.adjustableDate.Some',
        (x) => simpleWrap(formatDate(getDate(x)))
      ),
    ]
  }
}

// 3.2. Termination
function eventsTerminationView(party) {
  return {
    type: 'table-view',
    title: "Event_Termination",
    source: {
      type: 'contracts',
      filter: [
        { field: "template.id", value: "Main" },
        { field: "argument.d.eventQualifier.Some", value: 'Termination' }
      ],
      search: "",
      sort: [ { field: "argument.d.eventDate", direction: "ASCENDING" } ]
    },
    columns: [
      {
        key: "AcceptButton",
        title: "AcceptButton",
        sortable: false,
        width: 45,
        weight: 0,
        alignment: "left",
        createCell: (data) => eventButton(data, party)
      },
      keyColumn(
        'Status', 20,
        'templateName',
        (name) => simpleWrap(status(name))
      ),
      keyColumns(
        'Contract Id', 60,
        ['argument.ps', 'argument.d.primitive.quantityChange.0.before.contract.Some.contractIdentifier'],
        (xs) => rowsWrap(assIdtsByParty(party, xs[0], xs[1]))
      ),
      keyColumn(
        'Contract Key', 60,
        'argument.d.primitive.quantityChange.0.before.contract.Some.rosettaKey'
      ),
      keyRight(keyColumn(
        'Notional Before', 60,
        'argument.d.primitive.quantityChange.0.before.contract.Some.contractualProduct.economicTerms.payout.interestRatePayout.0.quantity.Some.notionalSchedule.Some.notionalStepSchedule.initialValue',
        (x) => simpleWrap(toCommas(x))
      )),
      keyRight(keyColumn(
        'Notional After', 60,
        'argument.d.primitive.quantityChange.0.after.contract.Some.contractualProduct.economicTerms.payout.interestRatePayout.0.quantity.Some.notionalSchedule.Some.notionalStepSchedule.initialValue',
        (x) => simpleWrap(toCommas(x))
      )),
      keyColumn(
        'Currency', 60,
        'argument.d.primitive.quantityChange.0.before.contract.Some.contractualProduct.economicTerms.payout.interestRatePayout.0.quantity.Some.notionalSchedule.Some.notionalStepSchedule.currency.value'
      ),
      keyColumn(
        'Event Date', 50,
        'argument.d.eventDate',
        (x) => simpleWrap(formatDate(x))
      ),
      keyColumn(
        'Effective Date', 30,
        'argument.d.effectiveDate.Some',
        (x) => simpleWrap(formatDate(x))
      ),
      keyRight(keyColumn('Fee', 30, 'argument', (d) => rowsWrap(amount(d)))),
      keyColumn('Payer;Receiver', 50, 'argument', (arg) => rowsWrap(payerReceiver(arg))),
    ]
  }
}

function payerReceiver(arg) {
  const pays = payer(arg);
  const recs = receiver(arg);
  return pays.map((pay, i) => pay + ";" + recs[i])
}

// 3.3. Novation
function eventsNovationView(party) {
  return {
    type: 'table-view',
    title: "Event_Novation",
    source: {
      type: 'contracts',
      filter: [
        { field: "template.id", value: "Main" },
        { field: "argument.d.eventQualifier.Some", value: 'Novation' }
      ],
      search: "",
      sort: [ { field: "argument.d.eventDate", direction: "ASCENDING" } ]
    },
    columns: [
      {
        key: "AcceptButton",
        title: "AcceptButton",
        sortable: false,
        width: 45,
        weight: 0,
        alignment: "left",
        createCell: (data) => eventButton(data, party)
      },
      keyColumn(
        'Status', 20,
        'templateName',
        (name) => simpleWrap(status(name))
      ),
      keyColumns(
        'Old Id', 60,
        ['argument.ps', 'argument.d.primitive.quantityChange.0.before.contract.Some.contractIdentifier'],
        (xs) => rowsWrap(assIdtsByParty(party, xs[0], xs[1]))
      ),
      keyColumns(
        'Old Parties', 60,
        ['argument.d.primitive.quantityChange.0.before.contract.Some.party', 'argument.ps'],
        (xs) => simpleWrap(parties(xs[0], xs[1]))
      ),
      keyColumns(
        'New Id', 60,
        ['argument.ps', 'argument.d.primitive.inception.0.after.contract.contractIdentifier'],
        (xs) => rowsWrap(assIdtsByParty(party, xs[0], xs[1]))
      ),
      keyColumns(
        'New Parties', 60,
        ['argument.d.primitive.inception.0.after.contract.party', 'argument.ps'],
        (xs) => simpleWrap(parties(xs[0], xs[1]))
      ),
      keyRight(keyColumn(
        'New Notional', 60,
        'argument.d.primitive.inception.0.after.contract.contractualProduct.economicTerms.payout.interestRatePayout.0.quantity.Some.notionalSchedule.Some.notionalStepSchedule.initialValue',
        (x) => simpleWrap(toCommas(x))
      )),
      keyColumn(
        'Currency', 60,
        'argument.d.primitive.inception.0.after.contract.contractualProduct.economicTerms.payout.interestRatePayout.0.quantity.Some.notionalSchedule.Some.notionalStepSchedule.currency.value'
      ),
      keyColumn(
        'Event Date', 50,
        'argument.d.eventDate',
        (x) => simpleWrap(formatDate(x))
      ),
      keyColumn(
        'Effective Date', 30,
        'argument.d.effectiveDate.Some',
        (x) => simpleWrap(formatDate(x))
      ),
      keyRight(keyColumn('Fee', 30, 'argument', (d) => rowsWrap(amount(d)))),
      keyColumn('Payer;Receiver', 50, 'argument', (arg) => rowsWrap(payerReceiver(arg))),
    ]
  }
}

function parties(party ,ps) {
  return party.map(p => findParty(getValue(p, "id.Some"), ps)).join(', ')
}

// 3.4. Reset
function eventsResetView(party) {
  return {
    type: 'table-view',
    title: "Event_Reset",
    source: {
      type: 'contracts',
      filter: [
        { field: "template.id", value: "Main" },
        { field: "argument.d.eventQualifier.Some", value: 'Reset' }
      ],
      search: "",
      sort: [ { field: "argument.d.eventDate", direction: "ASCENDING" } ]
    },
    columns: [
      keyColumn(
        'Status', 20,
        'templateName',
        (name) => simpleWrap(status(name))
      ),
      keyColumn(
        'Contract Key', 60,
        'argument',
        (x) => rowsWrap(contractRefRosettaKeys(x))
      ),
      keyColumn(
        'Fixing Date', 50,
        'argument.d.eventDate',
        (x) => simpleWrap(formatDate(x))
      ),
      keyColumn(
        'Reset Date', 50,
        'argument.d.effectiveDate.Some',
        (x) => simpleWrap(formatDate(x))
      ),
      keyColumn(
        'Value [%]', 30,
        'argument.d.primitive.reset',
        (x) => rowsWrap((x.length > 0) ? x.map((r) => (getValue(r, "resetValue") * 100).toFixed(2)) : x)
      ),
    ]
  }
}

// 3.5. Cash Transfer
function eventsCashTransferView(party) {
  return {
    type: 'table-view',
    title: "Event_CashTransfer",
    source: {
      type: 'contracts',
      filter: [
        { field: "template.id", value: "Main" },
        { field: "argument.d.eventQualifier.Some", value: 'CashTransfer' }
      ],
      search: "",
      sort: [ { field: "argument.d.eventDate", direction: "ASCENDING" } ]
    },
    columns: [
      keyColumn(
        'Status', 20,
        'templateName',
        (name) => simpleWrap(status(name))
      ),
      keyColumn(
        'Contract Key', 60,
        'argument',
        (x) => rowsWrap(contractRefRosettaKeys(x))
      ),
      keyColumn(
        'Transfer Status', 45,
        'argument',
        (arg) => rowsWrap(transferStatus(arg))
      ),
      keyColumn(
        'Type', 45,
        'argument',
        (arg) => rowsWrap(transferType(arg))
      ),
      keyColumn(
        'Receiver', 30,
        'argument',
        (arg) => rowsWrap(receiver(arg))
      ),
      keyColumn(
        'Payer', 30,
        'argument',
        (arg) => rowsWrap(payer(arg))
      ),
      keyRight(keyColumn(
        'Amount', 30,
        'argument',
        (arg) => rowsWrap(amount(arg))
      )),
      keyColumn(
        'Currency', 30,
        'argument',
        (arg) => rowsWrap(currency(arg))
      ),
      keyColumn(
        'Event Date', 50,
        'argument.d.eventDate',
        (x) => simpleWrap(formatDate(x))
      ),
      keyColumn(
        'Effective Date', 30,
        'argument.d.effectiveDate.Some',
        (x) => simpleWrap(formatDate(x))
      ),
    ]
  }
}

function transferStatus(arg) {
  return getValue(arg, "d.primitive.transfer").map((t) => 'Some' in getValue(t, "status") ? toEnumString(getValue(t, "status.Some")) : 'Pending')
}

function transferType(arg) {
  const transfers = getValue(arg, "d.primitive.transfer");
  const cts = flatten(transfers.map((t) => getValue(t, "cashTransfer")));
  return cts.map((ct) => ('Some' in getValue(ct, "cashflowType")) ? toEnumString(getValue(ct, "cashflowType.Some")) : '').filter(onlyUnique)
}

function onlyUnique(value, index, self) {
  return self.indexOf(value) === index;
}

function receiver(arg) {
  const transfers = getValue(arg, "d.primitive.transfer");
  const cts = flatten(transfers.map((t) => getValue(t, "cashTransfer")));
  const receiverReferences = cts.map((ct) => getValue(ct, "payerReceiver.receiverPartyReference.reference.Some"));
  return receiverReferences.map((rec) => findParty(rec, getValue(arg, "ps")))
}

function payer(arg) {
  const transfers = getValue(arg, "d.primitive.transfer");
  const cts = flatten(transfers.map((t) => getValue(t, "cashTransfer")));
  const payerReferences = cts.map((ct) => getValue(ct, "payerReceiver.payerPartyReference.reference.Some"));
  return payerReferences.map((pay) => findParty(pay, getValue(arg, "ps")))
}

function amount(arg) {
  const transfers = getValue(arg, "d.primitive.transfer");
  const cts = flatten(transfers.map((t) => getValue(t, "cashTransfer")));
  return cts.map((ct) => toCommas(Math.round(getValue(ct, "amount.amount") * 100) / 100))
}

function currency(arg) {
  const transfers = getValue(arg, "d.primitive.transfer");
  const cts = flatten(transfers.map((t) => getValue(t, "cashTransfer")));
  return cts.map((ct) => getValue(ct, "amount.currency.value"))
}


// ------------------------------------------------------------------------------------------------
// Combine
// ------------------------------------------------------------------------------------------------

export const customViews = (userId, party, role) => ({
  refDataIrFixing: refDataIrFixingView,
  masterAgreement: masterAgreementView,
  marketContract: marketContractView(party),
  marketCash: marketCashView(party),
  eventsNewTrade: eventsNewTradeView(party),
  eventsTermination: eventsTerminationView(party),
  eventsNovation: eventsNovationView(party),
  eventsReset: eventsResetView(party),
  evensCashTransfer: eventsCashTransferView(party),
});
