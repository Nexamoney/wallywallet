//
//  AppearanceLocalizationUITests.swift
//  iosAppUITests
//
//  C11 — Localization (i18n_ios.kt). Launching with a Norwegian preferred
//        language loads strings_nb.bin and localizes the UI.
//  C12 — Appearance / dark mode (WallyTheme.ios.kt). Dark theming is currently
//        disabled in the app (SettingsScreen.kt gates it behind `if (false)`),
//        so this is a stability/render check: the app must render its main UI
//        under the system appearance without crashing.
//  C13 — Device rotation (QrScannerView.ios.kt OrientationListener). Rotating
//        the device must not break the layout or crash.
//

import XCTest

final class AppearanceLocalizationUITests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    override func tearDownWithError() throws {
        XCUIDevice.shared.orientation = .portrait
    }

    /// C11 — The UI is localized to Norwegian Bokmål when launched with `nb`.
    func testNorwegianLocalization() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(nb)", "-AppleLocale", "nb_NO"]
        XCTAssertTrue(app.launchAndWaitForHome(), "App did not reach Home screen")

        // "Receive" -> "Motta" in values-nb/strings.xml. The button's testTag
        // identifier ("ReceiveButton") is language independent; its visible
        // label is localized.
        let receive = app.buttons["ReceiveButton"]
        XCTAssertTrue(receive.waitForExistence(timeout: 20), "Receive button not found")
        XCTAssertTrue(receive.label.contains("Motta"),
                      "Receive button was not localized to Norwegian. Label: \(receive.label)")
    }

    /// C12 — The app renders its main Home UI under the current system
    /// appearance (run under dark appearance to exercise the dark path).
    func testRendersUnderSystemAppearance() throws {
        let app = XCUIApplication()
        XCTAssertTrue(app.launchAndWaitForHome(), "App did not reach Home screen")

        // Core Home affordances must be present and hittable.
        XCTAssertTrue(app.buttons["SendButton"].isHittable, "Send button not rendered")
        XCTAssertTrue(app.buttons["ReceiveButton"].exists, "Receive button not rendered")
        XCTAssertTrue(app.staticTexts["AccountPillBalance"].exists, "Balance not rendered")
        XCTAssertTrue(app.buttons["HomeButton"].exists, "Bottom navigation not rendered")

        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = "Home (system appearance)"
        shot.lifetime = .keepAlways
        add(shot)
    }

    /// C13 — Rotating to landscape and back keeps the layout intact and does
    /// not crash. (The app is designed portrait-first; this guards the rotation
    /// path that the QR scanner's OrientationListener also relies on.)
    func testDeviceRotationDoesNotBreakLayout() throws {
        let app = XCUIApplication()
        XCTAssertTrue(app.launchAndWaitForHome(), "App did not reach Home screen")

        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(app.buttons["SendButton"].waitForExistence(timeout: 10),
                      "Home UI missing after rotating to landscape")

        XCUIDevice.shared.orientation = .portrait
        XCTAssertTrue(app.buttons["SendButton"].waitForExistence(timeout: 10),
                      "Home UI missing after rotating back to portrait")
        XCTAssertTrue(app.staticTexts["AccountPillBalance"].exists,
                      "Balance missing after rotation cycle")
    }
}
