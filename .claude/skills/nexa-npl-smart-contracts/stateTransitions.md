# NPL compilation: the init scaffold, state transitions, and `Cannot find state transition`

Everything behind NPL's compile step: which init-scaffold tier a contract needs, how the
`stackScripts.bin` cache works, why the compiler throws `Cannot find state transition`, and the
two ways to fix it. Read `SKILL.md` Pattern 2 first for the compile workflow itself (compile in a
test, print the bytecode, paste it as a constant); this file is the "why / when it breaks"
companion. Grounded in NPL's `nslxlat.kt` / `dynstackx.kt`; `dslReference.md` §10 is the terse API
catalogue for the same machinery.

## The compile init scaffold — which tier you need

The maximal init scaffold (the four calls shown in `SKILL.md` Pattern 2 —
`Initialize()` then `loadCalcStackX()` + `addSpecificTransitions(stackX)` +
`addWarriorContractTransitions(stackX)` + `initRefactor()`) is correct for delegation-style
("warrior"-style) contracts. The exact tier you need depends on which transition patterns your DSL
relies on:

| Init scaffold (after `Initialize()`) | Use for |
| --- | --- |
| `loadCalcStackX()` + `initRefactor()` | simple packed-data contracts |
| + `addSpecificTransitions(stackX)` | standard multi-rule contracts |
| + `addSpecificTransitions(stackX)` + `addWarriorContractTransitions(stackX)` | delegation / multi-party ("warrior"-style) contracts |

These three tiers mirror the init scaffolds in NPL's own test suite, so they're a safe
guide. **Rule of thumb:** include `addWarriorContractTransitions(stackX)` for delegation /
multi-party contracts; you can omit it for simple single-rule reveal/refund contracts.

NPL also ships a one-call convenience, `initNpl()`, which is exactly
`loadCalcStackX()` + `initRefactor()` (the minimal tier). Note it does **not** call
`addSpecificTransitions` / `addWarriorContractTransitions`, so on a cold cache (no
`stackScripts.bin`) a delegation contract compiled after only `initNpl()` can still hit
`Cannot find state transition` — add the explicit registrations for the tier you need, as
above. You must still call `org.nexa.scriptmachine.Initialize()` first regardless.

Note `loadCalcStackX()` loads precomputed transitions from a `stackScripts.bin` cache when
present and only runs `calcStackX()` (which itself calls `addSpecificTransitions` +
`addWarriorContractTransitions`) on a cache miss — so adding them explicitly after
`loadCalcStackX()` is the robust choice regardless of cache state.

## How NPL compiles a rule, and the `Cannot find state transition` error

You will eventually hit this error, and it is unlike any other compiler error because the
fix is sometimes to **edit NPL itself**. Understanding why is the difference between being
stuck and being unblocked.

NPL does not map your DSL one-to-one onto opcodes. It treats each rule's script as a
sequence of **steps**, keeps an abstract model of the VM **stack** (and altstack) between
steps, and for each step works out the *stack rearrangement* needed to bring the operands
the next opcode consumes into the right positions — then emits opcodes that realize that
rearrangement. Such a rearrangement is a **state transition**: a `begin` stack shape ⇒ an
`end` stack shape, where each position carries a numeric label (same label = same value;
the **end** of the array is the **top** of the stack; the altstack is tracked separately).

To turn a transition into opcodes the compiler, in order:

1. looks it up in `stackX`, a large precomputed table (`StateTransitions`) mapping a
   transition to the shortest opcode sequence that achieves it;
2. if absent, asks the `DynamicStackTransformRegistry` — a list of algorithmic generators
   (`SimpleRoll`, `RotationTransform`, `RecursiveDecompositionTransform`, …) that try to
   synthesize a sequence on the fly, each validated before use;
3. if still unresolved, it **prints the transition and throws**:

```
Missing this state transition (in 1234567 options):
((0,1,2,3,4,5,6,7,8,9,10,11), ())⇒((2,3,4,5,6,7,8,9,10,11,1,0), ())
java.lang.IllegalStateException: Cannot find state transition
```

