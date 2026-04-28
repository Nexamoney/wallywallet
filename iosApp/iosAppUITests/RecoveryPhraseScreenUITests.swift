import XCTest

final class RecoveryPhraseScreenUITests: XCTestCase {

    var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
    }

    override func tearDownWithError() throws {
        app = nil
    }

    /// Verify that the recovery phrase screen can be viewed and dismissed on the account detail screen.
    /// This exercises the SecureWhileVisible UIKit container that wraps the mnemonic display.
    func testViewRecoveryPhraseAndDismiss() throws {
        // Navigate to account details via the gear icon
        let detailsButton = app.buttons["AccountDetailsButton"]
        // Skip if no accounts loaded (known simulator database issue)
        try XCTSkipUnless(detailsButton.waitForExistence(timeout: 15), "No accounts available — simulator database may have failed to open")
        detailsButton.tap()

        // Scroll down to find and tap "View Account Recovery Secret Phrase"
        let viewPhraseButton = app.buttons["View Account Recovery Secret Phrase"]
        XCTAssertTrue(viewPhraseButton.waitForExistence(timeout: 10), "View recovery phrase button should exist")
        viewPhraseButton.tap()

        // Verify the recovery phrase section is displayed
        let phraseHeader = app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "12 word phrase")).firstMatch
        XCTAssertTrue(phraseHeader.waitForExistence(timeout: 10), "Recovery phrase description should be displayed")

        // Verify the warning text is displayed
        let warningText = app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "DO NOT STORE DIGITALLY")).firstMatch
        XCTAssertTrue(warningText.waitForExistence(timeout: 5), "Recovery phrase warning should be displayed")

        // Tap "I wrote it down" to dismiss
        let wroteItDownButton = app.buttons["I wrote it down"]
        XCTAssertTrue(wroteItDownButton.waitForExistence(timeout: 5), "I wrote it down button should exist")
        wroteItDownButton.tap()

        // Verify we're back to the account detail screen
        XCTAssertTrue(viewPhraseButton.waitForExistence(timeout: 10), "Should return to account detail screen with View Recovery Phrase button visible")
    }
}
