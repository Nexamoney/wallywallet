//
//  ShareSheetUITests.swift
//  iosAppUITests
//
//  A3 — Share sheet. The Home action-bar Share icon invokes
//  utils_ios.kt platformShare() -> UIActivityViewController. This test taps it
//  and asserts the system share sheet is presented.
//

import XCTest

final class ShareSheetUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    override func tearDownWithError() throws {
        app = nil
    }

    /// A3 — Tapping Share presents the system UIActivityViewController.
    func testShareSheetAppearsFromHome() throws {
        XCTAssertTrue(app.launchAndWaitForHome(), "App did not reach Home screen")

        // The Share icon lives in the top action bar (accessibility label "Share").
        let shareButton = app.buttons["Share"]
        XCTAssertTrue(shareButton.waitForExistence(timeout: 10), "Share button not found")
        shareButton.tap()

        // The share sheet hosts standard activities. "Copy" / "Save to Files" are
        // present for text sharing; the container is "ActivityListView". Check
        // across the app and SpringBoard for whichever process hosts the sheet.
        let activityView = app.otherElements["ActivityListView"]
        if activityView.waitForExistence(timeout: 5) {
            XCTAssertTrue(activityView.exists, "Activity sheet container missing")
            return
        }

        let activity = firstSystemElement(labeled: ["Copy", "Save to Files", "More"],
                                          in: app, timeout: 10)
        XCTAssertTrue(activity.exists,
                      "System share sheet did not appear after tapping Share")
    }
}
