//
//  MainViewControllerKtTest.swift
//  iosAppTests
//
//  MainViewController() and prewarmFontMgr() in MainViewController.kt.
//

import XCTest
import src

final class MainViewControllerKtTest: XCTestCase {

    func testMainViewControllerReturnsNonNilUIViewController() {
        XCTAssertNotNil(AppKt.wallyApp, "Precondition: OnAppStartup() must have run in the test host")
        let previousBackgroundOnly = AppKt.backgroundOnly
        defer { AppKt.backgroundOnly = previousBackgroundOnly }

        let vc = MainViewControllerKt.MainViewController()

        XCTAssertNotNil(vc, "MainViewController() must return a view controller")
        XCTAssertTrue(vc.isKind(of: UIViewController.self),
                      "MainViewController() must return a UIViewController, got \(type(of: vc))")

        XCTAssertFalse(type(of: vc) == UIViewController.self,
                       "Expected a ComposeUIViewController subclass, got the base UIViewController")
    }

    func testMainViewControllerCalledTwiceReturnsDistinctInstances() {
        let previousBackgroundOnly = AppKt.backgroundOnly
        defer { AppKt.backgroundOnly = previousBackgroundOnly }

        let first = MainViewControllerKt.MainViewController()
        let second = MainViewControllerKt.MainViewController()

        XCTAssertFalse(first === second,
                       "Each MainViewController() call must build a fresh view controller")
    }

    func testMainViewControllerDistinctInstancesOwnDistinctViews() {
        let previousBackgroundOnly = AppKt.backgroundOnly
        defer { AppKt.backgroundOnly = previousBackgroundOnly }

        let first = MainViewControllerKt.MainViewController()
        let second = MainViewControllerKt.MainViewController()
        first.loadViewIfNeeded()
        second.loadViewIfNeeded()

        XCTAssertNotNil(first.view)
        XCTAssertNotNil(second.view)
        XCTAssertFalse(first.view === second.view,
                       "Two factory instances must not share a single root UIView")
    }

    func testMainViewControllerWhenBackgroundOnlyIsTrueClearsIt() {
        let previousBackgroundOnly = AppKt.backgroundOnly
        defer { AppKt.backgroundOnly = previousBackgroundOnly }

        AppKt.backgroundOnly = true
        XCTAssertTrue(AppKt.backgroundOnly, "Failed to arm the precondition")

        _ = MainViewControllerKt.MainViewController()

        XCTAssertFalse(AppKt.backgroundOnly,
                       "MainViewController() must set backgroundOnly = false; the GUI is being built")
    }

    func testMainViewControllerRepeatedCallsKeepBackgroundOnlyFalse() {
        let previousBackgroundOnly = AppKt.backgroundOnly
        defer { AppKt.backgroundOnly = previousBackgroundOnly }

        for iteration in 0 ..< 3 {
            AppKt.backgroundOnly = true
            _ = MainViewControllerKt.MainViewController()
            XCTAssertFalse(AppKt.backgroundOnly,
                           "backgroundOnly was not cleared on call #\(iteration + 1)")
        }
    }

    func testMainViewControllerDoesNotResetBackgroundStop() {
        let previousBackgroundOnly = AppKt.backgroundOnly
        let previousBackgroundStop = AppKt.backgroundStop
        defer {
            AppKt.backgroundOnly = previousBackgroundOnly
            AppKt.backgroundStop = previousBackgroundStop
        }

        AppKt.backgroundStop = true
        _ = MainViewControllerKt.MainViewController()
        XCTAssertTrue(AppKt.backgroundStop,
                      "MainViewController() must not touch backgroundStop (it was true on entry)")

        AppKt.backgroundStop = false
        _ = MainViewControllerKt.MainViewController()
        XCTAssertFalse(AppKt.backgroundStop,
                       "MainViewController() must not touch backgroundStop (it was false on entry)")
    }

    // Never set AppKt.wallyApp = nil here: the Kotlin `!!` aborts the whole test process.
    func testMainViewControllerPreconditionWallyAppIsNonNilInHostedEnvironment() {
        let previousBackgroundOnly = AppKt.backgroundOnly
        defer { AppKt.backgroundOnly = previousBackgroundOnly }

        XCTAssertNotNil(AppKt.wallyApp,
                        "OnAppStartup() runs from iOSApp.init() in the TEST_HOST; wallyApp must be set")
        XCTAssertNotNil(AppKt.wallyApp?.focusedAccount,
                        "MainViewController() reads wallyApp!!.focusedAccount; it must exist")

        let vc = MainViewControllerKt.MainViewController()
        XCTAssertNotNil(vc)
    }

