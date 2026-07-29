/*
 * contractSpendTestHarness.kt — a drop-in JUnit base class for verifying Nexa contract spends
 * through the real script VM (org.nexa:scriptmachine).
 *
 * Copy this into your TEST source set (src/test) and extend it from your contract tests.
 * It is a development/test-time tool — never wire ScriptMachine into your production send path
 * (see SKILL.md "Running the VM inline in production").
 *
 * Requires:  testImplementation(libs.nexa.scriptmachine)
 *            (the native VM ships bundled in the libnexakotlin-jvm jar; Initialize() extracts
 *            and loads it — no separately-installed libnexa.so needed.)
 *
 * Built on SKILL.md Pattern 3/4 (two-tx replay) and Pattern 2 (single-input spend).
 */

import org.nexa.scriptmachine.ScriptMachine
import org.nexa.scriptmachine.Initialize
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.NexaTransaction
import org.nexa.libnexakotlin.NexaTxOutput
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * Extend this from a contract test. It loads the native VM once for the class and exposes
 * assert/replay helpers that report the precise failing opcode + stack on failure.
 *
 *   class MyContractTest : ContractSpendTestHarness() {
 *       @Test fun claimRuleValidates() = assertSpendValidates(fundingHex, claimHex)
 *       @Test fun refundBeforeTimeoutFails() =
 *           assertSpendFails(fundingHex, earlyRefundHex, expecting = "failed")
 *   }
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ContractSpendTestHarness {

    companion object {
        // Initialize() extracts + System.load()s the bundled libnexa. Guard so multiple test
        // classes don't re-run it (libnexakotlin also guards internally; the flag is belt-and-braces).
        private var initialized = false
        @JvmStatic @BeforeAll
        fun loadVmOnce() {
            if (!initialized) { Initialize(); initialized = true }
        }
    }

    /**
     * Replay a real parent→child spend through the VM. Returns null on success, or a failure
     * detail string (the VM error and a full state snapshot) on failure.
     *
     * Success = the run reaches the end with no "failed" error AND a clean main stack.
     */
    protected fun spendError(parentTxHex: String, childTxHex: String): String? {
        val sm = ScriptMachine(parentTxHex, childTxHex)   // auto-detects the spend dependency
        try {
            sm.next(false)                                // stage; ready to step the template
            do { } while (sm.step())                      // step to completion / error
            val err = sm.scriptErr ?: ""
            if (err.contains("failed", ignoreCase = true))
                return "script-verify failed: $err\n${sm.getState()}"
            if (sm.mainStackAt(0) != "")
                return "main stack not clean (leftover truthy value): ${sm.getState().mainstack}"
            return null
        } finally {
            sm.delete()                                   // always release the native handle
        }
    }

    /**
     * Single-input variant: verify input `inputIdx` of `spendTx` against the UTXO it spends.
     * Use when you have the built tx + prevout in hand rather than two serialized txs.
     * `advance = true` pre-runs satisfier+constraint; one next() drives the template.
     */
    protected fun spendError(spendTx: NexaTransaction, inputIdx: Int, utxo: NexaTxOutput): String? {
        val sm = ScriptMachine(spendTx, inputIdx, utxo, /*advance*/ true)
        try {
            sm.next()                                     // run the staged template to completion
            val err = sm.scriptErr ?: ""
            if (err != "No error(0)")
                return "spend failed: $err\n${sm.getState()}"
            if (sm.mainStackAt(0) != "")
                return "main stack not clean: ${sm.getState().mainstack}"
            return null
        } finally {
            sm.delete()
        }
    }

    /** Assert the connected spend validates. Fails the test with the VM's detail if it doesn't. */
    protected fun assertSpendValidates(parentTxHex: String, childTxHex: String) =
        assertNull(spendError(parentTxHex, childTxHex))

    protected fun assertSpendValidates(spendTx: NexaTransaction, inputIdx: Int, utxo: NexaTxOutput) =
        assertNull(spendError(spendTx, inputIdx, utxo))

    /**
     * Assert the spend FAILS, and (optionally) that the failure detail contains `expecting`
     * (e.g. "failed", "OP_EQUALVERIFY", "Locktime", "Stack total length limit exceeded").
     * Guards against a test that passes for the wrong reason (a spend you expected to reject but
     * which actually validated).
     */
    protected fun assertSpendFails(parentTxHex: String, childTxHex: String, expecting: String? = null) {
        val detail = spendError(parentTxHex, childTxHex)
            ?: fail("expected the spend to fail, but it validated")
        if (expecting != null && !detail.contains(expecting, ignoreCase = true))
            fail("spend failed, but not with \"$expecting\":\n$detail")
    }

    /** Print a readable snapshot of a machine for ad-hoc debugging (top-of-stack first). */
    protected fun dumpState(sm: ScriptMachine) {
        val st = sm.getState()
        println("script=${st.scriptType} pos=${st.pos} status=${st.status} bmd=${st.bmd}")
        println("  main(top→bottom): ${st.mainstack.asReversed()}")
        println("  alt (top→bottom): ${st.altstack.asReversed()}")
    }
}