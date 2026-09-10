//
//  iosAppUITests.swift
//  iosAppUITests
//
//  Created by Jørgen Svennevik Notland on 14/03/2024.
//  Copyright © 2024 orgName. All rights reserved.
//

import XCTest

final class iosAppUITests: XCTestCase {

    override func setUpWithError() throws {
        // Put setup code here. This method is called before the invocation of each test method in the class.

        // In UI tests it is usually best to stop immediately when a failure occurs.
        continueAfterFailure = false

        // In UI tests it’s important to set the initial state - such as interface orientation - required for your tests before they run. The setUp method is a good place to do this.
    }

    override func tearDownWithError() throws {
        // Put teardown code here. This method is called after the invocation of each test method in the class.
    }

    func testExample() throws {
        // UI tests must launch the application that they test.
        let app = XCUIApplication()
        app.launch()

        // Use XCTAssert and related functions to verify your tests produce the correct results.
    }

    func testLaunchPerformance() throws {
        if #available(macOS 10.15, iOS 13.0, tvOS 13.0, watchOS 7.0, *) {
            // This measures how long it takes to launch your application.
            measure(metrics: [XCTApplicationLaunchMetric()]) {
                XCUIApplication().launch()
            }
        }
    }
}

/// Runtime cover for the time lock vault list on iOS.
///
/// The list is gated behind TimeLockContractRepository.start(), whose one-shot walk raced a
/// concurrent discoverVaultsFromChain() and left the row spinning on "Loading vaults..."
/// indefinitely. The guard that fixes it reads a @Volatile flag across threads, and
/// Kotlin/Native has a different memory model from the JVM, so it must be exercised on a
/// Native target rather than only on JVM.
final class TimeLockVaultUITests: XCTestCase {

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    func testContractListLeavesLoadingState() {
        let app = XCUIApplication()
        XCTAssertTrue(app.launchAndWaitForHome(), "Home never became interactive")

        // Home's third tab. The tab row exposes each icon's contentDescription.
        let contractsTab = app.buttons["Contracts"].firstMatch
        XCTAssertTrue(contractsTab.waitForExistence(timeout: 30), "Contracts tab not found")
        contractsTab.tap()

        // The row itself proves the contract list composed at all.
        let vaultRow = app.staticTexts["Time Lock Vault"].firstMatch
        XCTAssertTrue(vaultRow.waitForExistence(timeout: 60), "Time Lock Vault row never appeared")

        // start() must finish and publish counts. Before the fix this never cleared.
        let loading = app.staticTexts["Loading vaults..."]
        expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: loading, handler: nil)
        waitForExpectations(timeout: 90) { error in
            XCTAssertNil(error, "still showing 'Loading vaults...' after 90s - start() did not complete")
        }
    }
}
