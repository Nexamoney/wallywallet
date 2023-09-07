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

@main
struct iOSApp: App {
    
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
        MainViewControllerKt.OnAppStartup()
    }
    

	var body: some Scene {
		WindowGroup {
			ContentView()
		}
	}
}
