//
//  ScreenNavTests.swift
//  iosAppTests
//
//  The global ScreenNav (NavigationRootKt.nav) that MainViewController() drives.
//

import XCTest
import src

final class ScreenNavTests: XCTestCase {

    private final class NavTestPayload: NSObject {
        let tag: String
        init(tag: String) { self.tag = tag }
    }

    private struct NavSnapshot {
        let screen: ScreenId
        let subState: KotlinByteArray?
        let data: Any?
        let depart: ((ScreenNav.Direction) -> Void)?
        let frames: [Any]
    }

    private var navBaseline: NavSnapshot?

    private var nav: ScreenNav { NavigationRootKt.nav }

    override func setUpWithError() throws {
        continueAfterFailure = false

        XCTAssertTrue(Thread.isMainThread,
                      "These tests rely on running on the main thread so that they are atomic "
                      + "with respect to the host app's Compose scene.")

        _ = waitForNav(timeout: 20) { (nav.currentScreen.value as? ScreenId) !== ScreenId.splash }

        try XCTSkipUnless(navLockIsFree(within: 5),
                          "ScreenNav.lock is still held by something else; skipping to avoid a deadlock.")

        navBaseline = snapshotNav()
    }

    override func tearDownWithError() throws {
        defer { navBaseline = nil }
        guard let baseline = navBaseline else { return }
        guard navLockIsFree(within: 5) else {
            XCTFail("ScreenNav.lock was never released; cannot restore global nav state.")
            return
        }
        restoreNav(baseline)
    }

    private func snapshotNav() -> NavSnapshot {
        var frames: [Any] = []
        frames.reserveCapacity(nav.path.count)
        for i in 0 ..< nav.path.count { frames.append(nav.path.object(at: i)) }
        return NavSnapshot(
            screen: (nav.currentScreen.value as? ScreenId) ?? ScreenId.home,
            subState: nav.currentSubState.value as? KotlinByteArray,
            data: nav.curData.value,
            depart: nav.currentScreenDepart,
            frames: frames)
    }

    private func restoreNav(_ s: NavSnapshot) {
        nav.currentScreenDepart = nil
        _ = nav.`switch`(screen: s.screen, screenSubState: s.subState, data: s.data)
        drainNavPath()

        for frame in s.frames { nav.path.insert(frame, at: nav.path.count) }
        nav.currentScreenDepart = s.depart
    }

    private func drainNavPath() {
        var guardCounter = 0
        while nav.path.count > 0 && guardCounter < 10_000 {
            nav.path.removeObject(at: nav.path.count - 1)
            guardCounter += 1
        }
    }

    @discardableResult
    private func navLockIsFree(within seconds: TimeInterval) -> Bool {
        let token = NSObject()
        func attempt() -> Bool {
            return (nav.lock.trylock(block: { token }) as AnyObject?) === token
        }
        let deadline = Date().addingTimeInterval(seconds)
        repeat {
            if attempt() { return true }
            usleep(20_000)
        } while Date() < deadline
        return attempt()
    }

