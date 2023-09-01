# Wally Personal Wallet

This is a non-custodial SPV & Electrum protocol wallet for Nexa and Bitcoin Cash.

## Cloning

Use `git clone https://gitlab.com/wallywallet/android.git` or `git clone git@gitlab.com:wallywallet/android.git` to clone this repository.

## Platforms

### JVM

#### Building

```bash
./gradlew appJar
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

## Localization

WallyWallet text is internationalized in the Android standard manner, via specialization of app/src/main/res/values/strings.xml.  See https://developer.android.com/guide/topics/resources/localization.
