//
//  WallyUITestHelpers.swift
//  iosAppUITests
//
//  Shared helpers for the iOS-specific XCUITest suites.
//
//  Wally's UI is Kotlin Multiplatform Compose hosted in a UIViewController.
//  Compose `Modifier.testTag("X")` is surfaced to XCUITest as the element's
//  accessibility `identifier` (verified at runtime), so most elements are
//  reachable via `app.buttons["TestTag"]` etc. A handful of action-bar / FAB
//  buttons expose a duplicated label (e.g. "Scan QR, Scan QR") and are matched
//  by substring via `actionButton(labelContains:)`.
//

import XCTest

extension XCUIApplication {

    /// Sentinel proving the ~2s splash screen has finished and the Home screen
    /// is interactive. `SendButton` is a stable Compose testTag on the Home
    /// account pill and is language/appearance independent.
    var homeSentinel: XCUIElement { buttons["SendButton"] }

    /// Launch the app and block until the Home screen is interactive.
    /// Returns false if Home never appeared within `timeout`.
    @discardableResult
    func launchAndWaitForHome(timeout: TimeInterval = 60) -> Bool {
        launch()
        return homeSentinel.waitForExistence(timeout: timeout)
    }

    /// Wait for the Home screen without (re)launching.
    @discardableResult
    func waitForHome(timeout: TimeInterval = 60) -> Bool {
        return homeSentinel.waitForExistence(timeout: timeout)
    }

    /// A bottom action-bar / FAB button whose Compose accessibility label is
    /// duplicated (e.g. "Scan QR, Scan QR", "Paste, Paste"). Matched by substring.
    func actionButton(labelContains text: String) -> XCUIElement {
        return buttons.matching(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
    }
}

extension XCTestCase {

    /// Poll a condition until it becomes true or the timeout elapses.
    /// Returns the final value of the condition.
    @discardableResult
    func waitUntil(_ timeout: TimeInterval = 10,
                   pollInterval: TimeInterval = 0.25,
                   _ condition: () -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return true }
            RunLoop.current.run(until: Date().addingTimeInterval(pollInterval))
        }
        return condition()
    }

    /// Returns the first element across the app and SpringBoard whose label
    /// matches one of `labels`, or a non-existent element if none is found.
    /// Useful for system UI (share sheet, permission alerts) that may be hosted
    /// in either process depending on the iOS version.
    func firstSystemElement(labeled labels: [String],
                            in app: XCUIApplication,
                            types: [XCUIElement.ElementType] = [.button, .staticText, .cell],
                            timeout: TimeInterval = 10) -> XCUIElement {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            for source in [app, springboard] {
                for label in labels {
                    let predicate = NSPredicate(format: "label == %@ OR label CONTAINS %@", label, label)
                    for type in types {
                        let element = source.descendants(matching: type).matching(predicate).firstMatch
                        if element.exists { return element }
                    }
                }
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        } while Date() < deadline
        // Return a query that will report .exists == false for clean assertions.
        return app.buttons["__wally_no_such_element__"]
    }
}
