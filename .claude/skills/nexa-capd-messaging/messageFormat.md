# CapdMsg wire format, PoW, and rescind

The `CapdMsg` wire fields, the priority/proof-of-work relationship, expiration, and the rescind
mechanism — distilled from the Nexa CAPD specification
(`https://spec.nexa.org/network/capd/`). Read `SKILL.md` for the app-facing model and
`capdConversationTemplate.kt` for the encrypted conversation channel; this file is the on-the-wire
reference behind libnexakotlin's `CapdMsg` (`capd.kt`).

## Wire fields

| Field | Format | Meaning |
| --- | --- | --- |
| `fields` | 1 byte | bitfield: bit 0 set if `expiration` is present, bit 1 set if `rescindHash` is present |
| `createTime` | uint64 | seconds since epoch. Priority declines with age; a message timestamped in the **future** is rejected by relays |
| `difficultyBits` | uint32 | the PoW target in Bitcoin "nBits" form: `0xSSVVVVVV` → `VVVVVV << ((SS-3)*8)` |
| `nonce` | 1–8 bytes | the value ground during PoW |
| `expiration` | uint16 (optional) | seconds **after `createTime`**; `0xFFFF` or omitted = never expire |
| `rescindHash` | 20 bytes (optional) | hash of a secret; publishing the preimage marks the message expired |
| `data` | byte vector | the message payload (arbitrary bytes; the conversation channel puts the convoId + AES-encrypted bytes here) |

## Proof of work

The PoW hash (must be at/under `difficultyBits` target):

```
SHA256(SHA256( nonce ++ SHA256( data ++ createTime ++ rescindHash ++ expiration ++ difficultyBits ) ))
```

`++` is concatenation of bitcoin-style serialized fields; use zeroes for any absent optional field
(rescindHash/expiration). The inner SHA256 collapses the message to 32 bytes so grinding the
`nonce` doesn't re-hash the whole payload; the outer double-SHA256 follows Bitcoin's PoW (and
defeats precomputed intermediate state). **A message must carry valid PoW before any relay forwards
it** — this is the anti-spam mechanism, not authentication.

## Priority (how PoW + size + age combine)

Relays rank/forward messages by a computed priority, not raw PoW. Per the spec:

```
Priority(msgContentLength, ageInSeconds, proofOfWorkTarget):
  # 1. work relative to the minimum difficulty:
  x = min_difficulty / proofOfWorkTarget
  # 2. penalize messages larger than the nominal 100-byte size:
  if (msgContentLength > 100) x = (x / msgContentLength) * 100.0
  # 3. linear age decay — crosses 0 at 10 minutes regardless of initial priority:
  x = x - (x / 600) * age
  return x
```

Consequences for an app:
- **Bigger messages need more PoW** to reach the same priority (the size penalty). Keep payloads
  small; the conversation channel's encryption already adds overhead.
- **Priority hits zero ~10 minutes after `createTime`** no matter how hard the PoW was. CAPD is for
  *rendezvous / short-lived coordination*, not durable storage — re-send if you need persistence
  (the `capdConversationTemplate.kt` re-send-until-acked loop).
- libnexakotlin's `setPowTargetHarderThanPriority(priority)` sizes `difficultyBits` to clear a
  target priority; `solve()` then grinds the nonce. If the required work exceeds
  `CapdSolvableCutoff`, `solve()` throws `CapdTooDifficult` (don't loop forever — `SKILL.md`).

## Expiration and lifetime

- `expiration` is seconds **after** `createTime`; `0xFFFF`/omitted means "never" (but priority
  still decays to zero at ~10 minutes, so "never" is not "durable").
- Expired messages are **not relayed** to peers/clients, but a node does **not immediately remove**
  them from its pool (removing on self-declared expiry would be a DoS vector during low-PoW
  periods).
- Recommendation from the spec: don't relay a message that expires within the next ~5 minutes.

## Rescind

- Set `rescindHash` to `hash(secret)` when you create a message you may want to cancel.
- Later, **publishing the `secret`** (the preimage) instructs participants to mark the message
  expired in their pools (and thus stop relaying it).
- Use it when only one responder is needed and you want to stop further responses once satisfied.
- **Not enforced:** honoring a rescind is a courtesy, not a consensus rule — don't rely on it for
  security, only as an optimization.

## Related

- `SKILL.md` — the app model (encrypted channel, PoW flow, P2P-peer requirement, security).
- `capdConversationTemplate.kt` — the two-party channel that sets PoW, solves, and re-sends.
- Spec: `https://spec.nexa.org/network/capd/`.