    func testMainViewControllerDefersViewCreationUntilLoadViewIfNeeded() {
        let previousBackgroundOnly = AppKt.backgroundOnly
        defer { AppKt.backgroundOnly = previousBackgroundOnly }

        let vc = MainViewControllerKt.MainViewController()
        XCTAssertFalse(vc.isViewLoaded,
                       "The factory must not force the view to load; view creation is deferred")

        vc.loadViewIfNeeded()

        XCTAssertTrue(vc.isViewLoaded, "loadViewIfNeeded() must load the Compose root view")
        XCTAssertNotNil(vc.view, "A loaded view controller must expose a non-nil view")
    }

    func testMainViewControllerFactoryCallReturnsPromptly() {
        let previousBackgroundOnly = AppKt.backgroundOnly
        defer { AppKt.backgroundOnly = previousBackgroundOnly }

        _ = MainViewControllerKt.MainViewController()

        let elapsed = elapsedSeconds { _ = MainViewControllerKt.MainViewController() }

        XCTAssertLessThan(elapsed, 5.0,
                          "MainViewController() took \(elapsed)s; it must be a cheap constructor")
    }

    func testMainViewControllerViewAdoptsAssignedFrameAfterLayout() {
        let previousBackgroundOnly = AppKt.backgroundOnly
        defer { AppKt.backgroundOnly = previousBackgroundOnly }

        let vc = MainViewControllerKt.MainViewController()
        vc.loadViewIfNeeded()

        let target = CGRect(x: 0, y: 0, width: 320, height: 640)
        vc.view.frame = target
        vc.view.layoutIfNeeded()

        XCTAssertEqual(vc.view.bounds.width, target.width, accuracy: 0.5,
                       "Compose root view did not adopt the assigned width")
        XCTAssertEqual(vc.view.bounds.height, target.height, accuracy: 0.5,
                       "Compose root view did not adopt the assigned height")
        XCTAssertFalse(vc.view.bounds.isEmpty, "Laid-out view must have non-empty bounds")
    }

    func testMainViewControllerHostedInKeyWindowGetsNonZeroBounds() {
        let previousBackgroundOnly = AppKt.backgroundOnly
        defer { AppKt.backgroundOnly = previousBackgroundOnly }

        let vc = MainViewControllerKt.MainViewController()

        withTemporaryKeyWindow(hosting: vc) { window in
            window.layoutIfNeeded()

            pumpMainRunLoop(for: 2.0)

            XCTAssertFalse(window.bounds.isEmpty,
                           "Temporary host window has empty bounds: \(window.bounds)")
            XCTAssertTrue(vc.isViewLoaded, "Hosting as rootViewController must load the view")
            XCTAssertFalse(vc.view.bounds.isEmpty,
                           "Compose root view has empty bounds while hosted: \(vc.view.bounds)")

            XCTAssertTrue(vc.view.window === window,
                          "The root view controller's view must be installed in the host window")
            XCTAssertTrue(vc.view.isDescendant(of: window),
                          "The root view controller's view must be in the host window's hierarchy")
        }

        XCTAssertFalse(AppKt.backgroundOnly,
                       "backgroundOnly must still be false after a full mount cycle")
    }

    func testMainViewControllerFullAppearanceLifecycleDoesNotCrash() {
        let previousBackgroundOnly = AppKt.backgroundOnly
        defer { AppKt.backgroundOnly = previousBackgroundOnly }

        let vc = MainViewControllerKt.MainViewController()
        vc.loadViewIfNeeded()
        vc.view.frame = CGRect(x: 0, y: 0, width: 320, height: 640)
        vc.view.layoutIfNeeded()

        let elapsed = elapsedSeconds {
            vc.viewWillAppear(false)
            vc.viewDidAppear(false)
            pumpMainRunLoop(for: 0.5)
            vc.viewWillDisappear(false)
            vc.viewDidDisappear(false)
        }

        XCTAssertLessThan(elapsed, 30.0,
                          "The appearance lifecycle took \(elapsed)s; it must not block the UI thread")
        XCTAssertTrue(vc.isViewLoaded,
                      "The view must remain loaded after a full appear/disappear cycle")
        XCTAssertNotNil(vc.view, "The view must survive a full appear/disappear cycle")

        vc.viewWillAppear(false)
        vc.viewDidAppear(false)
        XCTAssertTrue(vc.isViewLoaded, "Re-appearing a previously disappeared controller broke it")
        vc.viewWillDisappear(false)
        vc.viewDidDisappear(false)
    }

