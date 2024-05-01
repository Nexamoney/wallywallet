import BackgroundTasks
import SwiftUI
import src

// https://stackoverflow.com/questions/55518898/how-to-pass-bytes-from-swift-ios-to-kotlin-common-module
extension KotlinByteArray {
    static func from(data: Data) -> KotlinByteArray {
        let swiftByteArray = [UInt8](data)
        return swiftByteArray
            .map(Int8.init(bitPattern:))
            .enumerated()
            .reduce(into: KotlinByteArray(size: Int32(swiftByteArray.count))) { result, row in
                result.set(index: Int32(row.offset), value: row.element)
            }
    }
}

/*
func loadLocaleFile() -> Data?
{
    guard let fileURL = Bundle.main.url(forResource: "strings_no", withExtension: "bin") else {
        print("Failed to create URL for file.")
        return nil
    }
    do {
        let data = try Data(contentsOf: fileURL)
        return data
    }
    catch {
        print("Error opening file: \(error)")
        return nil
    }
}
 */

@main
struct iOSApp: App {
    
    private static let backgroundProcessingIdentifier = "info.bitcoinunlimited.www.wally.backgroundProcessing"
    private static let appRefreshIdentifier = "info.bitcoinunlimited.www.wally.appRefresh"

    // Gives us the ability to understand whether our app is in active, inactive or background state.
    @Environment(\.scenePhase) private var scenePhase

    @AppStorage("lastAppRefreshExecution")
    private var lastAppRefreshExecution: TimeInterval = 0

    @AppStorage("lastBackgroundProcessing")
    private var lastBackgroundProcessingExecution: TimeInterval = 0

    init()
    {
    /*
        if #available(iOS 16, *) {
            I18n_iosKt.setLocale(language: Locale.current.language.languageCode!.identifier, country: Locale.current.identifier)
        } else {
            I18n_iosKt.setLocale(language: Locale.current.languageCode!, country: Locale.current.identifier)
        }

        var localeData = loadLocaleFile()
        if (localeData != nil)
        {
            var kba = KotlinByteArray.from(data: localeData!)
            I18n_iosKt.provideLocaleFilesData(data: kba)
        }
        */
        registerBackgroundTask()
        scheduleBGProcessingTask()
        MainViewControllerKt.OnAppStartup()
    }

	var body: some Scene {
		WindowGroup {
			let composeView = ComposeContentView()
                .onOpenURL(perform: { url in
                    print("App was opened via URL: \(url)")
                    MainViewControllerKt.onQrCodeScannedWithDefaultCameraApp(qr: url.absoluteString)
                })
            composeView.ignoresSafeArea(.keyboard, edges: .all)
		}
        .onChange(of: scenePhase, perform: { newValue in
            switch newValue {
            case .active:
                if lastBackgroundProcessingExecution != 0 {
                    print("[backgroundTask] last background processing execution date: \(Date(timeIntervalSince1970: lastBackgroundProcessingExecution))")
                }
                if lastAppRefreshExecution != 0 {
                    print("[backgroundTask] last background app refresh execution date: \(Date(timeIntervalSince1970: lastAppRefreshExecution))")
                }
            case .inactive: break
            case .background:
                scheduleAppRefreshTask()
                
            @unknown default: break
            }
        })
	}

    /*
        This method involves initiating your background work and providing a way to signal
        completion back to the Operation itself, so it can accurately update its state and
        notify the OperationQueue it's part of.

        willChangeValue inform the observed object that the value at key is about to change.
     */
    class BackgroundOperation: Operation {

        private var _isExecuting: Bool = false {
            willSet {
                willChangeValue(forKey: "isExecuting")
            }
            didSet {
                didChangeValue(forKey: "isExecuting")
            }
        }

        private var _isFinished: Bool = false {
            willSet {
                willChangeValue(forKey: "isFinished")
            }
            didSet {
                didChangeValue(forKey: "isFinished")
            }
        }

        override var isAsynchronous: Bool {
            return true
        }

        override var isExecuting: Bool {
            return _isExecuting
        }

        override var isFinished: Bool {
            return _isFinished
        }

        override func start() {
            if isCancelled {
                finish()
                return
            }

            _isExecuting = true
            MainViewControllerKt.iosBackgroundSync(completion: {
                // Call this in your completion handler when the background work is done
                self.finish()
            })
        }

        override func cancel() {
            super.cancel()
            MainViewControllerKt.iosCancelBackgroundSync()
            // Directly marking as finished in case cancel is called before the operation starts executing or finishes
            if _isExecuting {
                finish()
            }
        }

