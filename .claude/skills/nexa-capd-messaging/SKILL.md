---
name: nexa-capd-messaging
description: "Handles off-chain, peer-to-peer messaging over the Nexa network using the CAPD (Counterparty And Protocol Discovery) subprotocol, a decentralized, proof-of-work-rate-limited, ephemeral message bus. Use when exchanging data between parties with no direct connection, coordinating multi-step protocols (multisig signing rounds, atomic-swap setup, payment-channel negotiation), building rendezvous/discovery channels, or debugging CAPD messages rejected as not-solved or too-difficult. Triggers: CAPD, CapdMsg, CapdQuery, broadcastMsg, installMsgMonitor, powTargetBits, convoSecret, recindHash, public offer broadcast / order book over CAPD, protocol magic prefix. Not for permanent on-chain data (use OP_RETURN via nexa-transaction-construction), the wallet-app protocol (nexa-wallet-connection), or browser-server state (nexa-server-state-and-flows)."
---

# Nexa CAPD messaging (decentralized counterparty/protocol discovery)

## When to use this skill

Trigger when a developer needs **off-chain, peer-to-peer messaging over the Nexa network** —
exchanging data between parties who have no direct connection, coordinating a multi-step protocol
(multisig signing rounds, atomic-swap setup, payment-channel negotiation), or building a rendezvous
/ discovery channel — using Nexa's **CAPD** subprotocol. CAPD (Counterparty And Protocol Discovery)
is a feature unique to Nexa: a decentralized, proof-of-work-rate-limited, *ephemeral* message bus
carried by the same nodes that relay transactions. Concretely trigger on:

- Keywords: CAPD, `CapdMsg`, `CapdQuery`, `CapdProtocolCommunication`, `ProtocolCommunication`,
  `broadcastMsg`, `installMsgMonitor` / `removeMsgMonitor`, `reloadCapdInfo`, `capdRelayPriority`,
  `powTargetBits`, `setPowTargetHarderThanPriority`, `CapdSolvableCutoff`, `CapdTooDifficult`,
  `recindHash` / rescind, `convoSecret` / `convoId`, message proof-of-work, off-chain messaging,
  counterparty discovery, network rendezvous, `getp2p`, `prefixSize` / `msgPfx` / `receive(filter)`
  (conversation sub-channels), `MultisigWalletContract`, `WalletContract`,
  `ContractFormationInvitation`, `makeFormationInvitation` / `handleContractFormationInvitation`,
  `initializeMultisigContractLibrary`, `formMultisig`, "multisig wallet over CAPD",
  "N-of-M shared wallet".
- Tasks: "send a message to another party over the Nexa network without a direct connection",
  "coordinate a multisig signing round", "negotiate an atomic swap / contract off-chain", "set up
  an encrypted conversation channel over Nexa", "publish a short-lived discovery message", "why is
  my CAPD message rejected as not solved / too difficult".

**Negative triggers** — do NOT use this skill for:
- Putting data permanently on-chain — CAPD messages are **not** transactions and are **not** mined;
  they expire. For on-chain data use an `OP_RETURN` output (`nexa-transaction-construction`).
- The wallet↔app TDPP/nexid protocol (a different "messaging" concept entirely) — use
  `nexa-wallet-connection`.
- Reactive browser↔server state — use `nexa-server-state-and-flows`.
- Querying chain/tx/UTXO state — use `nexa-electrum-monitoring` or `nexa-rpc-node-client`.

## Mental model

CAPD is a **decentralized message bus on the Nexa P2P network** (spec:
`https://spec.nexa.org/network/capd/`). Nodes relay CAPD messages to each other the way they relay
transactions, but a CAPD message is *not* a transaction:

- **It is ephemeral.** Every message carries a `createTime` and an `expiration`; nodes drop it when
  it expires (or earlier, if its rescind preimage is published). There is no permanence, no
  consensus, no block. Treat CAPD as a short-lived bulletin board, not storage.
- **Proof-of-work replaces fees.** Instead of paying a fee, the sender **solves a small PoW** over
  the message (`powTargetBits`). This rate-limits spam: heavier/longer-lived/higher-priority
  messages cost more work. A node advertises the difficulty it currently wants (its
  `capdRelayPriority`), and you raise your target to match.
