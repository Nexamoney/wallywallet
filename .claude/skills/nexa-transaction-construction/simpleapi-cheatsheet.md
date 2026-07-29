# simpleapi cheat sheet (units, `payTo`, `ofGroup`)

The `org.nexa.libnexakotlin.simpleapi` sugar for expressing amounts and building outputs in
readable terms. One-line example per construct. Grounded in libnexakotlin `simpleapi.kt`; the
declarations there are authoritative.

## Units → satoshis (`Long`)

All three are extension `val`s on `Int` / `UInt` / `Long`, returning a satoshi `Long`:

| Extension | Meaning | Example | Result (sats) |
| --- | --- | --- | --- |
| `.sat` | satoshis (identity) | `500.sat` | `500` |
| `.nexa` | whole NEXA (×100) | `5.nexa` | `500` |
| `.mexa` | million NEXA (×100 000 000) | `2.mexa` | `200_000_000` |

```kotlin
val fee   = 1000.sat            // 1000 satoshis
val price = 10.nexa             // 1000 satoshis (10 NEXA)
val big   = 1.mexa              // 100_000_000 satoshis (1,000,000 NEXA)
```

> NEXA's finest unit is the satoshi and `1 NEXA == 100 satoshis` (two decimal places) — **not**
> 1e8 like BTC. Always do math in satoshi `Long`; never mix a whole-NEXA number into a satoshi
> field (see the "Mixing satoshi and whole-NEXA units" anti-pattern in `SKILL.md`).

### Going the other way — satoshis → display (libnexakotlin `currency.kt`)

The `.nexa`/`.sat` extensions lift literals *into* satoshis; for the display direction the library
ships exact-decimal helpers (all `BigDecimal`-based — never format amounts through `Double`):

```kotlin
SatToNexa(sats)                 // Long sats → BigDecimal whole NEXA (2-decimal math mode)
SatToString(sats)               // Long sats → formatted display string ("12,345.67")
NexaToSat(nexa)                 // Long or BigDecimal whole NEXA → Long sats (sanity-capped)
NexaDecimal(x) / CurrencyDecimal(x)   // BigDecimals with the right rounding/precision modes
SATperNEX                       // the 100 constant, if you need it by name
```

`CurrencyDecimal` (16 decimal places) is for intermediate math (e.g. fiat conversion);
`NexaDecimal` (2 places) matches the coin's display convention.

## Pay native coin: `amount payTo destination`

`payTo` is an infix function on `Int`/`Long`/`ULong` (a satoshi amount) → `iTxOutput`, overloaded
for every destination form:

```kotlin
val o1 = 10.nexa payTo recipientAddress          // PayAddress
val o2 = 1000.sat payTo "nexa:nqtsq5g5..."        // address String
val o3 = 500.sat  payTo someSatoshiScript          // a SatoshiScript (custom locking script)
val o4 = 750.sat  payTo payDestination             // a PayDestination
tx.add(o1)                                         // add the output to your tx
```

## Pay a token: `qty ofGroup gid payTo destination`

`ofGroup` / `ofToken` (synonyms) tag a quantity with a `GroupId`, giving a
`Pair<ULong, GroupId>`; that pair has its own `payTo` overloads:

```kotlin
val tokenOut = 5 ofGroup myGroupId payTo recipientAddress     // 5 tokens of myGroupId
val byString = 10 ofToken "nexa:tr…(group id)…" payTo destination
tx.add(tokenOut)
```

`ofGroup`/`ofToken` accept the `GroupId` as a `GroupId` object or a `String`. The token amount is
in the token's finest unit (apply `decimal_places` for display only — see `nexa-tokens-and-groups`).

## The wallet `send` overload family

All return the signed `iTransaction` (see `SKILL.md` § "simpleapi sugar for paying" for the knob
meanings — `deductFeeFromAmount`, `sync`, `note`, `minConfirms`):

```kotlin
wallet.send(sats, payAddress)                       // also String-address and SatoshiScript forms
wallet.send(sats, addr, deductFeeFromAmount = true) // fee comes out of the sent amount
wallet.send(listOf(addr1 to sats1, addr2 to sats2)) // multi-recipient in one tx
wallet.send(tokenQty, destAddress, groupId)         // one token output (fund/sign/broadcast)
wallet.sendNative(out1, out2)                       // multiple prebuilt native outputs
wallet.send(tx)                                     // broadcast a tx you already completed
```

## Build outputs explicitly: `txOutputFor(...)` and `dust(...)`

When you want the output object directly rather than via `payTo`:

```kotlin
txOutputFor(chain)                                   // an empty output for the chain
txOutputFor(chain, amountSats, lockingScript)        // amount + explicit script
txOutputFor(coinAmount, payAddress)                  // native coin to an address
txOutputFor(payAddress, tokenAmount, groupId)        // a token output (coinAmount auto-filled to the minimum)
txOutputFor(payAddress, tokenAmount, groupId, coinAmount)  // token output with explicit native funding
val minOut = dust(chain)                              // the chain's dust threshold (Long), the floor for an output's native value
```

A token output still needs a small amount of native coin to carry it; pass `coinAmount = null`
(or omit it) to let `txOutputFor` use the minimum, and never set a native value below `dust(chain)`.

## Build template/contract outputs: `NexaScript` / `NexaConstraint`

`NexaScript(vararg OP)` is a `SatoshiScript` builder; it has template/constraint helpers used when
hand-building contract outputs (the `SatoshiScript.p2t` path in `nexa-npl-smart-contracts` is the
other route):

```kotlin
NexaScript(OP.DUP, OP.HASH160, /* … */)              // a raw SatoshiScript from opcodes
ns.ungroupedP2t(argsScript)                          // a P2T output script (no group)
ns.constraint(groupInfo, argsScript, visArgs)        // a grouped constraint (token-bearing contract output)
NexaConstraint(gid, tokenQty, templateHash, argsScript, visArgs)   // top-level constraint builder
NexaArgs(arg1, arg2, …)                              // assemble a push-only args SatoshiScript
```

(Reach for these only when you are hand-constructing template outputs; for ordinary payments
`payTo` / `txOutputFor` are enough. Token specifics live in `nexa-tokens-and-groups`.)

## Related

- `SKILL.md` — the three build flavors, `txCompleter`/`TxCompletionFlags`, broadcast, finality.
- `templates/build-partial-tx.kt` — drop-in builders for the three flavors.
- `nexa-tokens-and-groups` — `GroupId`, token amounts, and `decimal_places`.