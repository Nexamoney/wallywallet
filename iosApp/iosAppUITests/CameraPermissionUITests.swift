//
//  CameraPermissionUITests.swift
//  iosAppUITests
//
//  A6 — Camera permission flow for QR scanning (QrScannerView.ios.kt,
//       rememberCameraPermissionState). Tapping "Scan QR" requests camera
//       access; denying shows the permission-denied UI.
//  A7 — "Open Settings" affordance (QrScannerView.ios.kt goToSettings ->
//       UIApplicationOpenSettingsURLString). On a device this opens Settings;
//       on the Simulator canOpenURL() is false so it is a no-op — the test
//       therefore asserts the affordance exists and is hittable without crashing.
//

import XCTest

final class CameraPermissionUITests: XCTestCase {

    private var app: XCUIApplication!
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        // Ensure the camera permission prompt appears fresh every run.
        app.resetAuthorizationStatus(for: .camera)
    }

    override func tearDownWithError() throws {
        app = nil
    }

    /// Tap "Scan QR" on Home and dismiss the camera permission alert by denying.
    /// Returns once the permission-denied UI is expected to be visible.
    private func openScannerAndDenyCamera() {
        XCTAssertTrue(app.launchAndWaitForHome(), "App did not reach Home screen")

        app.actionButton(labelContains: "Scan QR").tap()

        // The system camera alert ("...would like to access the Camera") is
        // hosted by SpringBoard. Tap the deny button (curly apostrophe in
        // "Don't Allow", so match by prefix).
        let denyButton = springboard.buttons.matching(
            NSPredicate(format: "label BEGINSWITH 'Don'")).firstMatch
        if denyButton.waitForExistence(timeout: 15) {
            denyButton.tap()
        } else {
            // Fallback: some iOS versions host the alert inside the app process.
            let appDeny = app.buttons.matching(
                NSPredicate(format: "label BEGINSWITH 'Don'")).firstMatch
            if appDeny.waitForExistence(timeout: 5) { appDeny.tap() }
        }
    }

    /// A6 — Denying camera access shows the permission-denied scanner UI.
    func testScanQrCameraPermissionDeniedShowsPrompt() throws {
        openScannerAndDenyCamera()

        let deniedMessage = app.staticTexts["Camera is required for QR Code scanning"]
        XCTAssertTrue(deniedMessage.waitForExistence(timeout: 15),
                      "Permission-denied message did not appear after denying camera access")

        XCTAssertTrue(app.buttons["Open Settings"].exists,
                      "Open Settings button missing from permission-denied UI")
    }

    /// A7 — The "Open Settings" affordance is present and tappable.
    /// On a physical device this launches Settings; on the Simulator
    /// canOpenURL() is false, so we assert it is hittable and does not crash
    /// the app (the scanner UI remains responsive, or Settings comes forward).
    func testOpenSettingsAffordanceFromDeniedPermission() throws {
        openScannerAndDenyCamera()

        let openSettings = app.buttons["Open Settings"]
        XCTAssertTrue(openSettings.waitForExistence(timeout: 15),
                      "Open Settings button did not appear")
        XCTAssertTrue(openSettings.isHittable, "Open Settings button is not hittable")

        openSettings.tap()

        // Either Settings came to the foreground (device) or the app stayed
        // responsive (Simulator no-op). Both are acceptable; a crash is not.
        let settings = XCUIApplication(bundleIdentifier: "com.apple.Preferences")
        let settingsForeground = settings.wait(for: .runningForeground, timeout: 5)
        let appStillAlive = app.buttons["Open Settings"].exists
            || app.homeSentinel.waitForExistence(timeout: 5)

        XCTAssertTrue(settingsForeground || appStillAlive,
                      "App crashed or became unresponsive after tapping Open Settings")
    }
}