- **It is a broadcast, addressed by content.** There is no "to" field. Senders tag a message with a
  short conversation id; receivers install a *monitor* that filters the global CAPD stream for that
  tag. Anyone can see any message — so confidentiality is the sender's job (encrypt the payload).

Two layers, and apps almost always want the higher one:

1. **`CapdMsg` (+ `CapdQuery`)** — the raw message: `data`, `createTime`, `expiration`,
   `recindHash`, `powTargetBits`, `nonce`. You `solve()` it (do the PoW) and `chain.net.broadcastMsg(it)`.
   Receiving raw messages means installing a monitor on a P2P connection.
2. **`CapdProtocolCommunication`** — an **encrypted, conversation-scoped channel** built on CAPD,
   implementing the simple `ProtocolCommunication` interface (`start` / `stop` / `send(ByteArray)` /
   `receive(): Pair<ByteArray, Long>`). Two parties share a secret; the class derives a 4-byte
   conversation id and an AES-256 key from it, and handles solve/broadcast/monitor/decrypt for you.
   This is the right entry point for "two parties exchange messages over Nexa," including the
   contract-coordination flows libnexakotlin builds on top of it.

**Receiving requires a P2P connection, not just SPV/electrum.** `CapdProtocolCommunication.start()`
calls `chain.net.getp2p()` to obtain a P2P client and installs a message monitor on it. A wallet
connected only to an electrum/SPV endpoint can *broadcast* (`broadcastMsg`) but needs a P2P peer to
*receive* — make sure your `Blockchain`'s connection manager has one.

## Setup and versions

You need `libnexakotlin` and a connected `Blockchain` (see `nexa-wallet-lifecycle-and-chain` for
`blockchainFor`). CAPD is part of libnexakotlin core; pin per `nexa-project-setup`.

Imports:

```kotlin
import org.nexa.libnexakotlin.CapdMsg
import org.nexa.libnexakotlin.CapdProtocolCommunication
import org.nexa.libnexakotlin.CapdException
import org.nexa.libnexakotlin.CapdTooDifficult
import org.nexa.libnexakotlin.blockchainFor
```

`CapdProtocolCommunication` takes a `CoroutineContext`; its `send`/`receive` are `suspend`
functions, so drive them from a coroutine scope.

## Core patterns

### Pattern 1: An encrypted conversation between two parties (the usual entry point)

Both sides derive the channel from a **shared secret** they agreed on out of band (a passphrase, a
DH exchange, a value committed in a prior on-chain step). Everything else — the conversation id, the
encryption key, the PoW, the monitor — is handled for you:

```kotlin
val chain = blockchainFor(ChainSelector.NEXATESTNET)
val convo = CapdProtocolCommunication(chain, sharedSecretBytes, coroutineContext)

convo.start()                       // gets a P2P peer, installs the monitor for this conversation

// Send (payload is AES-256 encrypted under the shared secret, PoW-solved, then broadcast):
convo.send("offer: 100 TOKENS for 5000 NEXA".encodeToByteArray())

// Receive blocks until a message tagged with this conversation arrives; returns (plaintext, createTime):
val (reply, createdAtSec) = convo.receive()
println(reply.decodeToString())

convo.stop()                        // removes the monitor, cancels the scope
```

