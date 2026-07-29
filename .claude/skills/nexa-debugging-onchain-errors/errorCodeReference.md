# Reject / error code reference

A fuller reference for the codes and messages behind broadcast and P2P rejections, to complement
the symptom→cause table in `SKILL.md`. Two distinct code spaces are involved: the **P2P reject
message codes** (when a node rejects your tx/block over the network) and the **`NexaRpcException`
code** (when a JSON-RPC call fails). Grounded in the Nexa spec
(`https://spec.nexa.org/network/messages/reject`) and nexarpc `NexaRpc.kt`.

## P2P reject message

When a node rejects a relayed object it sends a `reject` message:

| Field | Format | Notes |
| --- | --- | --- |
| rejected message type | var string | the command being rejected, e.g. `"tx"` |
| rejection code | 1 byte | see table below |
| rejection reason | var string | human-readable; **do not** branch on this string programmatically — it can change |
| rejection data | varies | for object rejections, usually the 32-byte hash (txid / block hash) of the rejected object |

### Rejection codes

| Name | Value | In response to | Meaning |
| --- | --- | --- | --- |
| `REJECT_MALFORMED` | `0x01` | any | message could not be deserialized |
| `REJECT_INVALID` | `0x10` | block, tx, filter | block/tx is invalid (e.g. failed script verification), or an xthin filter is too small |
| `REJECT_OBSOLETE` | `0x11` | version | node no longer supports your protocol version |
| `REJECT_DUPLICATE` | `0x12` | (unused) | |
| `REJECT_NONSTANDARD` | `0x40` | tx | tx is non-standard, or not final per relative-locktime rules |
| `REJECT_DUST` | `0x41` | tx | tx has an output below the dust threshold |
| `REJECT_INSUFFICIENTFEE` | `0x42` | tx | tx doesn't pay enough fee to be relayed |
| `REJECT_CHECKPOINT` | `0x43` | (unused) | |

Mapping the ones you'll actually hit when broadcasting:

- **`REJECT_INVALID` (0x10)** on a tx → a consensus failure. For a contract spend this is usually a
  **script-verify failure** — reproduce it locally in the script VM to get the exact failing opcode
  and stack (`nexa-script-machine-testing`; see also the `OP_EQUALVERIFY` row in `SKILL.md`).
- **`REJECT_NONSTANDARD` (0x40)** → often a locktime/finality issue (the spend isn't final yet — the
  `nSequence < 0xFFFFFFFF` + MTP rules in `nexa-locktime-cltv`) or a non-standard script shape.
- **`REJECT_DUST` (0x41)** → an output's native value is below `dust(chain)`; raise it
  (`nexa-transaction-construction` / `simpleapi-cheatsheet.md`). Token outputs still need dust-level
  native funding.
- **`REJECT_INSUFFICIENTFEE` (0x42)** → the "mempool min fee not met" case. **Unfixable by
  rebroadcasting the same bytes** — rebuild with a larger fee (fee is size-proportional; a constant
  buffer tuned for a small spend underpays a larger one). See the fee-sizing caveat in `SKILL.md` /
  `nexa-transaction-construction`.

## Broadcast result strings (idempotency)

Some "errors" are actually success on a retry — fold them in (the `broadcast-tx.kt` template does):

| Message substring | Treat as |
| --- | --- |
| `txn-already-in-mempool` | success (already accepted) |
| `already in block chain` | success (already mined) |
| `already known` | success |
| `mempool min fee not met` / `min relay fee` | fee too low — rebuild (see `REJECT_INSUFFICIENTFEE`) |
| `missing inputs` / `txn-mempool-conflict` / `already spent` | rejected — an input is gone/double-spent |
| `mandatory-script-verify-flag-failed` | script-verify failure — replay in the VM |

## JSON-RPC errors (`NexaRpcException`)

Every nexarpc call failure throws:

```kotlin
open class NexaRpcException(msg: String, val code: Long) : Exception(msg)
```

**Catch `NexaRpcException`, not `IOException`** — auth and node-side errors surface this way,
carrying the node's numeric `code` and message. Two failure shapes bypass it: a transport failure
(node down/unreachable) propagates raw — e.g. `java.net.ConnectException` on JVM — and a kotlinx
`SerializationException` is thrown when the node's reply shape doesn't match the client's data
classes (a large node↔artifact version gap). Two common cases:

- **`Unauthorized (bad rpc username/password)` / HTTP 401** → wrong RPC credentials or the node's
  `rpcuser`/`rpcpassword` (or `rpcallowip`) don't match. See the RPC-401 section in `SKILL.md`.
- A rejected `sendrawtransaction` surfaces the same reject reason strings as the table above, as the
  exception message.

## Related

- `SKILL.md` — the symptom→cause→owning-skill triage table.
- `nexa-script-machine-testing` — reproduce a `REJECT_INVALID` script failure locally (exact opcode).
- `nexa-locktime-cltv` — `REJECT_NONSTANDARD` finality causes.
- `nexa-transaction-construction` / `templates/broadcast-tx.kt` — fee sizing and idempotent broadcast.
- `nexa-rpc-node-client` — `NexaRpcException`, the `getstat`-vs-pool-info surface, RPC auth.