`stackX` is seeded by `calcStackX()` — a bounded, multi-threaded breadth-first search that
enumerates short opcode sequences over *small* stacks — plus a set of **hand-registered**
transitions. Because the search is bounded (shallow stacks, short programs), a contract that
rearranges *many* stack items at once — lots of visible args, or delegation logic threading
state through a deep stack — can request a transition the search never precomputed and no
dynamic generator recognizes. That is exactly when you see `Cannot find state transition`.

**Resolving it — two manual fixes, both of which you can apply from your own project** (the
transition table `stackX` and the transformer registry are public API, and the library's README
("Handling missing state transitions") says outright that registering from your own project —
once at startup, after the init scaffold and before compiling the contract that needs it — is the
intended path; editing NPL's own `nslxlat.kt`/`dynstackx.kt` is only how you'd upstream a fix
into the library):

1. **Hard-code the transition** (the common fix). Copy the printed `begin⇒end` and register it
   against `stackX` — in your own compile test/scaffold, or (to upstream it) as an entry in
   NPL's `addSpecificTransitions(st)` / `addWarriorContractTransitions(st)`. Each entry is the
   begin/end descriptor plus a **hand-derived** opcode sequence that achieves it:

   ```kotlin
   // In YOUR project, after the init scaffold, before compile():
   // get transition ((0,1,2,3,4,5,6,7,8,9,10,11), ())⇒((2,3,4,5,6,7,8,9,10,11,1,0), ())
   val initial = SD(byteArrayOf(0,1,2,3,4,5,6,7,8,9,10,11), byteArrayOf())
   val target  = SD(byteArrayOf(2,3,4,5,6,7,8,9,10,11,1,0), byteArrayOf())
   val seq = arrayOf(OP.push(11), OP.ROLL, OP.push(11), OP.ROLL, OP.SWAP)
   stackX.add(initial, target, *seq)
   if (DoubleCheckTransitions) stackX.check(initial, target, *seq)  // executes seq in the real VM
   ```

   The `// get transition …⇒…` comment above every existing entry in NPL's two `add*Transitions`
   functions is literally a pasted error printout; the lines under it are its fix — so the
   functions read as a registry of "contract X needed this permutation." `check(...)` (gated by
   the `DoubleCheckTransitions` flag) runs your proposed opcodes through the script VM and asserts
   they really produce `target`, so a wrong hand-derived sequence fails at registration, not
   on-chain. `add` keeps your sequence only if it is shorter than any existing one for that
   transition.

