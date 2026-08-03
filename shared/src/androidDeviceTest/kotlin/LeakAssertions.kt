import androidx.test.platform.app.InstrumentationRegistry

actual object LeakAssertions {
    // Heap analysis is slow (~5-10s/test on the emulator), so it only runs when the instrumentation
    // is launched with -e leakDetection true. The build wires this argument from the LEAK_DETECTION
    // env var (see src/build.gradle.kts); CI's androidPixel5TestLeakDetection job sets it, while the
    // plain androidPixel5Test job leaves it off so assertNoLeaks is a no-op there.
    private val leakDetectionEnabled: Boolean by lazy {
        InstrumentationRegistry.getArguments().getString("leakDetection")?.toBoolean() ?: false
    }

    actual fun assertNoLeaks(tag: String) {
        if (leakDetectionEnabled) {
            leakcanary.LeakAssertions.assertNoLeaks(tag)
        }
    }
}
