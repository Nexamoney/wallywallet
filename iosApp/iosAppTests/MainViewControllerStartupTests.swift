//
//  MainViewControllerStartupTests.swift
//  iosAppTests
//
//  OnAppStartup(), the MainViewControllerKt.accounts global and
//  onQrCodeScannedWithDefaultCameraApp(qr:).
//

import XCTest
import src

final class MainViewControllerStartupTests: XCTestCase {

    private final class StartupCallResult {
        private let lock = NSLock()
        private var _error: Error?
        private var _elapsed: TimeInterval = .infinity
        private var _completed = false

        var error: Error? { lock.lock(); defer { lock.unlock() }; return _error }
        var elapsed: TimeInterval { lock.lock(); defer { lock.unlock() }; return _elapsed }
        var completed: Bool { lock.lock(); defer { lock.unlock() }; return _completed }

        func record(error: Error?, elapsed: TimeInterval) {
            lock.lock(); _error = error; _elapsed = elapsed; _completed = true; lock.unlock()
        }
    }

    private final class StartupLatch {
        private let lock = NSLock()
        private var value = false
        var isSet: Bool { lock.lock(); defer { lock.unlock() }; return value }
        func set() { lock.lock(); value = true; lock.unlock() }
    }

    private func startupCallOffMainBounded(_ label: String,
                                           timeout: TimeInterval,
                                           file: StaticString = #filePath,
                                           line: UInt = #line,
                                           _ work: @escaping () throws -> Void) -> StartupCallResult {
        let result = StartupCallResult()
        let done = XCTestExpectation(description: label)
        DispatchQueue.global(qos: .userInitiated).async {
            let started = DispatchTime.now().uptimeNanoseconds
            var thrown: Error?
            do { try work() } catch { thrown = error }
            let ns = DispatchTime.now().uptimeNanoseconds - started
            result.record(error: thrown, elapsed: Double(ns) / 1_000_000_000.0)
            done.fulfill()
        }
        let outcome = XCTWaiter().wait(for: [done], timeout: timeout)
        XCTAssertEqual(outcome, .completed,
                       "\(label) did not return within \(timeout)s -- it must not block its caller",
                       file: file, line: line)
        return result
    }

