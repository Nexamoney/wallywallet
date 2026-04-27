actual object LeakAssertions {
    actual fun assertNoLeaks(tag: String) {
        leakcanary.LeakAssertions.assertNoLeaks(tag)
    }
}
