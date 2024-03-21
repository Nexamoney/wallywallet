//
//  iosAppUITestsLaunchTests.swift
//  iosAppUITests
//
//  Created by Jørgen Svennevik Notland on 14/03/2024.
//  Copyright © 2024 orgName. All rights reserved.
//

import XCTest

final class iosAppBackgroundTests: XCTestCase {

    override class var runsForEachTargetApplicationUIConfiguration: Bool {
        true
    }

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testAppBackgroundBehavior() throws {
        let app = XCUIApplication()
        app.launch()

        // Insert steps here to perform after app launch but before taking a screenshot,
        // such as logging into a test account or navigating somewhere in the app

        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Launch Screen"
        attachment.lifetime = .keepAlways
        add(attachment)
        
        // Simulate pressing the Home button to send the app to background
        XCUIDevice.shared.press(.home)
        
        // Wait for a short period (e.g., 60 seconds)
        // Note: XCTest does not support waiting for 24 hours.
        // You should consider what you're trying to achieve with such a wait and if it can be tested in a more practical manner.
        sleep(60)
        
        // Ideally, here you would mock the passage of time or check for the app's ability to resume correctly.
        
        // For demonstration, we're bringing the app back to the foreground.
        app.activate()
        
        // Add assertions here to verify the app's state or behavior upon resuming.
    }
}
