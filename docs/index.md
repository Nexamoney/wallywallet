# Wally Personal Wallet documentation

Wally is a non-custodial SPV and Electrum protocol wallet for Nexa, written in Kotlin
Multiplatform for Android, iOS, macOS and JVM desktop.

## Contents

* [User Interface Design Philosophy](designPhilosophy.md) — the concepts the interface is
  designed around, and what "navigation distance" costs the user.
* [Internationalization](i18n.md) — where translatable strings live and how the `S` object
  and the per-locale binary packs are generated.

Cloning, environment setup, build and test instructions are in the repository
[README](https://gitlab.com/wallywallet/wallet/-/blob/main/README.md).

## Adding to this site

Put a markdown file in the repository's `docs/` directory. The `pages` CI job picks it up
on the next build of `main` and adds it to the navigation under its first heading, with no
CI change needed. Only `docs/` is published, so markdown elsewhere in the repository stays
out of the site.
