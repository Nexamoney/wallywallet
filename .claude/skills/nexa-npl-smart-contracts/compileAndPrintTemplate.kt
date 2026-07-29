/*
 * compileAndPrintTemplate.kt — a drop-in test scaffold for compiling an NPL contract and printing
 * its bytecode + template hash. Copy this into your TEST source set (src/test), point it at your
 * own Nexa(...) contract, and run it once to obtain:
 *   - the compiled template bytecode (hex) — what you attach to transactions, and
 *   - the template script hash (hash160) — the on-chain identifier for this contract type.
 *
 * Compiling NPL is a development/test-time step (it drives the script VM), exactly like
 * nexaScriptMachineTesting — keep it OUT of your production send path (see SKILL.md Pattern 2 and
 * the "compiling NPL at server startup" anti-pattern). Production code ships the already-compiled
 * bytecode/hash this scaffold printed.
 *
 * Requires (test source set):
 *   testImplementation(libs.nexa.npl)
 *   testImplementation(libs.nexa.scriptmachine)   // Initialize() + the VM the compiler uses
 *   (the native VM ships bundled in the libnexakotlin-jvm jar; Initialize() extracts and loads
 *   it — no separately-installed libnexa.so needed.)
 */

import org.nexa.npl.NPL
import org.nexa.npl.addSpecificTransitions
import org.nexa.npl.addWarriorContractTransitions
import org.nexa.npl.initRefactor
import org.nexa.npl.loadCalcStackX
import org.nexa.npl.stackX
import org.nexa.libnexakotlin.toHex
import kotlin.test.Test
import kotlin.test.BeforeTest

@OptIn(ExperimentalUnsignedTypes::class)
class CompileAndPrintTemplateTest {

    @BeforeTest
    fun setUpCompiler() {
        // One-time compiler scaffold. Order matters:
        org.nexa.scriptmachine.Initialize()       // load the native VM the compiler validates against
        loadCalcStackX()                           // load (or compute) the stack-transition table from cache
        addSpecificTransitions(stackX)             // hand-registered transitions beyond calcStackX()'s search
        addWarriorContractTransitions(stackX)      // additional transitions for delegation/covenant-style contracts
        initRefactor()
        // The two add*Transitions calls are the "+ specific / + warrior" tiers (SKILL.md Pattern 2).
        // If compilation throws `Cannot find state transition`, see SKILL.md "How NPL compiles a
        // rule" — you may need to add the printed (begin)⇒(end) transition here, then delete the
        // stale `stackScripts.bin` cache so loadCalcStackX() recomputes.
    }

    /**
     * Compile one contract and print its bytecode + template hash.
     *
     * @param project        your Nexa("…") { … } project object
     * @param contractName   the contract's name as declared in `contract("…") { … }`
     */
    private fun compileAndPrint(project: NPL, contractName: String) {
        println("Compiling $contractName ...")
        project.compile()                          // compiles every group/contract/interface in the project
        println("$contractName compiled successfully.")

        val contract = project.contract(contractName)
            ?: error("no contract named '$contractName' in this project")
        val iface = contract.interfaces[0]          // the usual single interface
        iface.compile()                             // ensure this interface's template script is built

        val bytecodeHex  = iface.compiled!!.toHex()
        val templateHash = iface.compiled!!.scriptHash160()

        println("$contractName template bytecode: $bytecodeHex")
        println("$contractName template hash (hash160): ${templateHash.toHex()}")
        // Pin these two values in a test assertion once you've verified the contract: the bytecode
        // is what you push in the satisfier / fund into the P2T output, and the template hash is a
        // stable on-chain identifier for "an output of this contract type".
    }

    @Test
    fun printTemplate() {
        // Replace with your own project + contract name, e.g.:
        //   compileAndPrint(secretRevealContract, "SecretRevealContract")
        // (see SKILL.md and the examples/ folder for full contract definitions)
        error("point compileAndPrint(...) at your Nexa(...) project and contract name")
    }
}