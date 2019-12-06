# Wally Wallet Bitcoin Cash Android Wallet

This is a non-custodial SPV wallet that gathers its information from "normal" Bitcoin Cash nodes via the Bitcoin P2P and ElectrumX protocols.


## Cloning

Use `git clone https://gitlab.com/wallywallet/android.git --recursive` or `git clone git@gitlab.com:wallywallet/android.git --recursive` to clone this repository and its submodules.

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

This project has a submodule "android/app/src/main/java/libbitcoincash" which must be the libbitcoincashkotlin project which is another project under the "WallyWallet" group.

If you did not clone this repo using the --recursive flag, you can check out this submodule by running:

```bash
git submodule update --init --recursive
```
#### Bitcoin Unlimited

This software uses the "libbitcoincash" library produced by Bitcoin Unlimited, via the libbitcoincashkotlin proejct.  This requires that libbitcoincash be built by Android Studio for Android.  To accomplish this check out Bitcoin Unlimited in a separate directory.  Let us imagine that you check BU out in the directory named "BUDIR":

```bash
git clone git@github.com:BitcoinUnlimited/BitcoinUnlimited.git BUDIR
```

Next prepare BU for the libbitcoincash compilation:

```bash
cd BUDIR
./autogen.sh
```

**Note, do NOT run ./configure.sh**. If you want to do a separate (non-Android) build, then do an out-of-source-tree build.  It is necessary for the Android build to pick up the _libsecp256k1-config.h_ file located in src/cashlib.  If you have run ./configure.sh, the Android build will pick up the _libsecp256k1-config.h_ file created during configure (which will result in a configuration optimized for your host machine), rather than a configuration compatible with Android devices.

Next, tell this project where libbitcoincash is located.  Load the file .../android/app/src/main/cpp/CMakeLists.txt into an editor and change the cashlib_src_DIR variable:

```
set( cashlib_src_DIR /fast/bitcoin/budev/src/cashlib )
```
to:
```
set( cashlib_src_DIR BUDIR/src/cashlib )
```

#### Boost

The Android build expects that the boost source code is located in src/cashlib/boost.

BitcoinUnlimited comes with a script to download and compile boost for Android.  Execute buildBoostAndroid.sh in the src/cashlib directory and create a symbolic link from the specific boost versioned directory (created by buildBoostAndroid.sh) to the name "boost" i.e:
```bash
ln -s boost_1_70_0 boost
```

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
