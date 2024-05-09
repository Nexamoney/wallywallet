# Wally Personal Wallet

This is a non-custodial SPV & Electrum protocol wallet for Nexa and Bitcoin Cash.

<a href='https://play.google.com/store/apps/details?id=info.bitcoinunlimited.www.wally&hl=en&gl=US&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' style="width: 250px;" width="250" /></a>

<a href="https://www.producthunt.com/posts/wally-nexa-wallet?utm_source=badge-featured&utm_medium=badge&utm_souce=badge-wally&#0045;nexa&#0045;wallet" target="_blank"><img src="https://api.producthunt.com/widgets/embed-image/v1/featured.svg?post_id=452242&theme=light" alt="Wally&#0032;Nexa&#0032;Wallet - Open&#0032;source&#0032;light&#0032;Wallet&#0032;for&#0032;Nexa&#0046;&#0032;Support&#0032;coins&#0032;and&#0032;NFTs | Product Hunt" style="width: 250px; height: 54px;" width="250" height="54" /></a>

[TestFlight BETA for iPhone](https://testflight.apple.com/join/AznUHg38)

The TestFlight BETA is used for testing new features and bugfixes and usually has a never build than App Store

<a href="https://apps.apple.com/us/app/wally-nexa-wallet/id6469619075?itsct=apps_box_badge&amp;itscg=30200" style="display: inline-block; overflow: hidden; border-radius: 13px; width: 250px; height: 83px;"><img src="https://tools.applemediaservices.com/api/badges/download-on-the-app-store/black/en-us?size=250x83&amp;releaseDate=1715212800" alt="Download on the App Store" style="border-radius: 13px; width: 250px; height: 83px;"></a>

## Cloning

Use `git clone https://gitlab.com/wallywallet/android.git` or `git clone git@gitlab.com:wallywallet/android.git` to clone this repository.

## Platforms

### JVM

#### Building

```bash
./gradlew appJar
```

##### Generate version file
```
./gradlew generateVersionFile
```

#### Execution

In Android Studio, create a "JAR Application" in edit run configurations.  Just put the application (fat) jar file into the "Path To Jar" field.
Then go down to "Before Launch" and add a gradle task with this project "wpw" and task name "appJar".  The Jar file is located at:

```bash
YOUR_PROJECT_PATH/build/libs/wpw-app.jar
```

## Building




### Tools

#### Android Studio

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

### Dependencies

#### Bitcoin Cash Kotlin Library

This software uses the "libbitcoincash" library produced by Bitcoin Unlimited, via the libbitcoincashkotlin project.  This project is located under the "WallyWallet" group, as a sibling to this project.

If you cloned this repository, this project's app/build.gradle file most likely points to a locally built libbitcoincash library:

```
implementation files('aars/libbitcoincash-debug.aar')  // locate this directory at: <repo_home>/app/aars/
```
To actually use this locally built version, you need to clone libbitcoincashkotlin, build it, and copy (or symlink) the resulting aar file to "src/aars/".

If you do not plan to modify the libbitcoincash library, you can use the released version by commenting out the above line and uncommenting the line that is similar to:
```
//implementation "info.bitcoinunlimited:libbitcoincash:0.2.1"
```

##### Bitcoin Unlimited

Libbitcoincashkotlin uses the libbitcoincash shared library that is build as part of the BCH Unlimited full node project.  That project in turn required C++ boost header files.  Your build of libbitcoincashkotlin should have automatically cloned BCH Unlimited and boost underneath libbitcoincashkotlin.  For more information about troubleshooting the process or about setting up a full stack (from libbitcoincash to the wallet) development environment see the libbitcoincashkotlin project.

### Run a Build

Start Android Studio and use the _Build_ menu to start a build.


### Troubleshooting

#### Error running src/cashlib/buildBoostAndroid.sh

Don't worry about it.  We only use boost headers right now.

#### Error compiling under Android Studio: missing int128 type

You are including the host's version of libsecp256k1-config.h.
Search the Bitcoin Unlimited source tree for this file and remove any copies except for the one located in src/cashlib.

## Running Compose UI tests (Experimental)
Compose UI test commands:
```
./gradlew pixel5DebugAndroidTest
./gradlew :src:connectedAndroidTest
./gradlew :src:jvmTest
./gradlew :src:iosSimulatorArm64Test
```

Read more at the Kotlin Compose Multiplatform UI docs.

https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-test.html

## Localization

WallyWallet text is internationalized in the Android standard manner, via specialization of app/src/main/res/values/strings.xml.  See https://developer.android.com/guide/topics/resources/localization.

## libnexa.dylib 

### versioning

Current version: 1.4.0.0

From release: https://gitlab.com/nexa/nexa/-/releases/nexa1.4.0.0

### Installation for JVM Mac m1

1. Download nexa-<version>-macos-arm64-unsigned.tar.gz
2. Uncompress
3. Copy nexa-1.4.0.0/lib/libnexa.0.dylib into wally root as libnexa.dylib
4. codesign --deep --force --sign - ./libnexa.dylib