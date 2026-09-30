import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue

/**
 * The vault runs print a funding address and wait for the host funder script to pay it. Without that script
 * (CI, a plain local run) nobody ever pays, so skip rather than wait out the timeout and fail.
 * The funder launches with `VAULT_HARNESS=true`, which the build passes through as `-e vaultHarness true`.
 */
fun assumeVaultHarness() =
    assumeTrue("no vault funding harness (run with VAULT_HARNESS=true)",
        InstrumentationRegistry.getArguments().getString("vaultHarness").toBoolean())
