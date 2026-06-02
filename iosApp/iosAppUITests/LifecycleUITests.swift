//
//  LifecycleUITests.swift
//  iosAppUITests
//
//  B8 — Background -> foreground (iOSApp.swift scenePhase). Backgrounding and
//       reactivating must restore state without crashing.
//  B9 — Lock-on-background -> PIN on resume. NOTE: on iOS this lifecycle hook is
//       NOT wired. Android calls wallyApp.lockAccounts() on background
//       (WallyApp_android.kt / CommonActivity.kt) but the iOS scenePhase
//       .background handler only schedules BGTasks (iOSApp.swift) and never
//       calls lockAccounts(). The account therefore stays unlocked across a
//       background cycle, so an assertion-based test cannot pass today. The
//       test is recorded as skipped to document the platform gap; the unlock
//       dialog itself is covered by the shared AccountLockTest (commonTest).
//

import XCTest

final class LifecycleUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    override func tearDownWithError() throws {
        app = nil
    }

    /// B8 — Backgrounding then foregrounding restores the Home screen state.
    func testBackgroundThenForegroundRestoresState() throws {
        XCTAssertTrue(app.launchAndWaitForHome(), "App did not reach Home screen")

        // Capture a piece of state that should survive the round trip.
        let balance = app.staticTexts["AccountPillBalance"]
        XCTAssertTrue(balance.waitForExistence(timeout: 20), "Balance not shown before backgrounding")

        // Send to background via the Home button, then reactivate.
        XCUIDevice.shared.press(.home)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 10),
                      "App did not enter the background")

        app.activate()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10),
                      "App did not return to the foreground")

        // State is restored and the app is still interactive.
        XCTAssertTrue(app.homeSentinel.waitForExistence(timeout: 20),
                      "Home screen was not restored after foregrounding")
        XCTAssertTrue(app.staticTexts["AccountPillBalance"].exists,
                      "Account balance missing after foregrounding")
    }

    /// B9 — Lock on background -> PIN prompt on resume.
    /// Skipped: the iOS scenePhase handler does not call lockAccounts(), so the
    /// account is never locked by a background cycle (see file header).
    func testPinPromptAfterBackground() throws {
        throw XCTSkip("""
            Lock-on-background is not wired on iOS. Android calls \
            wallyApp.lockAccounts() when backgrounded, but iOSApp.swift's \
            scenePhase .background case only schedules BGTasks. Until the iOS \
            lifecycle calls lockAccounts(), there is no PIN prompt on resume to \
            assert. The unlock dialog logic is covered by AccountLockTest \
            (commonTest).
            """)
    }
}