        private func finish() {
            _isExecuting = false
            _isFinished = true
        }
    }

    private func registerBackgroundTask()
    {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.backgroundProcessingIdentifier, using: nil) { task in
            handleBackgroundProcessing(task: task as! BGProcessingTask)
        }

        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.appRefreshIdentifier, using: nil) { task in
            handleBackgroundAppRefresh(task: task as! BGAppRefreshTask)
        }
    }

    private func scheduleAppRefreshTask() {
        print("[BGTaskScheduler] scheduleAppRefreshTask()")
        let request = BGAppRefreshTaskRequest(
            identifier: Self.appRefreshIdentifier
        )

        do { // Submitting a task request for an unexecuted task that’s already in the queue replaces the previous task request.
            try BGTaskScheduler.shared.submit(request)
            print("[BGTaskScheduler] submitted task with id: \(request.identifier)")
        } catch BGTaskScheduler.Error.notPermitted {
            print("Error: [backgroundTask] scheduleAppRefreshTask BGTaskScheduler.shared.submit notPermitted")
        } catch BGTaskScheduler.Error.tooManyPendingTaskRequests {
            print("Error: [backgroundTask] scheduleAppRefreshTask BGTaskScheduler.shared.submit tooManyPendingTaskRequests")
        } catch BGTaskScheduler.Error.unavailable {
            print("Error: [backgroundTask] scheduleAppRefreshTask BGTaskScheduler.shared.submit unavailable")
        } catch {
            print("Error: [backgroundTask] scheduleAppRefreshTask BGTaskScheduler.shared.submit \(error.localizedDescription)")
        }
    }
    
    private func scheduleBGProcessingTask() {
        print("[BGTaskScheduler] scheduleBGProcessingTask()")
        let request = BGProcessingTaskRequest(
            identifier: Self.backgroundProcessingIdentifier
        )

        do { // Submitting a task request for an unexecuted task that’s already in the queue replaces the previous task request.
            try BGTaskScheduler.shared.submit(request)
            print("[BGTaskScheduler] submitted task with id: \(request.identifier)")
        } catch BGTaskScheduler.Error.notPermitted {
            print("Error: [backgroundTask] scheduleBGProcessingTask BGTaskScheduler.shared.submit notPermitted")
        } catch BGTaskScheduler.Error.tooManyPendingTaskRequests {
            print("Error: [backgroundTask] scheduleBGProcessingTask BGTaskScheduler.shared.submit tooManyPendingTaskRequests")
        } catch BGTaskScheduler.Error.unavailable {
            print("Error: [backgroundTask] scheduleBGProcessingTask BGTaskScheduler.shared.submit unavailable")
        } catch {
            print("Error: [backgroundTask] scheduleBGProcessingTask BGTaskScheduler.shared.submit \(error.localizedDescription)")
        }
    }

    func handleBackgroundAppRefresh(task: BGAppRefreshTask)
    {
        print("[backgroundTask] handleBackgroundAppRefresh Task fired")
        // Schedule a new app refresh task.
        scheduleAppRefreshTask()

        let operation = BackgroundOperation()
        let queue = OperationQueue()

        // Provide the background task with an expiration handler that cancels the operation.
        task.expirationHandler = {
            operation.cancel()
        }

        // Inform the system that the background task is complete
        // when the operation completes.
        // ...is executed when the value in the isFinished property changes to true. 
        operation.completionBlock = {
            task.setTaskCompleted(success: !operation.isCancelled)
        }
        queue.maxConcurrentOperationCount = 10
        queue.addOperation(operation)
        
        print("[backgroundTask]", Self.appRefreshIdentifier, "invoked")

        lastAppRefreshExecution = Date().timeIntervalSince1970

    }

    private func handleBackgroundProcessing(task: BGProcessingTask) {
        print("[backgroundTask] handleBackgroundProcessing Task fired")
        // Schedule a new background processing task.
        scheduleBGProcessingTask()

        let operation = BackgroundOperation()
        let queue = OperationQueue()

        // Provide the background task with an expiration handler that cancels the operation.
        task.expirationHandler = {
            operation.cancel()
        }
        
        // Inform the system that the background task is complete
        // when the operation completes.
        // ...is executed when the value in the isFinished property changes to true.
        operation.completionBlock = {
            task.setTaskCompleted(success: !operation.isCancelled)
        }

        queue.maxConcurrentOperationCount = 10
        queue.addOperation(operation)

        print("[backgroundTask]", Self.backgroundProcessingIdentifier, "invoked")

        lastBackgroundProcessingExecution = Date().timeIntervalSince1970
    }
}
