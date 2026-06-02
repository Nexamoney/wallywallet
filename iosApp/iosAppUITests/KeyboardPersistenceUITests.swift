//
//  KeyboardPersistenceUITests.swift
//  iosAppUITests
//
//  C14 — Keyboard handling (iOSApp.swift .ignoresSafeArea(.keyboard),
//        MainViewController keyboard observers). Tapping a text field brings up
//        the software keyboard and typing populates the field.
//  C15 — Persistence across relaunch (preferences.ios.kt -> NSUserDefaults).
//        Toggling the "Enable assets screen" setting persists across an app
//        relaunch (observed via the Assets tab in the bottom navigation).
//

import XCTest

final class KeyboardPersistenceUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    override func tearDownWithError() throws {
        app = nil
    }

    /// C14 — Tapping the Send address field shows the keyboard and accepts input.
    func testKeyboardAppearsAndAcceptsInputOnSend() throws {
        XCTAssertTrue(app.launchAndWaitForHome(), "App did not reach Home screen")

        app.buttons["SendButton"].tap()
        let column = app.buttons["SendScreenContentColumn"]
        XCTAssertTrue(column.waitForExistence(timeout: 20), "Send screen never appeared")

        // The destination address field sits at the top of the content column.
        // Tap there to focus it (the fields are merged into the column's
        // accessibility node, so target by coordinate).
        column.coordinate(withNormalizedOffset: CGVector(dx: 0.3, dy: 0.12)).tap()

        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 10),
                      "Software keyboard did not appear when tapping the address field")

        // Typing populates the address field, which reveals the Clear button.
        app.typeText("nexa:test")
        XCTAssertTrue(app.buttons["Clear Address"].waitForExistence(timeout: 10),
                      "Typed text did not register in the address field")
    }

    /// C15 — A settings toggle persists across an app relaunch.
    /// Uses "Enable identity screen", which controls whether the Identity tab
    /// appears in the bottom navigation — a deterministic, persisted signal.
    /// (A Compose Switch's on/off state is not reliably exposed via XCUITest's
    /// `.value`, so we observe the resulting UI change instead. Identity is
    /// chosen over Assets because the Assets tab is auto-re-enabled by
    /// updateNavMenuContents() when the wallet holds assets, which would mask
    /// the persisted preference.)
    func testIdentityScreenSettingPersistsAcrossRelaunch() throws {
        XCTAssertTrue(app.launchAndWaitForHome(), "App did not reach Home screen")

        openSettings()
        let identitySwitch = settingsSwitch("IdentitySwitch")
        XCTAssertTrue(identitySwitch.waitForExistence(timeout: 10), "IdentitySwitch not found")

        // The Settings screen shows the bottom nav, so the Identity tab's
        // visibility is observable here.
        let identityTabInitiallyVisible = app.buttons["IdentityButton"].exists

        identitySwitch.tap()
        let flipped = waitUntil(8) {
            app.buttons["IdentityButton"].exists != identityTabInitiallyVisible
        }
        XCTAssertTrue(flipped,
                      "Toggling 'Enable identity screen' did not update the bottom navigation")
        let newVisibility = app.buttons["IdentityButton"].exists

        // Background the app first so iOS flushes NSUserDefaults to disk before
        // the hard terminate, then relaunch from scratch.
        XCUIDevice.shared.press(.home)
        _ = app.wait(for: .runningBackground, timeout: 10)
        app.terminate()
        XCTAssertTrue(app.launchAndWaitForHome(), "App did not relaunch to Home")

        // The Home bottom nav must reflect the persisted setting.
        XCTAssertEqual(app.buttons["IdentityButton"].exists, newVisibility,
                       "'Enable identity screen' setting did not persist across relaunch")

        // Restore the original setting so the suite leaves no side effects.
        openSettings()
        settingsSwitch("IdentitySwitch").tap()
    }

    // MARK: - Helpers

    private func openSettings() {
        // The Settings gear lives in the top action bar (accessibility label "Settings").
        let settings = app.buttons["Settings"]
        XCTAssertTrue(settings.waitForExistence(timeout: 10), "Settings button not found")
        settings.tap()
        XCTAssertTrue(app.otherElements["SettingsScreenScrollable"].waitForExistence(timeout: 10)
                        || app.scrollViews.firstMatch.waitForExistence(timeout: 10)
                        || app.switches["IdentitySwitch"].waitForExistence(timeout: 10),
                      "Settings screen did not open")
    }

    /// Returns the named settings switch, scrolling it into view if needed.
    private func settingsSwitch(_ identifier: String) -> XCUIElement {
        let element = app.switches[identifier]
        if element.waitForExistence(timeout: 5) && element.isHittable { return element }
        // Scroll down to reveal switches lower on the page (small screens).
        for _ in 0..<5 {
            if element.exists && element.isHittable { break }
            app.swipeUp()
        }
        return element
    }
}
