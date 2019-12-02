# Wally Wallet Bitcoin Cash Android Wallet

This is a non-custodial SPV wallet that gathers its information from "normal" Bitcoin Cash nodes via the Bitcoin P2P and ElectrumX protocols.


## Cloning

Use `git clone https://gitlab.com/wallywallet/android.git --recursive` or `git clone git@gitlab.com:wallywallet/android.git --recursive` to clone this repository and its submodules.

## Building


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

Next, tell this project where libbitcoincash lis located.  Load the file .../android/app/src/main/cpp/CMakeLists.txt into an editor and change the cashlib_src_DIR variable:

```
set( cashlib_src_DIR /fast/bitcoin/budev/src/cashlib )
```
to:
```
set( cashlib_src_DIR BUDIR/src/cashlib )
```

### Run a Build

Start Android Studio and use the Build menu items to start a build.


## Localization

WallyWallet text is internationalized in the Android standard manner, via specialization of app/src/main/res/values/strings.xml.  See https://developer.android.com/guide/topics/resources/localization.
