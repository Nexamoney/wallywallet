//
//  ClipboardUITests.swift
//  iosAppUITests
//
//  iOS system-integration coverage for the clipboard (UIPasteboard).
//    A1 — Copy: tapping "Copy address" writes the address to UIPasteboard
//               (utils_ios.kt setTextClipboard -> UIPasteboard.generalPasteboard.string)
//               and shows an in-app "copied to clipboard" confirmation.
//    A2 — Paste: the Send "Paste" FAB reads UIPasteboard and populates the
//               destination address (ThumbButton paste -> checkUriAndSetUi).
//
//  NOTE: iOS 16+ shows a blocking system consent dialog ("X would like to paste
//  from Y") whenever a process reads UIPasteboard *string content* written by a
//  different process. So the test runner never reads pasteboard text (it only
//  uses `hasStrings`, which needs no consent), and the paste path is exercised
//  entirely inside the app (the app copies its own address, then pastes it),
//  which is same-process and prompts no dialog.
//

import XCTest
import UIKit

final class ClipboardUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    override func tearDownWithError() throws {
        app = nil
    }

    /// Navigate Home -> Receive and return the displayed receive address.
    private func gotoReceiveAndReadAddress() -> String {
        app.buttons["ReceiveButton"].tap()
        let addressElement = app.buttons["receiveScreen:receiveAddress"]
        XCTAssertTrue(addressElement.waitForExistence(timeout: 20),
                      "Receive address never appeared")
        let address = addressElement.label
        XCTAssertTrue(address.hasPrefix("nexa:"),
                      "Unexpected receive address format: \(address)")
        return address
    }

    /// A1 — Copy the receive address to the system pasteboard.
    func testCopyReceiveAddressToClipboard() throws {
        XCTAssertTrue(app.launchAndWaitForHome(), "App did not reach Home screen")
        _ = gotoReceiveAndReadAddress()

        // Clear the system pasteboard first (writing needs no consent).
        UIPasteboard.general.items = []
        XCTAssertFalse(UIPasteboard.general.hasStrings, "Pasteboard not cleared")

        // Tap "Copy address" (label is duplicated: "Copy address, Copy address").
        app.actionButton(labelContains: "Copy address").tap()

        // The app shows an in-app confirmation ("copied to clipboard"), proving
        // the copy handler ran.
        let notice = app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS[c] %@", "copied")).firstMatch
        XCTAssertTrue(notice.waitForExistence(timeout: 8),
                      "No 'copied to clipboard' confirmation appeared")

        // The system pasteboard now holds a string. `hasStrings` needs no
        // paste-consent, so it does not block the runner.
        XCTAssertTrue(waitUntil(5) { UIPasteboard.general.hasStrings },
                      "System pasteboard has no string after Copy address")
    }

    /// A2 — Copy the address in-app, then paste it into the Send screen.
    /// Same-process clipboard use, so no paste-consent dialog appears.
    func testPasteAddressIntoSend() throws {
        XCTAssertTrue(app.launchAndWaitForHome(), "App did not reach Home screen")

        // 1. App copies its own receive address to the clipboard.
        _ = gotoReceiveAndReadAddress()
        app.actionButton(labelContains: "Copy address").tap()
        XCTAssertTrue(app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS[c] %@", "copied")).firstMatch
            .waitForExistence(timeout: 8), "Copy confirmation did not appear")

        // 2. Back to Home (the Receive screen has no bottom nav, only Back),
        //    then into the Send screen.
        app.buttons["backButton"].tap()
        XCTAssertTrue(app.buttons["SendButton"].waitForExistence(timeout: 15),
                      "Did not return to Home")
        app.buttons["SendButton"].tap()
        XCTAssertTrue(app.buttons["SendScreenContentColumn"].waitForExistence(timeout: 20),
                      "Send screen never appeared")
        XCTAssertFalse(app.buttons["Clear Address"].exists,
                       "Address field unexpectedly populated before paste")

        // 3. Tap the Paste FAB; the app reads its own clipboard write.
        app.actionButton(labelContains: "Paste").tap()

        // Defensive: dismiss a paste-consent dialog if one ever appears.
        let allowPaste = XCUIApplication(bundleIdentifier: "com.apple.springboard")
            .buttons["Allow Paste"]
        if allowPaste.waitForExistence(timeout: 3) { allowPaste.tap() }

        // A populated address field reveals the "Clear Address" button, proving
        // the clipboard content was read and parsed into the destination field.
        XCTAssertTrue(app.buttons["Clear Address"].waitForExistence(timeout: 10),
                      "Paste did not populate the destination address from the clipboard")
    }
}
