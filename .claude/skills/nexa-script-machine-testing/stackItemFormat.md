# Stack-item text format (`mainStackAt` / `altStackAt` / `getRegister`)

`ScriptMachine.mainStackAt(idx)`, `altStackAt(idx)`, and `getRegister(idx)` all return the
**same human-readable string grammar** for one stack/register slot. You parse this string in test
assertions, so its exact shape matters. This is the rendering the native VM produces
(`getStackItemText` / `getRegisterText`); it is not the raw bytes — use `getBinaryStack(...)` when
you need the actual `ByteArray`s (see the end of this file).

## The grammar

```
<TYPE> <byteLength> <hex>h <decimal>
```

Four space-separated fields:

| Field | Index after `split(" ")` | Meaning |
| --- | --- | --- |
| `TYPE` | `[0]` | `BYTES` or `BIGNUM` |
| `byteLength` | `[1]` | length of the value in bytes (decimal) |
| `hex`+`h` | `[2]` | the value in hex, suffixed with `h`; or the literal `false` for an empty item |
| `decimal` | `[3]` | the value interpreted as a number, in base 10 |

`mainStackAt(0)` is the **top** of the stack; higher indices go deeper. An index past the bottom
of the stack returns the **empty string** `""` — that is how you detect "no more items" (and how
a clean post-template main stack reads: `mainStackAt(0) == ""`).

## Worked examples

| Returned string | What it is |
| --- | --- |
| `BYTES 1 03h 3` | a 1-byte value, `0x03`, decimal 3 |
| `BYTES 1 01h 1` | a 1-byte value, `0x01` (a typical "true") |
| `BYTES 1 05h 5` | a 1-byte value, `0x05` |
| `BYTES 0 false 0` | an **empty** (zero-length) item — the result of `OP_FALSE` / `OP.PUSHFALSE`. Field `[2]` is the literal `false`, not `0h`. |
| `BIGNUM 1 10h 16` | a bignum, 1 byte, `0x10`, decimal 16 |
| `BIGNUM 1 -fh -15` | a **negative** bignum, magnitude `0x0f`, decimal −15 |
| `BIGNUM 12 c0b0a090807060504030201h 3727165692135864801209549313` | a >8-byte bignum (positive) |
| `BIGNUM 12 -c0b0a090807060504030201h -3727165692135864801209549313` | the same magnitude, negative |
| `""` (empty string) | no item at that index (asked past the bottom of the stack) |

Two things to internalize from these:

- The **empty item** is `BYTES 0 false 0`, but an **absent slot** is `""`. They are different.
  `OP.PUSHFALSE` leaves `BYTES 0 false 0` on the stack (one item, zero length); reading off the end
  of the stack returns `""` (no item).
- For a `BIGNUM`, the **sign lives in field `[2]` and `[3]`** as a leading `-`, not in the byte
  count. The magnitude hex is unsigned; the `-` prefix marks negativity.

## Sign-magnitude and the BMD (bignum modulo divisor)

Nexa bignums use a **sign-magnitude** encoding with the sign in the **last byte**: a trailing
`0x80` byte means negative, a trailing `0x00` byte means positive. This is why
`setRegisterToBigNum(idx, "50607080")` renders as `BIGNUM ... -506070h` (the trailing `80` made it
negative) while `setRegisterToBigNum(idx, "5060708000")` renders as `BIGNUM ... 50607080h` (the
trailing `00` made it positive).

Values longer than 8 bytes require setting the **BMD** first. The pattern is
`OP.push(<bmd bytes>), OP.SETBMD, OP.push(<value bytes>), OP.BIN2BIGNUM`: the `SETBMD` establishes
the modulus/width so `BIN2BIGNUM` can interpret the following push as a wide bignum. You can also
set it directly via the `bmd` property (`sm.bmd = "FFFF"` → reads back lowercased as `"ffff"`).
Without a wide-enough BMD, a >8-byte binary push will not convert to the bignum you expect.

(The script-level bignum spec is documented at `https://spec.nexa.org/script/bignum/`; the VM's
sign-byte convention above is what the rendering actually produces, which is the part you assert
on.)

## Parsing in assertions

Because the grammar is stable, the robust way to assert on a value is to `split(" ")` and check the
field you care about, rather than string-matching the whole line:

```kotlin
val item = sm.mainStackAt(0)            // e.g. "BIGNUM 1 -fh -15"
val parts = item.split(" ")
check(parts[0] == "BIGNUM")             // type
check(parts[3] == "-15")                // decimal value  (most assertions want this one)
// parts[2] would be "-fh" (hex magnitude with sign and the trailing 'h')
```

Registers render identically, so the same parsing works for `getRegister(idx)`:

```kotlin
sm.setRegister(0, 1234)
check(sm.getRegister(0).split(" ")[3] == "1234")
sm.setRegister(0, -1234)
check(sm.getRegister(0).split(" ")[3] == "-1234")
sm.setRegister(2, byteArrayOf(4,5,6,7,8,9,0,1,2))
check(sm.getRegister(2).split(" ")[0] == "BYTES")
check(sm.getRegister(2).split(" ")[2] == "040506070809000102h")
```

## When you need the raw bytes instead

The text grammar is for human-readable assertions. When you need the actual bytes (e.g. to compare
against an expected `ByteArray`), read the typed stack:

```kotlin
val stk = sm.getBinaryStack(ScriptMachineStack.MAINSTACK)   // List<Any> — ByteArray / BigInteger entries
check((stk[2] as ByteArray) contentEquals byteArrayOf(1))   // NOTE: this list is bottom-first
```

`getBinaryStack` (and `getState().mainstack`) is ordered **bottom-first** — the *last* element is
the top of the stack — which is the **reverse** of `mainStackAt(idx)` indexing (where `0` is the
top). Don't mix the two conventions in the same assertion.

## Related

- `SKILL.md` Pattern 1 (bare-opcode `eval`) and Pattern 5 (step/inspect) produce these strings.
- `getState()` returns a `MachineState(scriptType, pos, status, bmd, mainstack, altstack)` whose
  `mainstack`/`altstack` are lists of these same strings (bottom-first).