    @discardableResult
    private func waitForNav(timeout: TimeInterval, _ condition: () -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return true }
            RunLoop.current.run(until: Date().addingTimeInterval(0.1))
        }
        return condition()
    }

    private func currentScreenId() -> ScreenId? { nav.currentScreen.value as? ScreenId }
    private func pathDepth() -> Int { nav.path.count }
    private func topFrame() -> ScreenNav.ScreenState? { nav.path.lastObject as? ScreenNav.ScreenState }

    private func kotlinBytes(_ values: [Int8]) -> KotlinByteArray {
        let arr = KotlinByteArray(size: Int32(values.count))
        for (i, v) in values.enumerated() { arr.set(index: Int32(i), value: v) }
        return arr
    }

    private func swiftBytes(_ arr: KotlinByteArray?) -> [Int8]? {
        guard let arr = arr else { return nil }
        return (0 ..< arr.size).map { arr.get(index: $0) }
    }

    private func assertScreen(_ actual: ScreenId?, _ expected: ScreenId, _ message: String = "",
                              file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(actual === expected,
                      "expected ScreenId.\(expected.name), got \(actual.map { "ScreenId.\($0.name)" } ?? "nil"). \(message)",
                      file: file, line: line)
    }

    private func runOffMainBounded(timeout: TimeInterval,
                                   file: StaticString = #filePath, line: UInt = #line,
                                   _ body: @escaping () -> Void) {
        let done = XCTestExpectation(description: "off-main nav work")
        DispatchQueue.global(qos: .userInitiated).async {
            body()
            done.fulfill()
        }
        let result = XCTWaiter().wait(for: [done], timeout: timeout)
        XCTAssertEqual(result, .completed,
                       "background work did not complete within \(timeout)s (waiter result: \(result.rawValue))",
                       file: file, line: line)
    }

    private func placeNavAt(_ screen: ScreenId,
                            subState: KotlinByteArray? = nil,
                            data: Any? = nil,
                            emptyPath: Bool = true) {
        nav.currentScreenDepart = nil
        if emptyPath { drainNavPath() }
        _ = nav.`switch`(screen: screen, screenSubState: subState, data: data)
    }

    func testNavGlobalIsASingleStableInstanceAcrossAccesses() {
        XCTAssertTrue(NavigationRootKt.nav === NavigationRootKt.nav,
                      "NavigationRootKt.nav must expose one process-global ScreenNav instance.")

        placeNavAt(ScreenId.settings)
        NavigationRootKt.nav.reset(newMutable: ScreenId.assets)
        assertScreen(NavigationRootKt.nav.currentScreen.value as? ScreenId, ScreenId.assets,
                     "A second access to NavigationRootKt.nav must observe the first access's mutation.")
    }

    func testNavCurrentScreenIsReadableAsAKnownScreenId() throws {
        let current = try XCTUnwrap(currentScreenId(),
                                   "nav.currentScreen.value must bridge to ScreenId.")
        XCTAssertTrue(ScreenId.entries.contains { $0 === current },
                      "current screen ScreenId.\(current.name) is not a member of ScreenId.entries")
        XCTAssertNotNil(nav.currentSubState, "nav.currentSubState flow must exist")
        XCTAssertNotNil(nav.curData, "nav.curData flow must exist")
    }

    func testNavLockIsReleasedAfterEveryNavigationOperation() {
        placeNavAt(ScreenId.settings)
        XCTAssertTrue(navLockIsFree(within: 0), "lock held after switch()")

        _ = nav.push(screen: ScreenId.assets)
        XCTAssertTrue(navLockIsFree(within: 0), "lock held after push()")

        _ = nav.go(screen: ScreenId.links, screenSubState: nil, data: nil)
        XCTAssertTrue(navLockIsFree(within: 0), "lock held after go()")

        _ = nav.hasBack()
        XCTAssertTrue(navLockIsFree(within: 0), "lock held after hasBack()")

        _ = nav.title()
        XCTAssertTrue(navLockIsFree(within: 0), "lock held after title()")

        _ = nav.back()
        XCTAssertTrue(navLockIsFree(within: 0), "lock held after back()")

        _ = nav.back()
        XCTAssertTrue(navLockIsFree(within: 0), "lock held after back() that fell through to up()")
    }

    func testPushAppendsFrameWithoutChangingCurrentScreen() {
        let sub = kotlinBytes([4, 2])
        let payload = NavTestPayload(tag: "push-keeps-state")
        placeNavAt(ScreenId.settings, subState: sub, data: payload)

        let depthBefore = pathDepth()
        let returned = nav.push(screen: ScreenId.txhistory)

        XCTAssertTrue(returned, "push() forwards ArrayDeque.add's return value, which is always true")
        XCTAssertEqual(pathDepth(), depthBefore + 1, "push() must append exactly one frame")
        assertScreen(currentScreenId(), ScreenId.settings, "push() must not move the current screen")
        XCTAssertEqual(swiftBytes(nav.currentSubState.value as? KotlinByteArray), [4, 2],
                       "push() must not disturb currentSubState")
        XCTAssertTrue((nav.curData.value as AnyObject?) === payload,
                      "push() must not disturb curData")
    }

    func testPushReturnsTrueUnconditionallyForEveryScreenIdIncludingDuplicates() {
        placeNavAt(ScreenId.home)
        let depthBefore = pathDepth()

        var results: [Bool] = []
        for screen in ScreenId.entries { results.append(nav.push(screen: screen)) }

        for _ in 0 ..< 3 { results.append(nav.push(screen: ScreenId.home)) }

        XCTAssertFalse(results.contains(false),
                       "push() returned false for at least one screen; it is documented here as always true")
        XCTAssertEqual(pathDepth(), depthBefore + ScreenId.entries.count + 3,
                       "push() must append one frame per call, duplicates included (ArrayDeque grows past its initial capacity of 10)")
    }

    func testPushStoresArgumentScreenWithEmptyFrameFields() throws {
        let sub = kotlinBytes([9])
        placeNavAt(ScreenId.settings, subState: sub, data: NavTestPayload(tag: "not-captured"))
        nav.onDepart { _ in }

        _ = nav.push(screen: ScreenId.alerts)

        let frame = try XCTUnwrap(topFrame(), "nav.path must expose ScreenNav.ScreenState frames")
        assertScreen(frame.id, ScreenId.alerts, "push() must store the screen it was given")
        XCTAssertNil(frame.departFn, "push() creates ScreenState(screen, null)")
        XCTAssertNil(frame.screenSubState, "push() must not capture the current sub-state")
        XCTAssertNil(frame.data, "push() must not capture the current data payload")
    }

    func testGoPushesOutgoingStateAndActivatesTarget() throws {
        let outgoingSub = kotlinBytes([1, 2, 3])
        let outgoingData = NavTestPayload(tag: "outgoing")
        placeNavAt(ScreenId.settings, subState: outgoingSub, data: outgoingData)
        nav.onDepart { _ in }

        let depthBefore = pathDepth()
        let incomingSub = kotlinBytes([7, 7])
        let incomingData = NavTestPayload(tag: "incoming")

        _ = nav.go(screen: ScreenId.assets, screenSubState: incomingSub, data: incomingData)

        XCTAssertEqual(pathDepth(), depthBefore + 1, "go() must push exactly one frame")
        let frame = try XCTUnwrap(topFrame())
        assertScreen(frame.id, ScreenId.settings, "the pushed frame must hold the screen we left")
        XCTAssertEqual(swiftBytes(frame.screenSubState), [1, 2, 3],
                       "the pushed frame must capture the outgoing sub-state")
        XCTAssertTrue((frame.data as AnyObject?) === outgoingData,
                      "the pushed frame must capture the outgoing data payload")
        XCTAssertNotNil(frame.departFn, "the pushed frame must capture the outgoing depart handler")

        assertScreen(currentScreenId(), ScreenId.assets)
        XCTAssertEqual(swiftBytes(nav.currentSubState.value as? KotlinByteArray), [7, 7])
        XCTAssertTrue((nav.curData.value as AnyObject?) === incomingData)
    }

    func testGoReturnsSameNavInstanceAndChains() {
        placeNavAt(ScreenId.home)
        let depthBefore = pathDepth()

        let returned = nav.go(screen: ScreenId.settings, screenSubState: nil, data: nil)
        XCTAssertTrue(returned === nav, "go() must return the receiver for chaining")

        let chained = returned
            .go(screen: ScreenId.assets, screenSubState: nil, data: nil)
            .go(screen: ScreenId.links, screenSubState: nil, data: nil)
        XCTAssertTrue(chained === nav, "chained go() must keep returning the same ScreenNav")
        XCTAssertEqual(pathDepth(), depthBefore + 3, "each chained go() must push one frame")
        assertScreen(currentScreenId(), ScreenId.links)
    }

    func testGoToCurrentScreenStillPushesAFrame() throws {
        placeNavAt(ScreenId.assets, subState: kotlinBytes([0]))
        let depthBefore = pathDepth()

        _ = nav.go(screen: ScreenId.assets, screenSubState: kotlinBytes([1]), data: nil)

        XCTAssertEqual(pathDepth(), depthBefore + 1,
                       "go(currentScreen) must still push, so back() returns to the previous sub-state")
        assertScreen(try XCTUnwrap(topFrame()).id, ScreenId.assets)
        XCTAssertEqual(swiftBytes(nav.currentSubState.value as? KotlinByteArray), [1])
    }

    func testScreenSubStateRoundTripsThroughGoAsKotlinByteArray() {
        placeNavAt(ScreenId.home)
        let payload: [Int8] = [0, 10, -1, 127, -128, 0]

        _ = nav.go(screen: ScreenId.settings, screenSubState: kotlinBytes(payload), data: nil)

        let read = nav.currentSubState.value as? KotlinByteArray
        XCTAssertNotNil(read, "currentSubState must bridge back as KotlinByteArray")
        XCTAssertEqual(read?.size, Int32(payload.count))
        XCTAssertEqual(swiftBytes(read), payload, "sub-state bytes were altered in transit")
    }

    func testDataPayloadRoundTripsByIdentityThroughGoAndSwitch() {
        placeNavAt(ScreenId.home)
        let viaGo = NavTestPayload(tag: "go")
        let viaSwitch = NavTestPayload(tag: "switch")

        _ = nav.go(screen: ScreenId.settings, screenSubState: nil, data: viaGo)
        XCTAssertTrue((nav.curData.value as AnyObject?) === viaGo,
                      "go(data:) must store the identical object")

        _ = nav.`switch`(screen: ScreenId.assets, screenSubState: nil, data: viaSwitch)
        XCTAssertTrue((nav.curData.value as AnyObject?) === viaSwitch,
                      "switch(data:) must store the identical object")

        _ = nav.`switch`(screen: ScreenId.assets, screenSubState: nil, data: nil)
        XCTAssertNil(nav.curData.value, "passing data: nil must clear the payload, not keep the old one")
    }

    func testSwitchMovesWithoutPushingAFrame() {
        placeNavAt(ScreenId.settings, subState: kotlinBytes([5]), data: NavTestPayload(tag: "old"))
        let depthBefore = pathDepth()
        let newData = NavTestPayload(tag: "new")

        let returned = nav.`switch`(screen: ScreenId.links, screenSubState: kotlinBytes([6]), data: newData)

        XCTAssertTrue(returned === nav, "switch() must return the receiver for chaining")
        XCTAssertEqual(pathDepth(), depthBefore, "switch() must leave the back stack untouched")
        assertScreen(currentScreenId(), ScreenId.links)
        XCTAssertEqual(swiftBytes(nav.currentSubState.value as? KotlinByteArray), [6])
        XCTAssertTrue((nav.curData.value as AnyObject?) === newData)
    }

    func testSwitchToEveryScreenIdKeepsPathDepthConstant() {
        placeNavAt(ScreenId.home)
        let depthBefore = pathDepth()

        let accountTitled: Set<String> = ["AccountDetails", "AddressHistory", "TxHistory"]

        for screen in ScreenId.entries where !accountTitled.contains(screen.name) {
            _ = nav.`switch`(screen: screen, screenSubState: nil, data: nil)
            assertScreen(currentScreenId(), screen, "switch() failed for ScreenId.\(screen.name)")
            XCTAssertEqual(pathDepth(), depthBefore,
                           "switch(ScreenId.\(screen.name)) changed the back stack depth")
        }
    }

    func testGoFiresDepartExactlyOnceWithDeeperDirection() {
        placeNavAt(ScreenId.settings)
        var directions: [ScreenNav.Direction] = []
        nav.onDepart { directions.append($0) }

        _ = nav.go(screen: ScreenId.assets, screenSubState: nil, data: nil)

        XCTAssertEqual(directions.count, 1, "go() must invoke the depart handler exactly once")
        XCTAssertTrue(directions.first === ScreenNav.Direction.deeper,
                      "go() must report Direction.DEEPER, got \(directions.first?.name ?? "nil")")
        XCTAssertFalse(ScreenNav.Direction.deeper === ScreenNav.Direction.leaving,
                       "Direction.DEEPER and Direction.LEAVING must be distinct enum entries")
    }

    func testSwitchFiresDepartExactlyOnceWithLeavingDirection() {
        placeNavAt(ScreenId.settings)
        var directions: [ScreenNav.Direction] = []
        nav.onDepart { directions.append($0) }

        _ = nav.`switch`(screen: ScreenId.assets, screenSubState: nil, data: nil)

        XCTAssertEqual(directions.count, 1, "switch() must invoke the depart handler exactly once")
        XCTAssertTrue(directions.first === ScreenNav.Direction.leaving,
                      "switch() must report Direction.LEAVING, got \(directions.first?.name ?? "nil")")
    }

    func testBackFiresDepartExactlyOnceWithLeavingDirection() {
        placeNavAt(ScreenId.settings)
        _ = nav.push(screen: ScreenId.assets)
        var directions: [ScreenNav.Direction] = []
        nav.onDepart { directions.append($0) }

        _ = nav.back()

        XCTAssertEqual(directions.count, 1, "back() must invoke the depart handler exactly once")
        XCTAssertTrue(directions.first === ScreenNav.Direction.leaving,
                      "back() must report Direction.LEAVING, got \(directions.first?.name ?? "nil")")
    }

    func testDepartHandlerIsClearedAfterFiringAndDoesNotFireTwice() {
        placeNavAt(ScreenId.settings)
        var fired = 0
        nav.onDepart { _ in fired += 1 }

        _ = nav.go(screen: ScreenId.assets, screenSubState: nil, data: nil)
        XCTAssertEqual(fired, 1)
        XCTAssertNil(nav.currentScreenDepart,
                     "go() to a non-notifying screen must leave currentScreenDepart nil")

        _ = nav.go(screen: ScreenId.links, screenSubState: nil, data: nil)
        XCTAssertEqual(fired, 1, "the depart handler must not fire a second time")

        _ = nav.`switch`(screen: ScreenId.home, screenSubState: nil, data: nil)
        XCTAssertEqual(fired, 1, "a later switch() must not resurrect the cleared handler")
    }

    func testOnDepartReplacesThePreviousHandler() {
        placeNavAt(ScreenId.settings)
        var first = 0
        var second = 0
        nav.onDepart { _ in first += 1 }
        nav.onDepart { _ in second += 1 }

        _ = nav.`switch`(screen: ScreenId.assets, screenSubState: nil, data: nil)

        XCTAssertEqual(first, 0, "the superseded handler must not run")
        XCTAssertEqual(second, 1, "the most recently registered handler must run exactly once")
    }

    func testGoAndBackInvokeDepartHandlerWithoutHoldingNavLock() {
        placeNavAt(ScreenId.settings)

        var lockFreeDuringGo: Bool?
        nav.onDepart { _ in lockFreeDuringGo = self.navLockIsFree(within: 0) }
        _ = nav.go(screen: ScreenId.assets, screenSubState: nil, data: nil)
        XCTAssertEqual(lockFreeDuringGo, true,
                       "go() must invoke the depart handler outside nav.lock")

        var lockFreeDuringBack: Bool?
        nav.onDepart { _ in lockFreeDuringBack = self.navLockIsFree(within: 0) }
        _ = nav.back()
        XCTAssertEqual(lockFreeDuringBack, true,
                       "back() must invoke the depart handler outside nav.lock")
    }

    func testGoToNotifyingScreenInstallsDepartHandlerAndPlainScreenDoesNot() {
        placeNavAt(ScreenId.home)

        XCTAssertNotNil(ScreenId.sendtoperm.notify, "SendToPerm is a user-decision screen and must notify")
        nav.currentScreenDepart = nil
        _ = nav.go(screen: ScreenId.sendtoperm, screenSubState: nil, data: nil)
        XCTAssertNotNil(nav.currentScreenDepart,
                        "go() to a notifying screen must install the denotify depart handler")

        XCTAssertNil(ScreenId.settings.notify, "Settings must not raise a notification")
        nav.currentScreenDepart = nil
        _ = nav.go(screen: ScreenId.settings, screenSubState: nil, data: nil)
        XCTAssertNil(nav.currentScreenDepart,
                     "go() to a non-notifying screen must leave currentScreenDepart nil")
    }

    func testBackRestoresDepartHandlerFromThePoppedFrame() {
        placeNavAt(ScreenId.settings)
        var fired: [String] = []
        nav.onDepart { _ in fired.append("origin") }

        _ = nav.go(screen: ScreenId.assets, screenSubState: nil, data: nil)
        XCTAssertEqual(fired, ["origin"], "go() fires the origin handler on the way down")
        XCTAssertNil(nav.currentScreenDepart)

        let destination = nav.back()
        assertScreen(destination, ScreenId.settings)
        XCTAssertNotNil(nav.currentScreenDepart,
                        "back() must reinstall the popped frame's departFn")

        _ = nav.`switch`(screen: ScreenId.home, screenSubState: nil, data: nil)
        XCTAssertEqual(fired, ["origin", "origin"],
                       "the reinstalled handler must fire when the restored screen is left again")
    }

    func testBackReturnsToPriorScreenWithItsSubStateAndData() throws {
        let originSub = kotlinBytes([1, 2, 3])
        let originData = NavTestPayload(tag: "origin")
        placeNavAt(ScreenId.settings, subState: originSub, data: originData)

        _ = nav.go(screen: ScreenId.assets,
                   screenSubState: kotlinBytes([9]),
                   data: NavTestPayload(tag: "deeper"))

        let frame = try XCTUnwrap(topFrame())
        XCTAssertEqual(swiftBytes(frame.screenSubState), [1, 2, 3])
        XCTAssertTrue((frame.data as AnyObject?) === originData)

        let destination = nav.back()

        assertScreen(destination, ScreenId.settings, "back() must return the popped frame's id")
        assertScreen(currentScreenId(), ScreenId.settings)
        XCTAssertEqual(swiftBytes(nav.currentSubState.value as? KotlinByteArray), [1, 2, 3],
                       "back() restores the popped frame's sub-state")
        XCTAssertTrue((nav.curData.value as AnyObject?) === originData,
                      "back() restores the popped frame's data")
    }

    func testDeepGoSequenceUnwindsLifoBackToOrigin() {
        placeNavAt(ScreenId.home)
        let depthBefore = pathDepth()

        let trail: [ScreenId] = [ScreenId.settings, ScreenId.assets, ScreenId.links,
                                 ScreenId.newaccount, ScreenId.splitbill, ScreenId.alerts]
        for screen in trail {
            _ = nav.go(screen: screen, screenSubState: nil, data: nil)
        }
        XCTAssertEqual(pathDepth(), depthBefore + trail.count, "each go() must push exactly one frame")
        assertScreen(currentScreenId(), ScreenId.alerts)

        let expectedOnBack: [ScreenId] = [ScreenId.splitbill, ScreenId.newaccount, ScreenId.links,
                                          ScreenId.assets, ScreenId.settings, ScreenId.home]
        for (step, expected) in expectedOnBack.enumerated() {
            let landed = nav.back()
            assertScreen(landed, expected, "LIFO order broken at unwind step \(step)")
            assertScreen(currentScreenId(), expected)
            XCTAssertEqual(pathDepth(), depthBefore + trail.count - (step + 1),
                           "back() must pop exactly one frame at unwind step \(step)")
        }
        XCTAssertEqual(pathDepth(), depthBefore, "the back stack must return to its original depth")
    }

    func testBackOnEmptyPathFallsBackToUp() {
        placeNavAt(ScreenId.newaccount)
        XCTAssertEqual(pathDepth(), 0)

        let destination = nav.back()

        assertScreen(destination, ScreenId.newaccount.up(),
                     "back() with an empty stack must go 'up', not return nil")
        assertScreen(destination, ScreenId.home)
        assertScreen(currentScreenId(), ScreenId.home)
        XCTAssertEqual(pathDepth(), 0, "the empty-stack path must not push anything")
    }

    func testBackOnEmptyPathFromIdentityEditGoesToIdentity() {
        placeNavAt(ScreenId.identityedit)

        let destination = nav.back()

        assertScreen(destination, ScreenId.identity,
                     "IdentityEdit.up() is Identity — the one non-Home parent in the hierarchy")
        assertScreen(currentScreenId(), ScreenId.identity)
    }

    func testBackFromHomeWithEmptyPathStaysOnHome() {
        placeNavAt(ScreenId.home)

        let first = nav.back()
        let second = nav.back()

        assertScreen(first, ScreenId.home)
        assertScreen(second, ScreenId.home, "repeated back() at the root must be idempotent")
        assertScreen(currentScreenId(), ScreenId.home)
        XCTAssertEqual(pathDepth(), 0)
    }

    func testBackSkipsNoneFramesAndKeepsPoppingInOneCall() {
        placeNavAt(ScreenId.settings)
        _ = nav.push(screen: ScreenId.txhistory)
        _ = nav.push(screen: ScreenId.none)
        XCTAssertEqual(pathDepth(), 2)

        let destination = nav.back()

        assertScreen(destination, ScreenId.txhistory,
                     "a ScreenId.None frame must be consumed and back() must continue to the next frame")
        assertScreen(currentScreenId(), ScreenId.txhistory)
        XCTAssertEqual(pathDepth(), 0, "both the None frame and the real frame must be popped")
    }

    func testBackWithOnlyANoneFrameFallsThroughToUp() {
        placeNavAt(ScreenId.settings)
        _ = nav.push(screen: ScreenId.none)

        let destination = nav.back()

        assertScreen(destination, ScreenId.home,
                     "after consuming the None frame the stack is empty, so back() uses Settings.up() == Home")
        assertScreen(currentScreenId(), ScreenId.home)
        XCTAssertEqual(pathDepth(), 0)
    }

    func testHasBackIsPureAcrossRepeatedCalls() {
        let sub = kotlinBytes([3, 1])
        let payload = NavTestPayload(tag: "untouched")
        placeNavAt(ScreenId.settings, subState: sub, data: payload)
        _ = nav.push(screen: ScreenId.txhistory)
        let depthBefore = pathDepth()

        let first = nav.hasBack()
        let second = nav.hasBack()
        let third = nav.hasBack()

        XCTAssertTrue(first === second && second === third,
                      "hasBack() must be deterministic across calls")
        XCTAssertEqual(pathDepth(), depthBefore, "hasBack() must not pop the back stack")
        assertScreen(currentScreenId(), ScreenId.settings, "hasBack() must not navigate")
        XCTAssertEqual(swiftBytes(nav.currentSubState.value as? KotlinByteArray), [3, 1])
        XCTAssertTrue((nav.curData.value as AnyObject?) === payload)
    }

    func testHasBackReportsTopFrameId() {
        placeNavAt(ScreenId.settings)
        _ = nav.push(screen: ScreenId.assets)
        _ = nav.push(screen: ScreenId.txhistory)

        assertScreen(nav.hasBack(), ScreenId.txhistory, "hasBack() must report the most recent frame")
    }

    func testHasBackOnEmptyPathReturnsUpAndNeverNone() {
        placeNavAt(ScreenId.settings)
        XCTAssertEqual(pathDepth(), 0)

        let result = nav.hasBack()

        assertScreen(result, ScreenId.settings.up(), "hasBack() falls back to up(), despite the KDoc")
        assertScreen(result, ScreenId.home)
        XCTAssertFalse(result === ScreenId.none,
                       "hasBack() never returns ScreenId.None on an empty stack — the KDoc is stale")

        placeNavAt(ScreenId.identityedit)
        assertScreen(nav.hasBack(), ScreenId.identity, "empty-stack hasBack() must track up() per screen")
    }

    func testHasBackPredictsBackDestination() {
        placeNavAt(ScreenId.settings)
        _ = nav.go(screen: ScreenId.assets, screenSubState: nil, data: nil)
        _ = nav.go(screen: ScreenId.links, screenSubState: nil, data: nil)

        let predictedFirst = nav.hasBack()
        let actualFirst = nav.back()
        assertScreen(actualFirst, predictedFirst, "hasBack() mispredicted the first back() destination")

        let predictedSecond = nav.hasBack()
        let actualSecond = nav.back()
        assertScreen(actualSecond, predictedSecond, "hasBack() mispredicted the second back() destination")

        let predictedEmpty = nav.hasBack()
        let actualEmpty = nav.back()
        assertScreen(actualEmpty, predictedEmpty, "hasBack() must also predict the empty-stack up() fallback")
    }

    func testNavTitleTracksCurrentScreen() {
        let screens: [ScreenId] = [ScreenId.settings, ScreenId.assets, ScreenId.identity,
                                   ScreenId.alerts, ScreenId.splitbill, ScreenId.newaccount]
        for screen in screens {
            placeNavAt(screen)
            XCTAssertEqual(nav.title(), screen.title(),
                           "nav.title() diverged from ScreenId.\(screen.name).title()")
            XCTAssertFalse(nav.title().isEmpty, "ScreenId.\(screen.name) must have a visible title")
        }

        for screen in [ScreenId.home, ScreenId.splash, ScreenId.none] {
            placeNavAt(screen)
            XCTAssertEqual(nav.title(), "",
                           "ScreenId.\(screen.name) must render no title text in the top bar")
        }
    }

    func testResetChangesOnlyTheCurrentScreen() {
        let sub = kotlinBytes([7])
        let payload = NavTestPayload(tag: "survives-reset")
        placeNavAt(ScreenId.settings, subState: sub, data: payload)
        _ = nav.push(screen: ScreenId.txhistory)
        let depthBefore = pathDepth()
        var departFired = 0
        nav.onDepart { _ in departFired += 1 }

        nav.reset(newMutable: ScreenId.assets)

        assertScreen(currentScreenId(), ScreenId.assets, "reset() must set the current screen")
        XCTAssertEqual(departFired, 0, "reset() is not a transition and must not fire onDepart")
        XCTAssertNotNil(nav.currentScreenDepart, "reset() must not clear the installed depart handler")
        XCTAssertEqual(pathDepth(), depthBefore, "reset() must not touch the back stack")
        XCTAssertEqual(swiftBytes(nav.currentSubState.value as? KotlinByteArray), [7],
                       "reset() must not clear the sub-state")
        XCTAssertTrue((nav.curData.value as AnyObject?) === payload, "reset() must not clear the payload")
        XCTAssertEqual(nav.title(), ScreenId.assets.title(), "title() must follow the reset screen")
    }

    func testScreenIdUpTerminatesAtAFixedPointFromEveryScreen() {
        let maxSteps = 8
        for start in ScreenId.entries {
            var cursor = start
            var steps = 0
            var reachedFixedPoint = false
            while steps < maxSteps {
                let next = cursor.up()
                if next === cursor { reachedFixedPoint = true; break }
                cursor = next
                steps += 1
            }
            XCTAssertTrue(reachedFixedPoint,
                          "up() from ScreenId.\(start.name) did not reach a fixed point within \(maxSteps) steps (cycle?)")
            assertScreen(cursor, ScreenId.home,
                         "every up() chain must terminate at Home; ScreenId.\(start.name) ended at ScreenId.\(cursor.name)")
            XCTAssertLessThanOrEqual(steps, 2,
                                     "the screen hierarchy is at most two levels deep; ScreenId.\(start.name) took \(steps) steps")
        }
    }

    func testScreenIdTitleIsTotalForEveryScreenId() {
        let intentionallyBlank: Set<String> = ["None", "Splash", "Home"]

        runOffMainBounded(timeout: 45) {
            for screen in ScreenId.entries {
                let title = screen.title()
                if intentionallyBlank.contains(screen.name) {
                    XCTAssertEqual(title, "",
                                   "ScreenId.\(screen.name) must render no title text")
                } else {
                    XCTAssertFalse(title.isEmpty,
                                   "ScreenId.\(screen.name).title() is empty — missing localisation entry?")
                    XCTAssertFalse(title.hasPrefix("STR"),
                                   "ScreenId.\(screen.name).title() fell through to the i18n placeholder '\(title)'")
                }
            }
        }
    }

    func testScreenIdEntriesAreDenseUniqueAndOrdinalIndexed() {
        let entries = ScreenId.entries
        XCTAssertGreaterThanOrEqual(entries.count, 24, "ScreenId lost entries")

        var names = Set<String>()
        for (index, screen) in entries.enumerated() {
            XCTAssertEqual(Int(screen.ordinal), index,
                           "ScreenId.\(screen.name) has ordinal \(screen.ordinal) but sits at index \(index)")
            XCTAssertTrue(entries[Int(screen.ordinal)] === screen,
                          "entries[ordinal] must round-trip for ScreenId.\(screen.name)")
            XCTAssertTrue(names.insert(screen.name).inserted,
                          "duplicate ScreenId name '\(screen.name)'")
        }

        assertScreen(entries.first, ScreenId.none, "ScreenId.None must stay the zero-ordinal sentinel")
        XCTAssertTrue(entries.contains { $0 === ScreenId.home }, "ScreenId.Home must exist")
    }

    func testScreenIdNotifyIsSetExactlyForUserDecisionScreens() {
        let expected: Set<String> = ["SpecialTxPerm", "AssetInfoPerm", "SendToPerm", "IdentityOp"]
        for screen in ScreenId.entries {
            if expected.contains(screen.name) {
                XCTAssertNotNil(screen.notify, "ScreenId.\(screen.name) must raise a notification")
            } else {
                XCTAssertNil(screen.notify, "ScreenId.\(screen.name) must NOT raise a notification")
            }
        }
    }

    func testScreenIdHasShareIsOnlyTrueForHome() {
        for screen in ScreenId.entries {
            XCTAssertEqual(screen.hasShare, screen === ScreenId.home,
                           "ScreenId.\(screen.name).hasShare must be \(screen === ScreenId.home)")
        }
    }

    func testScreenIdIsEntirelyScrollableIsOnlyTrueForNewAccount() {
        for screen in ScreenId.entries {
            XCTAssertEqual(screen.isEntirelyScrollable, screen === ScreenId.newaccount,
                           "ScreenId.\(screen.name).isEntirelyScrollable must be \(screen === ScreenId.newaccount)")
        }
        XCTAssertFalse(ScreenId.settings.isEntirelyScrollable,
                       "Settings must stay non-scrollable at the root or its inner list stops scrolling")
    }

    func testConcurrentPushesFromBackgroundQueuesLoseNoFrames() throws {
        let screenBefore = try XCTUnwrap(currentScreenId())
        let depthBefore = pathDepth()

        let workerCount = 4
        let pushesPerWorker = 50
        let finished = XCTestExpectation(description: "concurrent pushes")
        finished.expectedFulfillmentCount = workerCount

        for _ in 0 ..< workerCount {
            DispatchQueue.global(qos: .userInitiated).async {
                for _ in 0 ..< pushesPerWorker {
                    _ = NavigationRootKt.nav.push(screen: ScreenId.home)
                }
                finished.fulfill()
            }
        }

        let result = XCTWaiter().wait(for: [finished], timeout: 30)
        XCTAssertEqual(result, .completed,
                       "concurrent push() workers did not finish within 30s — ScreenNav.lock may be deadlocked")

        XCTAssertEqual(pathDepth(), depthBefore + workerCount * pushesPerWorker,
                       "ScreenNav.lock failed to serialise concurrent push(): frames were lost or double-counted")
        assertScreen(currentScreenId(), screenBefore,
                     "push() must never move the current screen, even under contention")
        XCTAssertTrue(navLockIsFree(within: 2),
                      "ScreenNav.lock must be free once all concurrent writers have finished")
    }
}
