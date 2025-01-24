
[![Pipeline Status](https://gitlab.com/wallywallet/wallet/badges/main/pipeline.svg)](https://gitlab.com/wallywallet/wallet/pipelines/latest)
![Coverage Status](https://gitlab.com/wallywallet/wallet/badges/main/coverage.svg)



# Wally Personal Wallet

This is a non-custodial SPV & Electrum protocol wallet for Nexa and Bitcoin Cash.

<a href='https://play.google.com/store/apps/details?id=info.bitcoinunlimited.www.wally&hl=en&gl=US&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' style="width: 250px;" width="250" /></a>

<a href="https://www.producthunt.com/posts/wally-nexa-wallet?utm_source=badge-featured&utm_medium=badge&utm_souce=badge-wally&#0045;nexa&#0045;wallet" target="_blank"><img src="https://api.producthunt.com/widgets/embed-image/v1/featured.svg?post_id=452242&theme=light" alt="Wally&#0032;Nexa&#0032;Wallet - Open&#0032;source&#0032;light&#0032;Wallet&#0032;for&#0032;Nexa&#0046;&#0032;Support&#0032;coins&#0032;and&#0032;NFTs | Product Hunt" style="width: 250px; height: 54px;" width="250" height="54" /></a>

[TestFlight BETA for iPhone](https://testflight.apple.com/join/AznUHg38)

The TestFlight BETA is used for testing new features and bugfixes and usually has a never build than App Store

<a href="https://apps.apple.com/us/app/wally-nexa-wallet/id6469619075?itsct=apps_box_badge&amp;itscg=30200" style="display: inline-block; overflow: hidden; border-radius: 13px; width: 250px; height: 83px;"><img src="https://tools.applemediaservices.com/api/badges/download-on-the-app-store/black/en-us?size=250x83&amp;releaseDate=1715212800" alt="Download on the App Store" style="border-radius: 13px; width: 250px; height: 83px;"></a>

## Cloning

Use `git clone https://gitlab.com/wallywallet/android.git` or `git clone git@gitlab.com:wallywallet/android.git` to clone this repository.


## Setup development environment

### Kotlin/Native compiler

> The Kotlin/Native compiler is available for macOS, Linux, and Windows. It is available as a command line tool

https://kotlinlang.org/docs/native-command-line-compiler.html

### Android Studio

* Download and install Android Studio

https://developer.android.com/studio

* Install the Android NDK and CMake

At the welcome screen, click "Configure" (bottom right) and choose "SDK Manager".  Next, Select "Android SDK" on the left and the "SDK Tools" tab.  Change your SDK location (if desired), check "NDK" and "CMake" and then click "Apply" or "OK" to make it happen.

* Enable phone emulation on your desktop

Make sure that CPU virtualization is enabled in your BIOS (you'll get an error when you try to start a phone if it is not).

* If using Ubuntu Linux (or other Debian distribution): Add yourself to the /dev/kvm group, and logout or restart.

```
sudo adduser $USER kvm
```
Note the above **should** work but did not.  Another option on a single-user machine is to have your user own /dev/kvm:
```
sudo chown $USER /dev/kvm
```

#### Kotlin Multiplatform Android Studio plugin

Install the kotlin multiplatform mobile android studio plugin

https://plugins.jetbrains.com/plugin/14936-kotlin-multiplatform-mobile

### Kdoctor: MacOs Environment analysis tool

```bash
brew install kdoctor
kdoctor
```

> KDoctor ensures that all required components are properly installed and ready for use. If something is missed or not configured, KDoctor highlights the problem and suggests how to fix the problem.


### Generate required files
**Generate version file**
```
./gradlew generateVersionFile
```

**Generate strings**

1. Add a `<string>` xml tag to:

`~/dev/wally/i18n/res/values/strings.xml`

And other locales if possible.

Generating strings requires the [Kotlin Native compiler](https://kotlinlang.org/docs/native-command-line-compiler.html) to be installed

2. Enter the i18n directory and run `./run.sh` to re-generate the String files with the new string included.

```
cd i18n
./run.sh
```

### Execution

In Android Studio, create a "JAR Application" in edit run configurations.  Just put the application (fat) jar file into the "Path To Jar" field.
Then go down to "Before Launch" and add a gradle task with this project "wpw" and task name "appJar".  The Jar file is located at:

```bash
YOUR_PROJECT_PATH/build/libs/wpw-app.jar
```

## Building

```bash
./gradlew assemble
```
### Building JVM

```bash
./gradlew appJar
```

### Test



#### Trigger iOS background processing task from Xcode
A physical device is required. Background processing is not supported in emulator.

1. Start Xcode and have an iphone connected
2. Add a breakpoint after the task is scheduled. In line one of `func scheduleBGProcessingTask` in swift
3. Start the app using the play button in Xcode
4. A text input field will appear at the bottom of Xcode
5. Run one of the following command to trigger `func handleBackgroundProcessing()`:

```
e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"info.bitcoinunlimited.www.wally.backgroundProcessing"]
```

Alternatively, fire `func handleBackgroundAppRefresh`:

```
e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"info.bitcoinunlimited.www.wally.appRefresh"]
```

Read more in the [official Apple documentation](https://developer.apple.com/documentation/backgroundtasks/starting-and-terminating-tasks-during-development)



### Dependencies

https://plugins.jetbrains.com/plugin/14936-kotlin-multiplatform-mobile

#### Nexa and Bitcoin Cash Kotlin Library

This software uses the `libnexakotlin` library produced by Bitcoin Unlimited.

https://gitlab.com/nexa/libnexakotlin

### Run a Build

Start Android Studio and use the _Build_ menu to start a build.

### Test and Debug

#### Running Compose UI tests (Experimental)

Read more at the Kotlin Compose Multiplatform UI docs.
https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-test.html

##### iOS UI automated tests
*  Note iOS Compose tests do not actually appear on the device.
```bash
./gradlew iosTest
```

```bash
./gradlew :src:iosSimulatorArm64Test
```

##### Android UI automated tests
  On Android phones, you can watch the tests on-device.

Running `connectedAndroidTest` requires that an android device is connected to your computer:

```bash
./gradlew :src:connectedAndroidTest
```

`pixel5DebugAndroidTest` uses a pixel5 emulator to run the tests:

```bash
./gradlew pixel5DebugAndroidTest
```

#### Running automated (unit) tests
To successfully run the units tests, you must have a local "regtest" Nexa full node running.
```
./gradlew :src:jvmTest
```

#### Code coverage reporting

Generate a code coverage .html report for jvm and Android with Kover (https://github.com/Kotlin/kotlinx-kover):
```
./gradlew koverHtmlReport
```

##### JVM
koverXmlReportJvm runs jvmTest as a child job
```
./gradlew :src:koverXmlReportJvm
```

The coverage report is written to `src/build/reports/kover/reportJvm.xmll`

#### Trigger iOS background processing task from Xcode
Physical device is required. Background processing is not supported in emulator

1. Start Xcode and have an iphone connected
2. Add a breakpoint after the task is scheduled. In line one of `func scheduleBGProcessingTask` in swift
3. Start the app using the play button in Xcode
4. A text input field will appear at the bottom of Xcode
5. Run one of the following command to trigger `func handleBackgroundProcessing()`:

```
e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"info.bitcoinunlimited.www.wally.backgroundProcessing"]
```

Alternatively, fire `func handleBackgroundAppRefresh`:

```
e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"info.bitcoinunlimited.www.wally.appRefresh"]
```

Read more in the [official Apple documentation](https://developer.apple.com/documentation/backgroundtasks/starting-and-terminating-tasks-during-development)


## Localization

WallyWallet text is internationalized in the Android standard manner, via specialization of app/src/main/res/values/strings.xml.  See https://developer.android.com/guide/topics/resources/localization.
However to apply this to a multiplatform project, some customization is needed.  To regenerate the internationalized text into multiplatform resource files, see "Generate required files" above.

## libnexa.dylib 

### versioning

Current version: 1.4.0.0

From release: https://gitlab.com/nexa/nexa/-/releases/nexa1.4.0.0

### Installation for JVM Mac m1

1. Download nexa-<version>-macos-arm64-unsigned.tar.gz
2. Uncompress
3. Copy nexa-1.4.0.0/lib/libnexa.0.dylib into wally root as libnexa.dylib
4. codesign --deep --force --sign - ./libnexa.dylib