Both parties run the *same* code with the *same* `sharedSecretBytes`; the 4-byte `convoId =
hash256(secret + 0x00)[0:4]` and the AES key `hash256(secret + 0x01)` are derived identically on
each side. `receive()` silently skips messages whose conversation id doesn't match or that fail to
decrypt (i.e. other people's traffic), and skips self-declared-expired messages.

#### Sub-channels within a conversation: the message prefix (added in a recent release)

The constructor's full form is
`CapdProtocolCommunication(chain, convoSecret, coCtxt, name = "", prefixSize = 0)` — `name` is a
log label, and `prefixSize` reserves a fixed number of **plaintext prefix bytes** between the
conversation id and the encrypted payload. When `prefixSize > 0`:

```kotlin
val convo = CapdProtocolCommunication(chain, secret, coCtxt, name = "orders", prefixSize = 4)

convo.send(payload, msgPfx = "bid!".encodeToByteArray())   // tag this message (truncated/zero-padded to prefixSize)
val (msg, at) = convo.receive(filter = "bid!".encodeToByteArray())  // only messages whose prefix starts with the filter
```

- `send(ba)` (no prefix) zero-fills the prefix region; `send(ba, msgPfx)` writes your tag
  (`copyOf(prefixSize)` — a longer tag is truncated, a shorter one zero-padded).
- `receive(filter)` is a leading-bytes match on the prefix region (an empty filter matches all),
  letting one conversation carry several message kinds/roles without decrypting and inspecting
  every message. The plain `receive()` still returns everything.
- The prefix is **plaintext on the bus** — like the conversation id it is routing metadata, not a
  secret; never put sensitive data in it. Both sides must agree on `prefixSize` (it changes the
  wire layout, so a `prefixSize = 0` peer cannot decrypt a `prefixSize = 4` sender's messages).

### Pattern 2: Build, solve, and broadcast a raw `CapdMsg`

When you want a one-shot broadcast (a discovery beacon, a public notice) rather than a conversation:

```kotlin
val msg = CapdMsg("hello nexa".encodeToByteArray())
msg.expiration = 3600u                       // UShort seconds after createTime; default = never (max)
msg.solve()                                  // do the PoW; backdates createTime by 5s by default
require(msg.check())                          // verify it's solved before sending
chain.net.broadcastMsg(msg)                   // throws CapdException("Message is not solved") if not solved
```

`solve(time)` notes:
- `time` defaults to `5` → the message is **backdated 5 seconds** (a message that claims to be
  created in the *future* is rejected by relays). Pass an absolute epoch-seconds value for an
  explicit createTime, or `null` to leave `createTime` unchanged.
- It throws **`CapdTooDifficult`** if the required work exceeds `CapdSolvableCutoff` (~a minute on a
  single desktop CPU). If you hit this, lower the target/priority or offload the solve — don't let
  the UI hang on an unsolvable message.

#### Public protocol channels: a magic prefix instead of a conversation

`CapdProtocolCommunication` is for *private* two-party channels. The complementary raw-`CapdMsg`
idiom is a **public, discoverable protocol channel** — how an order book / open-offer market
runs over CAPD. Each message is plaintext and self-describing:

```kotlin
// [3-byte protocol magic][indexable summary fields][payload]
val contents = BCHserialized(SerializationType.NETWORK)
    .addExactBytes(byteArrayOf('T'.code.toByte(), 'K'.code.toByte(), 'D'.code.toByte()))  // protocol magic
    .addVariableSized(offerGroup.data.takeLast(6).toByteArray() +                        // indicative: what's offered
                      (recvGroup?.data?.takeLast(6)?.toByteArray() ?: ByteArray(6)))     //   (zero = native coin)
    .addVariableSized(partialTxBin)                                                      // authoritative: the half-signed offer tx
val msg = CapdMsg(contents.toByteArray())
```

The structure encodes a trust rule worth copying: the summary fields (which token for which)
are **indicative** — they exist so takers can find/filter offers by content — while the partial
transaction itself is **authoritative**. A lying summary is harmless: a taker who completes the
tx gets exactly what the tx's inputs/outputs say or the tx is invalid, so nobody trusts the
advertisement. The payload here is precisely the half-tx swap offer from
`nexa-transaction-construction` Pattern 6 (built with two `PARTIAL` `txCompleter` passes), and a
taker completes it with `txCompleter` — or you hand a specific counterparty the same offer via
`createTdppUrl` instead of broadcasting it.

Priority etiquette for a channel that must out-live the relay tier's decay: read the connected
node's `capdRelayPriority` (via `reloadCapdInfo`) and set your target a margin above it
(`setPowTargetHarderThanPriority(priority + 0.1)`), then `solve()` — and re-broadcast open
offers periodically (the ~10-minute relay decay in Pattern 3 applies to offers too).

### Pattern 3: Raise priority / set the right difficulty

Relays prefer higher-PoW messages when busy. Match the difficulty the connected node currently
wants (the conversation helper does this automatically from `reloadCapdInfo`):

```kotlin
msg.setPowTargetHarderThanPriority(capdRelayPriority)   // priority < 1.0 is clamped to 1.0
msg.solve(0)                                            // then solve at the new target
```

`capdRelayPriority` comes from the node via `reloadCapdInfo { capdRelayPriority = it.capdRelayPriority }`
(P2P client). A priority of 1.0 is the baseline; raise it to make your message out-compete others
for relay/retention.

**Priority decays with age — and crosses zero ~10 minutes after `createTime`, regardless of how
much PoW you put in** (the spec's priority function subtracts a linear age penalty calibrated so
priority hits 0 at 600 seconds — `https://spec.nexa.org/network/capd/`). So even a high-PoW message
naturally ages out of the relay tier within about ten minutes, and a longer `expiration` only keeps
it *retrievable by direct query* longer, not *relayed* longer. This is the concrete reason the
"design for loss" anti-pattern below matters: for a conversation that must outlive ~10 minutes,
plan to **re-broadcast** (re-solve) periodically until you get an application-level ack, rather than
assuming one send keeps propagating.

### Pattern 4: Early expiry with a rescind hash

To publish a message you can later *retract* before its natural expiration, set a `recindHash` (the
hash of a secret preimage). When you broadcast the preimage, relays drop the message:

```kotlin
msg.recindHash = libnexa.hash160(rescindPreimage)   // message expires when the preimage is published
```

Useful for "this offer is live until I cancel it" semantics on the bus.

### Pattern 5: The built-in multisig wallet contract (formation over CAPD)

libnexakotlin ships a complete, working example of a CAPD-coordinated multi-party protocol: the
**multisig wallet contract** in `org.nexa.libnexakotlin.contracts` (`common.kt` / `multisig.kt` /
`multisigDestination.kt`). N parties who share nothing but an out-of-band invitation string form an
M-of-N multisig destination — the pubkey exchange runs over a `CapdProtocolCommunication` channel
seeded by a random 16-byte `convoSecret` carried in the invitation.

```kotlin
initializeMultisigContractLibrary()      // once at startup: registers the "multisig" contract type

// Party A creates the contract on their wallet and invites the others. Create through the
// registered type (it also attaches the contract to the wallet, so save/load and UTXO
// tracking work); don't construct MultisigWalletContract bare:
val contract = contractTypes["multisig"]!!.create("ourEscrow", walletA) as MultisigWalletContract
contract.sigs = 2; contract.pubkeys = 3              // M-of-N (defaults are 2-of-3)
val invitation = contract.makeFormationInvitation()      // starts the CAPD formation protocol
// The invitation serializes to a universal link (same w.nexa.org convention as TDPP):
//   https://w.nexa.org/<chain>/invite/multisig/<name>?id=<convoSecretHex>&data=<paramsHex>
val invitationString = invitation.toString()             // hand this to the other parties OUT OF BAND

// Each other party accepts on their own wallet (this joins the same CAPD conversation):
handleContractFormationInvitation(invitationString, walletB)

// When formation completes, state flips FORMATION → ACTIVE and the contract exposes:
contract.destination                       // the shared multisig PayDestination to fund
contract.balance()                         // Map<GroupId, Balance> — coins + tokens it holds
contract.send(amount, address)             // spend (triggers the interactive signing round)
```

What the framework handles for you: the formation protocol (pubkey exchange + destination
derivation over CAPD), persistence (`save`/`load` into the wallet DB — the `convoSecret` is stored,
so the channel survives restarts), UTXO tracking (`forEachUtxo`, `interestingTx` — contract UTXOs
are "claimed" by `contractId` so the wallet doesn't auto-spend them), and the interactive
**spending proposal** round (each cosigner is asked to approve/sign over the same channel). The
`WalletContract` / `WalletContractType` interfaces in `contracts/common.kt` are the extension
points if you build your own multi-party contract type; `formMultisig(cs, requiredSigs, dests)`
is the underlying multisig locking-script builder. The invitation string is the only thing you
distribute — treat it like the `convoSecret` it contains (anyone holding it can join/observe the
formation conversation).

## Common mistakes and anti-patterns

### Broadcasting an unsolved message

**Wrong**:
```kotlin
val msg = CapdMsg(payload)
chain.net.broadcastMsg(msg)            // throws CapdException("Message is not solved")
```
*A CAPD message without a valid PoW is the equivalent of a transaction with no fee — relays reject
it.*

**Right**: `msg.solve()` (and optionally `setPowTargetHarderThanPriority`) before broadcasting, and
`check()` if you want to confirm.

### Treating CAPD as durable storage or a delivery guarantee

**Wrong**: assuming a sent message will still be retrievable later, or that the counterparty
*will* receive it. *CAPD messages expire and are best-effort relayed; there is no consensus,
acknowledgement, or persistence.*

**Right**: design the protocol to tolerate loss — re-send on a timer until you get an
application-level ack over the same channel, keep `expiration` long enough to cover the
counterparty's offline windows, and anchor anything that must be permanent on-chain.

### Expecting to receive on an SPV/electrum-only connection

**Wrong**: building a `CapdProtocolCommunication` over a chain whose connection manager has only an
electrum/SPV link, then waiting forever in `receive()`. *Receiving installs a monitor on a P2P
client (`chain.net.getp2p()`); without a P2P peer there is nothing to monitor.*

**Right**: ensure the `Blockchain`'s connection manager has a P2P connection. (Broadcasting via
`broadcastMsg` is more forgiving than receiving.)

### Sending sensitive data in the clear

**Wrong**: `chain.net.broadcastMsg(CapdMsg(plaintextSecret))`. *Every CAPD message is visible to the
whole network; the conversation id is **not** secret and is explicitly only a bandwidth-reduction
tag.*

**Right**: use `CapdProtocolCommunication` (it AES-256-encrypts the payload under the shared
secret), or encrypt the payload yourself before wrapping it in a raw `CapdMsg`. Confidentiality and
sender-authentication come from the encryption, never from CAPD itself.

### Letting an unsolvable PoW hang the app

**Wrong**: solving a very long-lived / high-priority message synchronously on the UI thread and
blocking when it approaches `CapdSolvableCutoff`.

**Right**: catch `CapdTooDifficult`, and solve off the main thread; for genuinely heavy targets,
shorten the expiration, lower the priority, or delegate the solve.

## Security considerations

- **CAPD provides no confidentiality or authentication by itself.** The conversation id is public
  and only disambiguates traffic. Encrypt payloads (as `CapdProtocolCommunication` does); a valid
  decryption under the shared key is what proves a message came from an authorized participant.
- **The shared `convoSecret` is the channel's only protection.** Anyone who learns it can read and
  forge messages in that conversation. Exchange it over a secure channel and rotate it per
  conversation.
- **PoW is anti-spam, not proof of identity.** A solved message only proves someone spent work, not
  who they are. Don't infer authorization from "it was solved."
- **Messages are public and persistent for their lifetime.** Assume an observer logs the entire
  CAPD stream: the existence, timing, size, and conversation id of your messages leak metadata even
  when the payload is encrypted. Keep expirations as short as the protocol allows, and use the
  rescind mechanism to retract early.
- **Replay within the validity window is possible.** Include nonces/sequence numbers in your
  application payload so a re-broadcast of an old (still-unexpired) message can't be replayed as a
  fresh instruction.

## Related skills and references

- `nexa-wallet-lifecycle-and-chain` — provides the connected `Blockchain` (`blockchainFor`) and its
  connection manager (`chain.net`) that CAPD broadcasts and receives over; ensure it has a P2P peer.
- `nexa-transaction-construction` — CAPD is typically the *negotiation* layer for an eventual
  on-chain tx (the partial-tx offer / half-tx swap idiom there is exactly the kind of multi-party
  flow CAPD coordinates when the parties have no direct connection).
- `nexa-npl-smart-contracts` / `nexa-tokens-and-groups` — multi-party contract and token covenants whose
  signing/coordination rounds are a natural fit for a `CapdProtocolCommunication` channel. For the
  common M-of-N case, prefer the ready-made multisig wallet contract (Pattern 5) over hand-rolling
  the coordination.
- `nexa-electrum-monitoring` — the read side: once a coordinated tx is broadcast, watch the chain for
  it (and for the counterparty's UTXO spends) via electrum.
- The CAPD subprotocol spec: `https://spec.nexa.org/network/capd/` (the authoritative description of
  message fields, PoW, expiration, and relay rules).

### Supporting files in this folder

- `capdConversationTemplate.kt` — a drop-in two-party `CapdProtocolCommunication` harness with
  re-send-until-acked and expiry handling (frames messages with a sender/msg-id, acks, and dedups).
- `messageFormat.md` — the `CapdMsg` wire fields, the PoW formula and priority/size/age decay, the
  expiration/lifetime rules, and the rescind mechanism, distilled from the spec.