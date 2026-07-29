# Wallet create / backup / restore checklist

A safe sequence for creating a wallet, backing up its recovery phrase, encrypting it, and
restoring it later. This is the operational checklist behind `SKILL.md`; read the skill for the
lifecycle model and the new-vs-recover scan distinction. The API calls are libnexakotlin
(`init.kt`, `wallet.kt`); `walletStartupTemplate.kt` is the drop-in code form.

## The one fact that drives everything

- **`newWallet`** creates a wallet that does **not** scan chain history. It starts at zero balance
  and only ever sees coins received *after* creation.
- **`recoverWallet`** restores a wallet from a recovery phrase and **does** scan history (from the
  chain checkpoint), so it rediscovers prior coins.

Using `newWallet` with an already-used recovery phrase is the classic "my restored wallet shows
zero balance" bug — it won't scan, so it never finds the existing funds. **Create → `newWallet`;
restore → `recoverWallet`. Never cross them.**

## Create flow (new wallet)

1. **Initialize the native layer** once at startup (platform-specific init; e.g.
   `initializeLibNexa()`), before any wallet/chain call.
2. **Create:** `val w = newWallet(name, chain)` (or `openOrNewWallet(name, chain)` to open-or-create).
   The wallet now exists locally with a freshly generated seed.
3. **Surface the recovery phrase exactly once:** read `w.recoverySecret` (the BIP-style word list)
   and show it to the **user only**, for them to write down.
   - **Never** log it, transmit it, store it in plaintext, screenshot it, or send it to a server.
     Anyone with the phrase controls the funds on any device.
4. **Confirm the backup:** have the user re-enter (or confirm) the phrase before you let them
   receive funds, so a mistyped/missed word is caught while the wallet is still empty.
5. **Encrypt at rest:** `w.encrypt(passphrase)` so the wallet file is not stored in the clear.
   After this the secret is locked.
6. **Persist:** `w.saveBip44Wallet()` (if not already saved by the create path).
7. Only now begin receiving: `w.getNewAddress()` for a receive address; `w.balance` to read funds.

## Lock / unlock (day-to-day)

- `w.lockedState(): Boolean?` — is the wallet currently locked?
- `w.unlock(passphrase): Boolean?` — unlock to sign/spend (returns success/failure/null).
- `w.lock()` — re-lock when idle.

Keep the wallet locked except around operations that need the secret (signing a spend). Treat the
passphrase like any credential — never log it.

## Restore flow (existing wallet, from phrase)

1. Initialize the native layer (as above).
2. **Restore with scan:** `val w = recoverWallet(name, recoveryPhrase, chain)`. This scans history
   from the checkpoint — it can take a while on first run, unlike a freshly created wallet.
3. **Wait for initial sync** before trusting the balance: poll `w.synced(-1L)` (true ≈ "synced to
   now"), or in a UI compare `w.syncedHeight` against the chain tip and show progress. A balance
   read *before* the scan completes will look too low.
4. **Re-encrypt** on the new device: `w.encrypt(passphrase)`.

## Connecting the chain

`newWallet`/`recoverWallet` connect a `Blockchain` for you. To control or inspect it:

- `w.blockchain` — the wallet's `Blockchain`; `w.blockchain.curHeight` / `getTip()` for the tip.
- `w.blockchain.net.exclusiveNodes(setOf("host:port"))` — restrict to specific node(s) (e.g. your
  own) instead of public seeders.
- For a wallet that does **not** run SPV (signing-only / offline), open it with
  `openDisconnectedWallet(name)` and attach a chain later if needed.

## Security checklist

- The recovery phrase is the master secret. Show once, to the user only; never log/transmit/store
  in clear. It bypasses on-device encryption — possession = control of funds.
- `encrypt(...)` so the wallet isn't on disk in the clear; keep it `lock()`ed except when signing.
- Don't display or persist `recoverySecret` after the initial backup step.
- A restored wallet's balance is only trustworthy **after** initial sync — gate any
  balance-dependent UI/logic on `synced()`.

## Related

- `SKILL.md` — the full lifecycle, the keys-you-control vs external-Wally distinction, and the
  WallyEnterpriseWallet decision (Pattern 8).
- `walletStartupTemplate.kt` — the open-or-create + sync-gating code.
- `nexa-transaction-construction` — once synced and unlocked, how to build/sign/broadcast a spend.