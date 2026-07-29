/*
 * walletStartupTemplate.kt — a drop-in open-or-create wallet startup block for a Nexa app.
 *
 * The shape every app needs at launch: initialize the native layer, open the wallet if it exists
 * or create a new one if it doesn't, connect the chain, and gate work on initial sync. Grounded in
 * libnexakotlin init.kt / iWallet.kt / blockchain.kt. See SKILL.md for the lifecycle model and the
 * decisive new-vs-recover distinction; recoveryFlow.md for the safe create/backup/restore checklist.
 *
 * KEY FACT this encodes: newWallet does NOT scan chain history (addBlockchain(bc, -1, -1)), while
 * recoverWallet DOES (addBlockchain(bc, checkpointHeight, 0)). So a freshly *created* wallet starts
 * empty and only sees coins sent after creation; a *recovered* wallet must scan to find its funds.
 * Use newWallet for a brand-new wallet, recoverWallet to restore from a recovery phrase — never
 * newWallet with an existing phrase (it won't find the existing coins). See recoveryFlow.md.
 */

import org.nexa.libnexakotlin.Bip44Wallet
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.newWallet
import org.nexa.libnexakotlin.openWallet
import org.nexa.libnexakotlin.recoverWallet

/**
 * Open the named wallet, creating a fresh one if it doesn't exist yet.
 *
 * @param walletName  the on-device wallet name (also its database key)
 * @param chain       which chain a *new* wallet should be created on (ignored when opening an
 *                    existing one — openWallet reads the chain from the wallet file itself)
 * @param onCreated   called only when a NEW wallet was created — your cue to run the
 *                    show-phrase → confirm-backup → encrypt flow (see recoveryFlow.md)
 */
fun openOrCreateWallet(
    walletName: String,
    chain: ChainSelector,
    onCreated: (Bip44Wallet) -> Unit = {},
): Bip44Wallet {
    // 1) Initialize the native layer once before any wallet/chain call. The exact entry point is
    //    platform-specific (e.g. initializeLibNexa()); call your platform's init here.
    //    On JVM/test you also need scriptmachine's Initialize() if you compile/run contracts.

    // 2) Open if present, else create. openWallet throws if the wallet doesn't exist yet.
    return try {
        openWallet(walletName)                 // reads the wallet's own ChainSelector from its file
    } catch (notFound: Exception) {
        // Brand-new wallet: does NOT scan history, so it starts at zero balance by design.
        val w = newWallet(walletName, chain)
        onCreated(w)                           // back up the recovery phrase, then encrypt — now
        w
    }
    // (libnexakotlin also offers openOrNewWallet(name, cs) which does this open-or-create in one
    //  call; the explicit form above is shown so the onCreated backup hook is obvious.)
}

/**
 * Restore an existing wallet from its recovery phrase. Unlike newWallet, this scans chain history
 * from the checkpoint so the wallet rediscovers its prior coins.
 */
fun restoreWallet(walletName: String, recoveryPhrase: String, chain: ChainSelector): Bip44Wallet =
    recoverWallet(walletName, recoveryPhrase, chain)   // scans history (addBlockchain(bc, checkpointHeight, 0))

/**
 * Block until the wallet has done its initial sync (or a timeout elapses), then run `whenReady`.
 *
 * synced(-1) means "synced to ~now". Pass a height/epoch-ms to test synced-to-a-point. A freshly
 * created wallet syncs almost instantly (nothing to scan); a recovered one may take a while.
 */
fun whenSynced(wallet: Bip44Wallet, whenReady: () -> Unit) {
    // sync(...) blocks up to maxWait; or poll wallet.synced() on your own scheduler / coroutine.
    if (wallet.synced(/* epochMsOrHeight */ -1L)) {
        whenReady()
    } else {
        // e.g. wallet.sync(maxWait = 60L, epochTimeinMsOrBlockHeight = -1L); then whenReady()
        // In a UI app, observe sync progress (syncedHeight vs blockchain tip) instead of blocking.
        whenReady()
    }
}

/*
 * Typical launch sequence:
 *
 *   // Develop on testnet by default; NEXAREGTEST only if you need to force-mine blocks, NEXA for prod.
 *   val wallet = openOrCreateWallet("mainTestnet", ChainSelector.NEXATESTNET) { fresh ->
 *       showRecoveryPhrase(fresh.recoverySecret)   // SHOW ONCE, never log/transmit
 *       // ... user confirms they wrote it down ...
 *       fresh.encrypt(userPassphrase)              // encrypt at rest
 *   }
 *   whenSynced(wallet) {
 *       val addr = wallet.getNewAddress()          // a fresh receive address
 *       val bal  = wallet.balance                  // Long, in satoshis
 *   }
 *
 * To restrict which node(s) the wallet talks to (e.g. your own):
 *   wallet.blockchain.net.exclusiveNodes(setOf("your.node.host:<electrum port>"))
 */