    func testMainViewControllerRepeatedCreateAndReleaseDoesNotCrash() {
        let previousBackgroundOnly = AppKt.backgroundOnly
        defer { AppKt.backgroundOnly = previousBackgroundOnly }

        let batchSize = 6
        var live: [UIViewController] = []
        let elapsed = elapsedSeconds {
            for _ in 0 ..< batchSize {
                autoreleasepool { live.append(MainViewControllerKt.MainViewController()) }
            }
        }

        XCTAssertEqual(live.count, batchSize, "Not every factory call produced an instance")
        let identities = Set(live.map { ObjectIdentifier($0) })
        XCTAssertEqual(identities.count, batchSize,
                       "Simultaneously live instances must all be distinct objects")
        XCTAssertLessThan(elapsed, 30.0,
                          "Creating \(batchSize) controllers took \(elapsed)s; the factory got expensive")

        autoreleasepool { live.removeAll() }

        let afterChurn = MainViewControllerKt.MainViewController()
        afterChurn.loadViewIfNeeded()
        XCTAssertTrue(afterChurn.isViewLoaded,
                      "The factory stopped working after a create/release churn")
    }

    func testMainViewControllerCreationLeavesGlobalNavStateValid() {
        let previousBackgroundOnly = AppKt.backgroundOnly
        defer { AppKt.backgroundOnly = previousBackgroundOnly }

        guard let before = currentScreenId() else {
            return XCTFail("nav.currentScreen.value was not a ScreenId before the test")
        }

        let vc = MainViewControllerKt.MainViewController()
        vc.loadViewIfNeeded()

        guard let after = currentScreenId() else {
            return XCTFail("nav.currentScreen.value stopped being a ScreenId after MainViewController()")
        }
        XCTAssertTrue(ScreenId.entries.contains { $0 === after },
                      "nav.currentScreen holds a ScreenId that is not one of ScreenId.entries")

        XCTAssertTrue(before === after,
                      "MainViewController() navigated from \(before.name) to \(after.name)")
    }

    func testPrewarmFontMgrIsNonThrowingAndCompletes() {
        var completed = false
        let elapsed = elapsedSeconds {
            MainViewControllerKt.prewarmFontMgr()
            completed = true
        }

        XCTAssertTrue(completed, "prewarmFontMgr() did not return normally")
        XCTAssertLessThan(elapsed, 60.0,
                          "prewarmFontMgr() took \(elapsed)s; it must not stall app launch")
    }

    func testPrewarmFontMgrWarmCallReturnsPromptly() {
        MainViewControllerKt.prewarmFontMgr()

        let warmElapsed = elapsedSeconds { MainViewControllerKt.prewarmFontMgr() }

        XCTAssertLessThan(warmElapsed, 2.0,
                          "A warm prewarmFontMgr() took \(warmElapsed)s; FontMgr.default is no longer cached")
    }

    func testPrewarmFontMgrRepeatedCallsAreIdempotent() {
        MainViewControllerKt.prewarmFontMgr()

        var timings: [TimeInterval] = []
        for _ in 0 ..< 5 {
            timings.append(elapsedSeconds { MainViewControllerKt.prewarmFontMgr() })
        }

        XCTAssertEqual(timings.count, 5, "Not every repeated prewarmFontMgr() call returned")
        for (index, timing) in timings.enumerated() {
            XCTAssertLessThan(timing, 2.0,
                              "Repeat call #\(index + 1) took \(timing)s; prewarming is not idempotent")
        }

        let previousBackgroundOnly = AppKt.backgroundOnly
        defer { AppKt.backgroundOnly = previousBackgroundOnly }
        XCTAssertNotNil(MainViewControllerKt.MainViewController())
    }

    func testPrewarmFontMgrOnBackgroundThreadCompletes() {
        let ranOffMain = runOffMainThread(timeout: 60.0) {
            MainViewControllerKt.prewarmFontMgr()
        }
        XCTAssertTrue(ranOffMain,
                      "prewarmFontMgr() did not complete on a background thread within the timeout")
    }