    private func startupSettle(_ seconds: TimeInterval) {
        let deadline = Date().addingTimeInterval(seconds)
        while Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
        }
    }

    private struct StartupNavSnapshot {
        let screen: ScreenId?
        let subState: Any?
        let data: Any?
        let pathDepth: Int
    }

    private func startupSnapshotNav() -> StartupNavSnapshot {
        let nav = NavigationRootKt.nav
        return StartupNavSnapshot(screen: nav.currentScreen.value as? ScreenId,
                                  subState: nav.currentSubState.value,
                                  data: nav.curData.value,
                                  pathDepth: nav.path.count)
    }

    private func startupRestoreNav(_ snapshot: StartupNavSnapshot) {
        let nav = NavigationRootKt.nav
        var unwound = 0
        while nav.path.count > snapshot.pathDepth && unwound < 64 {
            _ = nav.back()
            unwound += 1
        }
        if let screen = snapshot.screen { nav.currentScreen.setValue(screen) }
        nav.currentSubState.setValue(snapshot.subState)
        nav.curData.setValue(snapshot.data)
    }

    func testWallyAppBeforeAnyTestIsNonNil() throws {
        XCTAssertNotNil(AppKt.wallyApp,
                        "iOSApp.init() -> OnAppStartup() must have installed a CommonApp before the "
                        + "test bundle runs; MainViewController() dereferences wallyApp!! and would crash")
    }

    func testCommonAppAfterHostedStartupShowsOnCreateSideEffects() throws {
        let app = try XCTUnwrap(AppKt.wallyApp, "hosted OnAppStartup() must have run")
        XCTAssertNotNil(app.assetLoaderThread,
                        "CommonApp.onCreate() assigns assetLoaderThread; nil means OnAppStartup() "
                        + "built the CommonApp but never called onCreate()")
        XCTAssertNotNil(app.periodicAnalysisThread,
                        "CommonApp.onCreate() assigns periodicAnalysisThread = uxPeriodicAnalysis()")
    }

    func testCommonAppUnderTestHostReportsRunningTestsFalse() throws {
        let app = try XCTUnwrap(AppKt.wallyApp)
        XCTAssertFalse(app.runningTests,
                       "OnAppStartup() passes CommonApp(false) unconditionally. If this now fails, the "
                       + "MainViewController.kt 'discover if we are running in test mode' TODO was fixed "
                       + "-- update this expectation and check that test state is now isolated")
    }

    func testWallyAppRepeatedReadsReturnSameInstance() throws {
        let first = try XCTUnwrap(AppKt.wallyApp)
        let second = try XCTUnwrap(AppKt.wallyApp)
        XCTAssertTrue(first === second,
                      "AppKt.wallyApp must bridge to a stable object identity across reads")
    }

    // Never call OnAppStartup() again here: it replaces wallyApp and orphans its live thread pools.
    func testOnAppStartupIsInvokedOnlyOnceFromAppInit() throws {
        let app = try XCTUnwrap(AppKt.wallyApp, "hosted startup must have run OnAppStartup()")
        XCTAssertNotNil(app.assetLoaderThread,
                        "onCreate() must have run on the CommonApp that startup installed")

        XCTAssertTrue(AppKt.wallyApp === app,
                      "wallyApp must be a stable singleton for the lifetime of the process")
    }

    func testRunWhenReadyOnHostedAppQueuesOrRunsThunkWithoutBlocking() throws {
        let app = try XCTUnwrap(AppKt.wallyApp)
        let ran = StartupLatch()

        let queueBefore = app.whenReady

        let result = startupCallOffMainBounded("runWhenReady", timeout: 15) {
            app.runWhenReady(thunk: { ran.set() })
        }

        XCTAssertNil(result.error, "runWhenReady must not fail")
        XCTAssertLessThan(result.elapsed, 2.0,
                          "runWhenReady only takes accountLock and appends; it must never block")
        if queueBefore == nil {
            XCTAssertTrue(ran.isSet,
                          "with whenReady == nil the app is ready and runWhenReady must run the thunk inline")
        } else {
            let queued = (app.whenReady?.count ?? 0) > 0
            XCTAssertTrue(ran.isSet || queued,
                          "a thunk handed to runWhenReady before the app is ready must be queued, not dropped")
        }
    }

    func testIosAccountsGlobalWithNoWriterIsEmpty() {
        XCTAssertEqual(MainViewControllerKt.accounts.count, 0,
                       "MainViewControllerKt.accounts has no writer anywhere in shared/ or iosApp/; "
                       + "a non-empty map means new code started using this global")
    }

    func testIosAccountsGlobalComparedToCommonAppAccountsIsUnrelated() throws {
        let app = try XCTUnwrap(AppKt.wallyApp)
        let realCount = app.accounts.count
        let deadCount = MainViewControllerKt.accounts.count

        XCTAssertEqual(deadCount, 0,
                       "the iosMain accounts global is not the account map -- CommonApp.accounts is")
        if realCount > 0 {
            XCTAssertNotEqual(deadCount, realCount,
                              "the iosMain accounts global tracked CommonApp.accounts -- if that is now "
                              + "intentional, one of the two maps should be deleted")
        }
    }

    func testIosAccountsGlobalAfterQrCodeScanRemainsEmpty() throws {
        let before = MainViewControllerKt.accounts.count
        XCTAssertEqual(before, 0, "precondition: the dead global starts empty")

        let result = startupCallOffMainBounded("qr scan (accounts tripwire)", timeout: 20) {
            try MainViewControllerKt.onQrCodeScannedWithDefaultCameraApp(qr: "wally-unit-test-not-a-uri")
        }
        XCTAssertNil(result.error)

        startupSettle(2.0)

        XCTAssertEqual(MainViewControllerKt.accounts.count, 0,
                       "the QR path must not write to the dead iosMain accounts global")
    }

    func testOnQrCodeScannedWithGarbageReturnsImmediately() {
        let warmup = startupCallOffMainBounded("qr warm-up", timeout: 30) {
            try MainViewControllerKt.onQrCodeScannedWithDefaultCameraApp(qr: "wally-unit-test-warmup")
        }
        XCTAssertNil(warmup.error, "warm-up scan must not throw")

        let timed = startupCallOffMainBounded("qr timed", timeout: 30) {
            try MainViewControllerKt.onQrCodeScannedWithDefaultCameraApp(qr: "wally-unit-test-timed")
        }
        XCTAssertNil(timed.error)
        XCTAssertLessThan(timed.elapsed, 1.0,
                          "onQrCodeScannedWithDefaultCameraApp must only log and dispatch a laterJob; "
                          + "it took \(timed.elapsed)s, so work moved onto the caller's thread")
    }

    func testOnQrCodeScannedWithEmptyStringDoesNotThrow() {
        let result = startupCallOffMainBounded("qr empty string", timeout: 20) {
            try MainViewControllerKt.onQrCodeScannedWithDefaultCameraApp(qr: "")
        }
        XCTAssertNil(result.error, "an empty QR payload must be tolerated, not thrown on")
        startupSettle(1.0)
    }

    func testOnQrCodeScannedWithGarbageTextDoesNotThrow() {
        let payloads = [
            "just some scanned prose",
            "WIFI:S:MyNetwork;T:WPA;P:hunter2;;",
            "\u{1F600} unicode \u{00E5}\u{00E6}\u{00F8} and\na newline",
            "   ",
            "://:::???&&&"
        ]
        for payload in payloads {
            let result = startupCallOffMainBounded("qr garbage: \(payload.prefix(16))", timeout: 20) {
                try MainViewControllerKt.onQrCodeScannedWithDefaultCameraApp(qr: payload)
            }
            XCTAssertNil(result.error, "unintelligible QR payload must not throw: \(payload)")
        }
        startupSettle(1.0)
    }

    func testOnQrCodeScannedWithOversizeStringDoesNotThrow() {
        let oversize = String(repeating: "a", count: Int(UtilsKt.MAX_QR_LENGTH_BYTE_MODE) * 4)
        let result = startupCallOffMainBounded("qr oversize", timeout: 30) {
            try MainViewControllerKt.onQrCodeScannedWithDefaultCameraApp(qr: oversize)
        }
        XCTAssertNil(result.error, "an oversize payload must be tolerated, not thrown on")
        XCTAssertLessThan(result.elapsed, 2.0,
                          "payload size must not be processed on the caller's thread")
    }

    func testOnQrCodeScannedWithNexaUriDoesNotThrow() {
        let snapshot = startupSnapshotNav()
        defer { startupRestoreNav(snapshot) }

        let result = startupCallOffMainBounded("qr nexa uri", timeout: 20) {
            try MainViewControllerKt.onQrCodeScannedWithDefaultCameraApp(
                qr: "nexa:nqtsq5g5tf6nsxmpv8vxvjkyvackv6wmha5vdcrgtnqtlnh4")
        }

        XCTAssertNil(result.error, "a well-formed nexa: deep link must not throw")
        XCTAssertLessThan(result.elapsed, 1.0,
                          "the address must be parsed on the laterJob, not on the caller's thread")

        startupSettle(3.0)
        XCTAssertNotNil(NavigationRootKt.nav.currentScreen.value as? ScreenId,
                        "the QR tail must never leave nav.currentScreen holding a non-ScreenId value")
    }

    func testOnQrCodeScannedWithGarbageLeavesNavUnchanged() {
        let before = startupSnapshotNav()

        let result = startupCallOffMainBounded("qr nav purity", timeout: 20) {
            try MainViewControllerKt.onQrCodeScannedWithDefaultCameraApp(qr: "not-a-payment-uri-at-all")
        }
        XCTAssertNil(result.error)

        let immediately = startupSnapshotNav()
        XCTAssertTrue(immediately.screen === before.screen,
                      "onQrCodeScannedWithDefaultCameraApp must not navigate synchronously")
        XCTAssertEqual(immediately.pathDepth, before.pathDepth,
                       "onQrCodeScannedWithDefaultCameraApp must not push onto the nav back stack")

        startupSettle(2.0)
        let afterTail = startupSnapshotNav()
        XCTAssertTrue(afterTail.screen === before.screen,
                      "an unintelligible QR payload must be rejected by handlePaste without navigating")
        XCTAssertEqual(afterTail.pathDepth, before.pathDepth,
                       "a rejected QR payload must leave the nav back stack alone")
    }

    func testOnQrCodeScannedRepeatedCallsDoNotBlockCaller() {
        let burst = 10
        let result = startupCallOffMainBounded("qr burst of \(burst)", timeout: 30) {
            for i in 0..<burst {
                try MainViewControllerKt.onQrCodeScannedWithDefaultCameraApp(qr: "wally-unit-test-burst-\(i)")
            }
        }
        XCTAssertNil(result.error, "no call in the burst may throw")
        XCTAssertLessThan(result.elapsed, 3.0,
                          "\(burst) dispatches took \(result.elapsed)s -- repeated scans are blocking the caller")
        startupSettle(2.0)
        XCTAssertNotNil(AppKt.wallyApp,
                        "a burst of scans must not tear down the app instance the tails are waiting on")
    }
}
