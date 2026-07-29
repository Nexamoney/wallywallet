# CLTV / locktime cheat sheet

One-page reference for `OP_CHECKLOCKTIMEVERIFY` and transaction `nLockTime` on Nexa. Read
`SKILL.md` for the reasoning; this is the quick lookup. The `nSequence` rule is grounded in
libnexakotlin's transaction `var sequence` field; the threshold and MTP rules are chain consensus.

## The two-part rule (both required)

For a `nLockTime` / `OP_CHECKLOCKTIMEVERIFY` constraint to take effect, **two** things must hold:

1. **The spending input's `nSequence` must be `< 0xFFFFFFFF`.** A sequence of `0xFFFFFFFF`
   *disables* locktime entirely — the tx is final regardless of `nLockTime`, and a CLTV check on it
   fails. libnexakotlin's field comment says it outright:
   `var sequence: Long = 0xffffffff  //!< enable locktime if not 0xffffffff`.
   Use `0xFFFFFFFE` to enable locktime while not opting into relative-locktime semantics. (Note
   `0xFFFFFFFE` is already a `Long` in Kotlin — no unsigned dance needed.)
2. **The tx's `nLockTime` must have been reached** — measured against **median-time-past (MTP)**
   for timestamp locktimes, or block height for height locktimes (see threshold below).

Forgetting (1) is the classic "my timeout contract won't spend / spends too early" bug — see the
"Forgetting to set `input.sequence`" anti-pattern in `nexa-transaction-construction`.

## `nLockTime`: height vs timestamp

`nLockTime` is interpreted as a **block height** if it is below the locktime threshold, and as a
**Unix timestamp (epoch seconds)** if at or above it:

| `nLockTime` value | Interpreted as |
| --- | --- |
| `< 500_000_000` | block height |
| `>= 500_000_000` | epoch-seconds timestamp |

`500_000_000` (the `LOCKTIME_THRESHOLD`) is ~Nov 1985 as a timestamp and far beyond any real block
height, so the split is unambiguous. Pick one domain and stay in it; don't mix a height into a
timestamp deadline.

## MTP — why your deadline lags wall-clock

A **timestamp** locktime is compared against **median-time-past**, not the latest block's time and
not wall-clock. MTP is the **median of the last 11 block timestamps**, so it trails real time
(commonly by roughly an hour, depending on block cadence). Consequences:

- A timestamp deadline becomes spendable only once **MTP** passes it — which is *after*
  wall-clock passes it. Build in margin.
- A node operator reads exact MTP directly (`getblock(...).mediantime` — see `nexa-rpc-node-client`).
  Without a node, estimate MTP from the last 11 block headers' `time` fields (see `mtpMonitor.kt`).
- The script VM evaluates the CLTV stack comparison but does **not** know chain MTP, so a CLTV
  spend can pass an offline VM replay yet still be non-final on-chain (`nexa-script-machine-testing`).

## Picking a realistic timeout

Production settlement timeouts are measured in **hours to days**, not minutes — the sub-hour values
that "work" on an auto-mining regtest do not survive MTP lag plus real block variance on a live
chain. Rule of thumb: set the deadline far enough out that normal MTP lag and a few slow blocks
can't strand a counterparty, and pick the side of the margin that fails safe for your contract
(e.g. give the refund path generous headroom past the claim path).

## In NPL (the DSL side)

- `checkLockTimeVerify(locktime: NInt)` emits `OP_CHECKLOCKTIMEVERIFY` and leaves the locktime on
  the stack; it presumes the spending input sets `nSequence < 0xFFFFFFFF` (the contract can't set
  that — the spender must). See `nexa-npl-smart-contracts/dslReference.md` §5.
- `checkSequenceVerify(seq: NInt)` is the relative-locktime (CSV) counterpart.

## Related

- `SKILL.md` — full reasoning, the `nSequence` detail, and timeout selection.
- `mtpMonitor.kt` — compute/estimate current MTP.
- `nexa-transaction-construction` — setting `input.sequence` when building the spend.
- `nexa-rpc-node-client` — exact MTP via `getblock(...).mediantime`.
- `nexa-script-machine-testing` — the VM checks the CLTV op but not chain MTP.