2. **Register a dynamic transformer** (the general fix). Implement the `DynamicStackTransform`
   interface — a single `tryGenerate(xSpec: StateTransition): Clause?` that returns opcodes or
   `null` (return `null` for shapes you don't handle, so the other generators still get a turn) —
   and register an instance:

   ```kotlin
   DynamicStackTransformRegistry.register(object : DynamicStackTransform {
       override val name = "MyTransformer"
       override fun tryGenerate(xSpec: StateTransition): Clause? {
           // inspect xSpec.begin / xSpec.end; return a Clause(opcodes, xSpec) or null
           return null
       }
   })
   ```

   The registry validates every candidate in the real VM and keeps the shortest across all
   generators; a validated generated transition is cached back into `stackX`, so it is computed
   only once. Prefer this when you keep hitting a *family* of related missing transitions rather
   than one specific shape.

**Two operational gotchas this creates:**

- A hand-registered transition is only in the table if its registering code actually ran.
  Your compile test's init scaffold (`SKILL.md` Pattern 2) must call `addSpecificTransitions(stackX)` /
  `addWarriorContractTransitions(stackX)` for the tier your contract needs — or load a
  `stackScripts.bin` cache that already contains them — or you will hit `Cannot find state
  transition` *even for a transition that is hand-coded*. The same applies to your own
  project-side `stackX.add(...)` / `DynamicStackTransformRegistry.register(...)` calls: they must
  run **after** the scaffold (so the cache load doesn't precede them pointlessly) and **before**
  `compile()`. That is why the tier table above matters: the scaffold is what populates `stackX`.
- `loadCalcStackX()` reads the precomputed table from the `stackScripts.bin` cache when
  present and only recomputes (via `calcStackX()`) on a miss. If you edit `calcStackX` /
  `addSpecificTransitions` / `addWarriorContractTransitions` and the cache is stale, **delete
  `stackScripts.bin`** to force a recompute (the cache is large — on the order of a hundred-plus
  MB). A stale cache is the usual reason a newly-added transition still appears missing.

## Designing rules that avoid the error in the first place

The cheapest missing transition is the one you never request. The compiler's own guidance
(NSL is dependency-based; see the mental model in `SKILL.md`) translates into four design rules
that keep the requested stack rearrangements shallow:

- **Cache multi-use extractions into `val`s.** Reading a `PackedStructure` field
  (`oracleMsg.priceAinB`) or an introspection value (`getOutputGroupAmount(i)`) more than once
  re-emits the extraction each time and deepens the stack. Bind once, reuse the binding.
- **Keep rules independent and minimal.** Each rule compiles to its own script; don't try to
  share extracted data across rules, and design timeout/refund rules that don't touch oracle
  data at all (they don't need it, and the extra bindings enlarge that rule's transitions).
- **Declare only the args a rule actually uses.** Unused holder/spender args still occupy
  stack positions and make every rearrangement in that rule wider.
- **Prefer intermediate variables over "clever" data flow.** If a value is needed in two
  places, name it; the dependency model handles duplication far more cheaply than the
  transition search handles a deep hand-shaped permutation.

If a transition still seems impossible to bridge, consider redesigning the logic to avoid the
rearrangement entirely — that is frequently easier than deriving a long opcode sequence.

## Deep stacks, the current generator roster, and compile diagnostics

Three facts about the current machinery worth knowing when you work at this layer:

- **Position labels are `Int`s; stacks deeper than 255 items compile.** In a recent release the
  state-transition framework widened its position labels from `Byte` to `Int`, so very deep
  stacks (e.g. a batch contract touching hundreds of items) can compile. The convenient
  `SD(byteArrayOf(...), byteArrayOf(...))` form still works (bytes are read as *unsigned*
  labels, so it covers labels 0–255); for wider descriptors construct
  `StateDescriptor(IntArray, IntArray)` directly. The `stackScripts.bin` cache remains
  byte-per-label (cached transitions never exceed 256 positions).
- **The dynamic-generator roster has grown.** Beyond the early `SimpleRoll` /
  `RotationTransform` / `RecursiveDecompositionTransform`, the registry now includes a family of
  targeted generators and — added in a recent release — `StructuredDecompositionTransform`,
  which efficiently handles the common "move a bottom segment to the top / rearrange the ends
  while a large contiguous middle segment stays untouched" shape (it supersedes the older
  brute-force `UniversalStackTransform` for these). Practical effect: many transitions that
  previously required hand-coding now resolve dynamically; try a plain compile after a library
  update before reaching for manual fixes.
- **You can measure what the compiler is doing.** NPL ships an instrumentation hook,
  `StackXformDiag` (in `nslc.kt`): an `object` of counters, gated behind `enabled` (zero cost
  when off), that records the planner's **abstract stack high-water** per compile — the deepest
  step, which rule hit it, how many of its items were real bindings vs re-pushable constants,
  and the program-order vs dataflow-weight-sorted pass depths. NPL's published test sources
  include a contract-agnostic diagnostics harness around it (`ScriptDiagnostics.kt`):
  `withCompileStackDiagnostics { …compile… }` returns the captured stats, and its static
  analyzers report per-rule **script size in bytes, opcode count, ROLL/PICK reach, and an
  all-branches stack-depth walk** — the numbers to watch when a contract grows toward the
  relay-policy size limit (see `SKILL.md` Pattern 11 for the enforcer/follower design that
  keeps many-input covenant txs small). For ground-truth *runtime* stack usage, step a real
  spend in the script VM and read the per-instruction stack + `getResources()` — see
  `nexa-script-machine-testing`.

## Related

- `SKILL.md` Pattern 2 — the compile-in-a-test workflow and the bytecode-constant paste pattern
  that this file is the "why / when it breaks" companion to.
- `dslReference.md` §10 — the terse compilation-internals API (`stackX`, `loadCalcStackX()`,
  `StateTransitions.add`/`check`, `DynamicStackTransform`).
- `nexa-script-machine-testing` — once a contract compiles, replay its spend through the real
  script VM to confirm it executes.