    func testPrewarmFontMgrConcurrentCallsAllComplete() {
        let callers = 4
        let done = expectation(description: "all concurrent prewarmFontMgr() calls returned")
        done.expectedFulfillmentCount = callers

        let offMainFlags = NSMutableArray()
        let flagLock = NSLock()
        for _ in 0 ..< callers {
            DispatchQueue.global(qos: .userInitiated).async {
                let offMain = !Thread.isMainThread
                MainViewControllerKt.prewarmFontMgr()
                flagLock.lock()
                offMainFlags.add(NSNumber(value: offMain))
                flagLock.unlock()
                done.fulfill()
            }
        }

        let result = XCTWaiter().wait(for: [done], timeout: 60.0)
        XCTAssertTrue(result == .completed,
                      "Only \(offMainFlags.count)/\(callers) concurrent prewarmFontMgr() calls returned")
        XCTAssertEqual(offMainFlags.count, callers)
        for flag in offMainFlags {
            XCTAssertTrue((flag as? NSNumber)?.boolValue == true,
                          "A concurrent caller unexpectedly ran on the main thread")
        }
    }

    func testPrewarmFontMgrBeforeMainViewControllerLeavesFactoryWorking() {
        let previousBackgroundOnly = AppKt.backgroundOnly
        defer { AppKt.backgroundOnly = previousBackgroundOnly }

        let prewarmed = runOffMainThread(timeout: 60.0) {
            MainViewControllerKt.prewarmFontMgr()
        }
        XCTAssertTrue(prewarmed, "Off-main prewarmFontMgr() never finished; cannot test the mount")

        AppKt.backgroundOnly = true
        let vc = MainViewControllerKt.MainViewController()
        vc.loadViewIfNeeded()
        vc.view.frame = CGRect(x: 0, y: 0, width: 320, height: 640)
        vc.view.layoutIfNeeded()

        XCTAssertFalse(AppKt.backgroundOnly, "Mounting after a prewarm must still clear backgroundOnly")
        XCTAssertTrue(vc.isViewLoaded, "Compose view failed to load after an off-main font prewarm")
        XCTAssertFalse(vc.view.bounds.isEmpty,
                       "Compose root view has empty bounds after an off-main font prewarm")
    }

    private static let fallbackWindowFrame = CGRect(x: 0, y: 0, width: 390, height: 844)

    private func elapsedSeconds(_ block: () -> Void) -> TimeInterval {
        let start = DispatchTime.now().uptimeNanoseconds
        block()
        let end = DispatchTime.now().uptimeNanoseconds
        return Double(end &- start) / 1_000_000_000.0
    }

    @discardableResult
    private func runOffMainThread(timeout: TimeInterval,
                                  qos: DispatchQoS.QoSClass = .userInitiated,
                                  file: StaticString = #filePath,
                                  line: UInt = #line,
                                  _ work: @escaping () -> Void) -> Bool {
        let done = expectation(description: "off-main work finished")
        let offMainFlag = NSMutableArray()
        DispatchQueue.global(qos: qos).async {
            offMainFlag.add(NSNumber(value: !Thread.isMainThread))
            work()
            done.fulfill()
        }
        let result = XCTWaiter().wait(for: [done], timeout: timeout)
        guard result == .completed else { return false }
        XCTAssertTrue((offMainFlag.firstObject as? NSNumber)?.boolValue == true,
                      "Work was expected to run off the main thread", file: file, line: line)
        return true
    }

    private func pumpMainRunLoop(for seconds: TimeInterval) {
        XCTAssertTrue(Thread.isMainThread, "pumpMainRunLoop must be called from the main thread")
        RunLoop.current.run(until: Date().addingTimeInterval(seconds))
    }

    private func hostWindowScene() -> UIWindowScene? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
    }

    private func withTemporaryKeyWindow(hosting vc: UIViewController,
                                        _ body: (UIWindow) -> Void) {
        XCTAssertTrue(Thread.isMainThread, "UIWindow work must happen on the main thread")

        let scene = hostWindowScene()
        let previousKeyWindow = scene?.windows.first { $0.isKeyWindow }

        let window: UIWindow
        if let scene = scene {
            window = UIWindow(windowScene: scene)
            if window.bounds.isEmpty {
                let sceneBounds = scene.coordinateSpace.bounds
                window.frame = sceneBounds.isEmpty ? Self.fallbackWindowFrame : sceneBounds
            }
        } else {
            window = UIWindow(frame: Self.fallbackWindowFrame)
        }

        window.rootViewController = vc
        window.makeKeyAndVisible()
        defer {
            window.rootViewController = nil
            window.isHidden = true
            previousKeyWindow?.makeKeyAndVisible()
        }

        body(window)
    }

    private func currentScreenId() -> ScreenId? {
        return NavigationRootKt.nav.currentScreen.value as? ScreenId
    }
}
