package repositories

import org.nexa.libnexakotlin.contracts.TimeLockVault
import org.nexa.libnexakotlin.contracts.TimeLockVaultDestination
import org.nexa.libnexakotlin.contracts.VaultScope
import info.bitcoinunlimited.www.wally.Account
import info.bitcoinunlimited.www.wally.screenOwnsTxFeedback
import info.bitcoinunlimited.www.wally.wallyApp
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.atomicfu.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.nexa.libnexakotlin.Bip44Wallet
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.GroupId
import org.nexa.libnexakotlin.GroupInfo
import org.nexa.libnexakotlin.Hash256
import org.nexa.libnexakotlin.NexaTxOutpoint
import org.nexa.threads.Mutex
import org.nexa.libnexakotlin.OP
import org.nexa.libnexakotlin.PayAddress
import org.nexa.libnexakotlin.SatoshiScript
import org.nexa.libnexakotlin.SatToString
import org.nexa.libnexakotlin.Spendable
import org.nexa.libnexakotlin.TransactionHistory
import org.nexa.libnexakotlin.WalletNotEnoughBalanceException
import org.nexa.libnexakotlin.fromHex
import org.nexa.libnexakotlin.iTxOutpoint
import org.nexa.libnexakotlin.iTxOutput
import org.nexa.libnexakotlin.nativeCoinGroupId
import org.nexa.libnexakotlin.outpointFor
import org.nexa.libnexakotlin.toHex
import org.nexa.libnexakotlin.txOutputFor
import kotlin.concurrent.Volatile

private val LogIt = GetLog("BU.timelockrepo")

/**
 * One repository for all of this Account's Time Lock Vaults.
 *
 * One account owns one [TimeLockContractRepository] and one
 * [TimeLockContractRepository] owns all the time lock repository
 * contracts for that account.
 *
 * The repository keeps a small derived view of each vault ([Vault]: name,
 * index, unlock height, destination) — rebuilt from the library's records, not
 * an independent source of truth. The set of contracts themselves is owned by
 * the wallet ([Bip44Wallet.getContractType] / persistence), so the repository
 * does not hold a contracts map.
 */
class TimeLockContractRepository(
    private val account: Account,
    private val accountIndex: Long = 1L,
    val chain: ChainSelector = account.wallet.chainSelector,
)
{
    private val wallet: Bip44Wallet get() = account.wallet

    companion object
    {
        /**
         * Wallet-contract name of the single factory that derives every vault.
         */
        const val FACTORY_NAME: String = "timelock"

        /**
         * UI-side estimate of the worst-case time vault contract claim fee,
         * in native satoshis (1 NEXA = 100 sat).
         */
        const val CLAIM_FEE_HEADROOM_SAT: Long = 2000L

        /**
         * Minimum funding amount for a freshly-created vault, in native
         * satoshis (1 NEXA = 100 sat) — 20 NEXA. This matched Otoplo's vault
         * floor. Kept equal to [CLAIM_FEE_HEADROOM_SAT] so any vault the UI
         * lets the user create can later pay its own claim fee with a
         * comfortable margin.
         */
        const val MIN_VAULT_FUNDING_SAT: Long = CLAIM_FEE_HEADROOM_SAT

        /**
         * Conservative fee estimate for the vault funding tx, used by the
         * [addVault] pre-flight balance check. The real fee comes from
         * txCompleter at send time and is far smaller.
         */
        const val FUNDING_FEE_HEADROOM_SAT: Long = CLAIM_FEE_HEADROOM_SAT

        // Claim-size estimate, calibrated against a real mainnet claim: one vault input and one payout
        // output measured 195 B (215 sat at the wallet's DesiredFeeSatPerByte). A vault input carries the
        // ~109 B time-lock satisfier on top of its outpoint, amount and sequence.
        private const val CLAIM_TX_OVERHEAD_BYTES: Long = 10
        private const val VAULT_INPUT_BYTES: Long = 150
        private const val CLAIM_OUTPUT_BYTES: Long = 35
        private const val WALLET_INPUT_BYTES: Long = 110

        /**
         * The UI rejects anything above this so
         * the user gets a friendly message rather than the library exception.
         * Highest unlock height a vault may commit to: one below the
         * library's [TimeLockVault.LOCKTIME_THRESHOLD], the nLockTime
         * height/time split — at or past it a full node reads the value as a
         * Unix epoch time, not a block height, and the library's
         * buildDestination rejects it.
         */
        const val MAX_UNLOCK_BLOCK_HEIGHT: Long = TimeLockVault.LOCKTIME_THRESHOLD - 1
    }

    /**
     * One row in a vault's transaction history list: a deposit into the contract or a claim from the contract.
     */
    data class VaultTx(
        /** The wallet's record of this tx — idem, confirmed height, date, note. */
        val history: TransactionHistory,
        val kind: VaultTxKind,
        /**
         * Native satoshis (1 NEX = 100 sat) moved by this side of the tx.
         * Counts only pure-native outputs/inputs at the vault address — the
         * dust carried by token/authority outputs is reported through
         * [tokenMovements] / [authorities] instead, so a token deposit shows
         * the asset that was sent rather than the output's incidental dust.
         */
        val amountSat: Long,
        /**
         * Per-group token amount this side of the transaction moved at the vault
         * address. Empty for a pure-native tx.
         */
        val tokenMovements: Map<GroupId, Long> = emptyMap(),
        /** Group authorities (batons) this side of the transaction moved to the vault. */
        val authorities: List<GroupInfo> = emptyList(),
        /**
         * Network fee paid by this tx, in native satoshis (1 NEX = 100 sat). Only populated for a
         * [VaultTxKind.Claim] that is a pure vault sweep (every input drained
         * this vault), where the fee is the difference between the swept inputs
         * and the payout outputs; `0` otherwise. The claim's [amountSat] is the
         * gross drained from the vault, so the wallet received `amountSat - feeSat`.
         */
        val feeSat: Long = 0L,
    )

    /**
     * What direction a [VaultTx] moves value relative to the vault.
     * `Claim` = funds leaving the vault (the user swept it).
     * `Deposit` = funds arriving at the vault address (vault was funded);
     */
    enum class VaultTxKind { Deposit, Claim }

    /**
     * Result of a successful [addVault] call. Carries the bits the user interface needs
     * for its post-create success card.
     */
    data class VaultCreated(
        val name: String,
        val address: PayAddress,
        val fundingTxIdem: Hash256,
    )

    /**
     * One vault's wallet-state snapshot — everything the user interface layer needs to
     * render the vault.
     *
     * Time-derived fields (estimated unlock date, days remaining, % locked)
     * are deliberately *not* included here because they are calculated in the viewmodel.
     */
    data class VaultSnapshot(
        val name: String,
        val address: PayAddress?,
        /** Unlock height committed in the time lock vault script; `null` if no destination is built. */
        val unlockBlockHeight: Long?,
        val isUnlockable: Boolean,
        /** Blocks remaining until the unlock block; `null` when wallet is unsynced. */
        val blocksUntilUnlock: Long?,
        val nativeBalanceSat: Long,
        /** Fee a claim would pay, from the wallet's own rate and this vault's real input count. */
        val claimFeeSat: Long,
        /** Token balances by group; excludes the native coin NEX. */
        val tokenBalances: Map<GroupId, Long>,
        val authorities: List<GroupInfo>,
        /** Earliest confirmed deposit's commit height; or `null` if is none. */
        val earliestDepositHeight: Long?,
        /**
         * Whether the balance fields above came from a successful on-chain
         * refresh. `false` means [refreshBalances] has not yet returned a
         * value for this vault — when it's null,' the UI should show a loading indicator.
         */
        val isBalanceLoaded: Boolean,
        /**
         * Any tx spending a UTXO at the vault address -- Whether the vault's history
         * contains a claim transaction. Once a time-lock vault
         * has been claimed, it is considered done: the UI marks it "Claimed" and
         * hides the Claim action even if funds later land at the address again.
         */
        val hasClaimTx: Boolean,
    )

    /**
     * The library's own vault destinations, keyed by user interface name, in index order.
     * A destination already carries everything a vault is — its BIP-44 leaf
     * ([TimeLockVaultDestination.hdIndex], which the library owns), the
     * committed unlock value, the address and the key material — so nothing is
     * re-declared here. Rebuilt from the library's saved records by
     * [reloadVaults], never an independent source of truth.
     */
    private val vaultsRef = atomic<Map<String, TimeLockVaultDestination>>(emptyMap())
    private val vaults: Map<String, TimeLockVaultDestination> get() = vaultsRef.value

    /** This vault's user interface key. */
    private val TimeLockVaultDestination.uiName: String get() = vaultName(hdIndex.toInt())

    /** Add a vault to the vaultsRef */
    private fun putVault(name: String, vault: TimeLockVaultDestination)
    {
        vaultsRef.update { LinkedHashMap(it).apply { this[name] = vault } }
    }

    /** Empty the vaultsRef. */
    private fun clearVaults()
    {
        vaultsRef.value = emptyMap()
    }

    /**
     * The single factory contract that derives every vault. Freshly constructed or reused from the
     * wallet if it already loaded one (the wallet persists & reloads
     * contracts by name). Only registered with the wallet ([registerFactoryIfReady]) once it has a destination —
     * `Bip44Wallet.saveContracts` calls `save()` on every registered
     * contract, and [TimeLockVault.save] throws when no vault is built.
     */
    private val factory: TimeLockVault

    /** Whether [factory] is in the accounts/wallet's contract map yet. */
    private var factoryAdded: Boolean

    private val _onChainNativeSat = MutableStateFlow<Map<String, Long>>(emptyMap())

    /**
     * Vaults whose claim sweep has been broadcast but isn't yet reflected
     * on-chain, mapped to the native balance they held at claim time.
     */
    private val _claimingVaults = MutableStateFlow<Map<String, Long>>(emptyMap())

    /**
     * Vault UTXOs electrum failed to list on the previous refresh, keyed by outpoint. A single miss can
     * be a server that has not indexed a fresh deposit yet, so only a second consecutive miss retires
     * the UTXO. Injected vault UTXOs carry no commit height or date, so age is not usable here.
     */
    private val missingVaultUtxos = mutableSetOf<String>()

    /**
     * Electrum-reported native balance per vault, keyed by vault name. This is
     * **not** the balance shown on the card — [Vault.snapshot] reconstructs
     * that from the wallet's tx history. It drives refresh signaling (the view
     * model re-publishes its snapshot on each emission) and lets
     * [refreshBalances] detect when a claim sweep has settled (clearing
     * [_claimingVaults]).
     *
     * Emits a new map every time [refreshBalances] completes; empty until the
     * first call.
     */
    val onChainNativeSat: StateFlow<Map<String, Long>> = _onChainNativeSat.asStateFlow()

    init
    {
        LogIt.info("init: starting for account=${account.name} chain=$chain")
        // Construct a fresh factory or reuse the factory the wallet already reloaded
        // (it persists & reloads contracts by name). A freshly-constructed
        // factory is NOT added to the wallet until it has a destination — see
        // [factory] / [registerFactoryIfReady].
        //
        // Only the (cheap) factory handle is resolved here. The heavy startup
        // walk lives in the start function so it never runs on whatever thread happened
        // to construct the repository (e.g. the Compose composition thread).
        val existing = wallet.getContractType<TimeLockVault>(FACTORY_NAME)
        if (existing != null)
        {
            factory = existing
            factoryAdded = true
        }
        else
        {
            factory = TimeLockVault(FACTORY_NAME, wallet, accountIndex, chain)
            factoryAdded = false
        }
    }

    /**
     * Serializes the one-time startup walk. A single shared repository is
     * driven by several view models, so both call [start]; the mutex makes
     * a late caller *await* an in-flight walk rather than race past it,
     * and [startCompleted] makes the walk itself run exactly once.
     */
    private val startMutex = kotlinx.coroutines.sync.Mutex()
    @Volatile private var startCompleted = false
    /** The wallet tx count the last discovery walk covered; see [discoverVaultsFromChain]. */
    @Volatile private var discoveredAtTxCount = -1L

    /**
     * One-time, heavy startup: rebuild the per-vault view from the library's
     * saved records, register the factory, inject vault addresses into the
     * wallet's receiving set, retag pre-existing UTXOs, and walk the wallet's
     * transaction history to recover vaults created on another device / from a
     * mnemonic.
     *
     * This work scales with wallet history size, so it **must not run on the
     * UI thread** — Idempotent, so it is safe to call more than once.
     */
    suspend fun start()
    {
        startMutex.withLock {
            if (startCompleted) return@withLock

            // This is where "the library keeps the index" lives:
            // we read indices back from the saved records (via load).
            // Walk the library's saved (factory, index) records to rebuild the
            // per-vault view.
            reloadVaults()
            registerFactoryIfReady()
            LogIt.info("start: loaded ${vaults.size} vault(s): ${vaults.keys}")

            // Make sure every loaded vault's address is in the wallet's
            // receiving set. This makes the bloom filter match its UTXOs and
            // markInterestingSpendable tag each UTXO. Without this, contract.balance()
            // and filterInputs read 0 even when the address has funds on chain.
            injectVaultAddresses()

            // Any UTXO that landed before the factory was registered (typical
            // on startup) sits untagged. Because the markInterestingSpendable
            // hook only fires at UTXO ingest time, so walk the wallet's UTXOs
            // once and tag any that sit at a known vault address with the factory's contractId.
            retagWalletUtxos()

            // Walk the wallet's tx history for any time-lock outputs that
            // belong to this mnemonic but aren't represented locally — covers vaults
            // created on another device, or restored from mnemonic. Best-effort:
            // only finds vaults whose funding transactions are already in the wallet's
            // tx history, so a subsequent call after more sync may turn up more.
            discoverVaultsFromChain()

            startCompleted = true
        }
    }

    // --- Catalog -------------------------------------------------------

    /** Vault names that are currently held, in index order. */
    fun names(): List<String> = vaults.keys.toList()

    /** The number of vaults currently held. */
    fun size(): Int = vaults.size

    /** Whether a vault by this name is currently held in the vaults map. */
    fun contains(name: String): Boolean = vaults.containsKey(name)

    // --- Vault lifecycle -----------------------------------------------

    /**
     * Add a fresh vault at [unlockBlockHeight] and fund it with
     * [amountSat] native satoshis from the user's main account. Allocates
     * the next library index via [TimeLockVault.nextDestination], persists
     * the slot, registers the address, sends the funding tx, and returns a
     * [VaultCreated] result.
     *
     * The vault name (UI key) is derived from the library index; callers
     * don't get to choose it.
     *
     * An empty vault is not useful, so [amountSat] must be at least
     * [MIN_VAULT_FUNDING_SAT] (1 NEXA = 100 sat). The repo guards this with
     * a `require` — UI layers should refuse to call `addVault` below the
     * minimum so the user sees a friendly message rather than an exception.
     *
     */
    fun addVault(unlockBlockHeight: Long, amountSat: Long): VaultCreated
    {
        LogIt.info("addVault: unlockBlockHeight=$unlockBlockHeight amountSat=$amountSat")
        require(amountSat >= MIN_VAULT_FUNDING_SAT) {
            "addVault requires at least $MIN_VAULT_FUNDING_SAT sat (1 NEXA) of funding; got $amountSat"
        }

        // Pre-flight: fail with a clear error BEFORE any vault state is created to prevent empty vaults.
        // The funding send runs after the vault is registered, so a funding
        // failure there would leave a confusing empty vault card behind.
        val availableSat = wallet.balance
        if (availableSat < amountSat + FUNDING_FEE_HEADROOM_SAT)
            throw WalletNotEnoughBalanceException(
                "Insufficient balance: locking ${SatToString(amountSat)} plus fees needs about " +
                "${SatToString(amountSat + FUNDING_FEE_HEADROOM_SAT)}, but only " +
                "${SatToString(availableSat)} is available.")

        val newIndex = if (vaults.isEmpty()) 0 else vaults.values.maxOf { it.hdIndex }.toInt() + 1
        factory.buildDestination(unlockBlockHeight, newIndex)
        factory.save(wallet.wdb)

        val destination = factory.destination as? TimeLockVaultDestination
            ?: error("TimeLockVault destination missing after buildDestination()")
        val address = destination.address
            ?: error("TimeLockVault destination missing after buildDestination()")

        registerFactoryIfReady()
        val name = vaultName(newIndex)
        putVault(name, destination)

        // This helps the wallets bloom filter / address include the vault,
        // so the funding tx's output is tagged with the contract id so that
        // contract.balance() / filterInputs return the correct amount.
        wallet.injectReceivingAddresses(listOf(destination))
        val fundingTxIdem = wallet.send(amountSat, address).idem
        LogIt.info("addVault: created $name (index $newIndex) @ $address funded by tx $fundingTxIdem")
        return VaultCreated(name = name, address = address, fundingTxIdem = fundingTxIdem)
    }

    /** Fee a funding transaction would pay: one wallet input, the vault output and change. */
    fun estimatedFundingFeeSat(): Long =
        wallet.feeForSize(CLAIM_TX_OVERHEAD_BYTES + WALLET_INPUT_BYTES + 2 * CLAIM_OUTPUT_BYTES)

    /** User Interface key for the time lock vault at the given library index. */
    private fun vaultName(index: Int): String = "vault-${index + 1}"

    /**
     * Rebuild the [vaults] from the library's persisted state.
     *
     * The library stores ONE index-free record per contract because its DB key holds
     * neither index nor unlock value, so there is nothing to slot-walk: a
     * single [TimeLockVault.load] restores the factory's current cursor and
     * destination. Every time lock vault the factory ever built is then enumerated via
     * [TimeLockVault.knownVaults] — the current destination, every receiving-map
     * destination stamped with this contract's name (funded or NOT, so a
     * created-but-unfunded vault survives a restart), and any tagged UTXO's
     * backing vault. Chain-only vaults (e.g. after a seed restore) are picked
     * up separately by [discoverVaultsFromChain].
     *
     * Leaves the factory pointed at the highest-index vault so its
     * (index, destination) agree before registerFactoryIfReady()/saveContracts
     * can run.
     */
    private fun reloadVaults()
    {
        clearVaults()
        // Restore the factory cursor + current destination. This is absent for a wallet
        // that never saved a vault — harmless; knownVaults do still enumerate any
        // receiving-map / tagged-UTXO vaults.
        try { factory.load(wallet.wdb) }
        catch (e: Throwable) { LogIt.info("reloadVaults: no saved contract record ($e)") }

        for (dest in factory.knownVaults(onlyUnspent = false).values)
        {
            val idx = dest.hdIndex.toInt()
            val name = vaultName(idx)
            putVault(name, dest)
        }

        // Re-pin the factory index to the highest-index vault. recoverDestination (not
        // buildDestination) so a timestamp vault, whose unlock value sits above
        // the block height/time split, is not rejected by the creation-height guard.
        val last = vaults.values.maxByOrNull { it.hdIndex }
        if (last != null && factory.index.toLong() != last.hdIndex)
        {
            factory.recoverDestination(last.unlockValue, last.hdIndex.toInt())
        }
    }

    /** Add [factory] to the wallet once it has a time lock destination to persist. */
    private fun registerFactoryIfReady()
    {
        if (!factoryAdded && factory.destination != null)
        {
            wallet.add(FACTORY_NAME, factory)
            factoryAdded = true
        }
    }

    /** Inject every held time lock vault's address into the wallet's receiving set. */
    private fun injectVaultAddresses()
    {
        val dests = vaults.values.toList()
        if (dests.isNotEmpty())
        {
            LogIt.info("injectVaultAddresses: injecting ${dests.size} receiving address(es)")
            wallet.injectReceivingAddresses(dests)
        }
    }

    /**
     * Re-attach previously-saved time lock vaults from the wallet DB. With a single
     * factory there is no per-name load — re-walk the library's records and report whether [name] is now held.
     */
    fun loadVault(name: String): Boolean
    {
        reloadVaults()
        registerFactoryIfReady()
        return name in vaults
    }

    /**
     * Walk the wallet's existing UTXO set and tag any UTXO sitting at a
     * known time lock vault address with the factory's contractId. Mirrors what the
     * wallet's ingest path does at tx-arrival time, but for UTXOs that
     * landed *before* the factory was registered. Without this, those UTXOs
     * remain untagged and `contract.balance()` / `filterInputs` read 0.
     */
    private fun retagWalletUtxos()
    {
        if (vaults.isEmpty()) return
        val byAddr = vaults.values.associateBy { it.address.toString() }
        var tagged = 0
        wallet.forEachUtxo { sp ->
            val addr = sp.address?.toString()
            val vault = if (addr != null) byAddr[addr] else null
            if (vault != null)
            {
                val existing = sp.contractId
                val alreadyTagged = existing != null && existing.isNotEmpty() &&
                    existing contentEquals factory.contractId
                if (!alreadyTagged)
                {
                    sp.backingPayDestination = vault
                    sp.contractId = factory.contractId
                    sp.dirty = true
                    tagged++
                }
            }
            false
        }
        if (tagged > 0) LogIt.info("retagWalletUtxos: tagged $tagged UTXO(s) for the time-lock factory")
    }

    /**
     * Walk the wallet's tx history for time-lock vault outputs that belong to this
     * seed but aren't already held. Mirrors the recovery path in
     * libnexakotlin's `recoverOtoploVaultFromMnemonic` test:
     *
     *   1. Scan every output of every tx for a locking script starting with
     *      [TimeLockVaultDestination.TIME_LOCK_SCRIPT_PREFIX] — the constant
     *      22-byte prefix every time-lock vault on the network shares.
     *   2. Parse cleartext `(blockHeight, hdIndex)` out of the visible args.
     *   3. Derive a candidate vault at those parameters from this wallet's
     *      seed and accept iff its locking script bytes equal the on-chain
     *      output's. The match proves the constraint hash derived from this
     *      wallet's pubkey at `hdIndex` is what's committed on chain, i.e.
     *      the vault belongs to this wallet.
     *
     * Each newly-discovered time lock vault is persisted under the factory at its
     * on-chain `hdIndex` (so the address reproduces exactly), registered,
     * and its address injected into the wallet's receiving set. A discovered
     * index already in use is skipped — the single-factory model holds one
     * vault per index.
     *
     * @return names of vaults newly added by this call, in insertion order.
     */
    fun discoverVaultsFromChain(): List<String>
    {
        // start() owns the factory cursor while it walks. Discovery re-points that cursor through
        // recoverDestination, so running it concurrently wedges reloadVaults mid-load. Discovery is an
        // incremental refresh, so skipping it until the startup walk has finished loses nothing.
        if (!startCompleted) return emptyList()
        // A vault can only turn up with new history, so an unchanged tx count means nothing to walk
        val txCount = wallet.getTxCount()
        if (txCount == discoveredAtTxCount) return emptyList()
        val prefix = TimeLockVaultDestination.TIME_LOCK_SCRIPT_PREFIX
        val knownAddrs = vaults.values
            .mapTo(mutableSetOf()) { it.address.toString() }
        val usedIndexes = vaults.values.mapTo(mutableSetOf()) { it.hdIndex.toInt() }
        val seenAddrs = mutableSetOf<String>()
        val added = mutableListOf<String>()

        wallet.forEachTx { txHist ->
            for (output in txHist.tx.outputs)
            {
                val scriptHex = output.script.flatten().toHex()
                if (!scriptHex.startsWith(prefix)) continue

                val script = SatoshiScript(chain).fromHex(scriptHex)
                val insts = script.parsed()
                if (insts.size < 5) continue
                val blockHeight = OP.parse(insts[3]).number ?: continue
                val hdIndex = OP.parse(insts[4]).number ?: continue
                if (blockHeight <= 0 || hdIndex < 0 || hdIndex >= (1L shl 31)) continue
                val idx = hdIndex.toInt()
                if (idx in usedIndexes) continue

                // One unsupported or malformed candidate must not abort the
                // whole history walk and strand every vault after it.
                runCatching {
                    // Probe on the factory itself. Having a second TimeLockVault at the same contract
                    // index throws WalletAlreadyExistsException the moment the factory is registered
                    // — which it is on any wallet that has ever held a vault. recoverDestination re-derives
                    // without the creation-height guard, so a vault committed above the height/time split
                    // is probed rather than rejected; it only moves the cursor, and nothing is persisted unless
                    // the script matches below.
                    val candidate = factory.recoverDestination(blockHeight, idx)
                    if (candidate.lockingScript().flatten().toHex() != scriptHex) return@runCatching
                    val addr = candidate.address?.toString() ?: return@runCatching
                    if (addr in knownAddrs || addr in seenAddrs) return@runCatching
                    seenAddrs += addr

                    // Persist the discovered vault under the factory at its true on-chain index,
                    // then capture it into the derived view.
                    factory.save(wallet.wdb)
                    val name = vaultName(idx)
                    putVault(name, candidate)
                    usedIndexes += idx
                    knownAddrs += addr
                    added += name
                }.onFailure { e ->
                    LogIt.warning("discoverVaultsFromChain: skipping candidate at index $idx " +
                        "(unlock $blockHeight): $e")
                }
            }
            false
        }

        // Probing moved the shared cursor; re-pin it to the highest-index vault so a later addVault/claim doesn't act
        // through a probe's leftovers.
        val last = vaults.values.maxByOrNull { it.hdIndex }
        if (last != null && factory.index.toLong() != last.hdIndex)
            runCatching { factory.recoverDestination(last.unlockValue, last.hdIndex.toInt()) }

        if (added.isNotEmpty())
        {
            registerFactoryIfReady()
            val dests = added.mapNotNull { vaults[it] }
            if (dests.isNotEmpty()) wallet.injectReceivingAddresses(dests)
        }
        discoveredAtTxCount = txCount
        return added
    }

    /**
     * Inject any on-chain UTXOs the wallet doesn't already track at each held time lock vault's address,
     * then publish each vault's native balance (`electrum.getBalance`, confirmed + unconfirmed)
     * through [onChainNativeSat]. Idempotent — call as often as needed (e.g. on a poll, on app
     * foreground, after a send) to keep observers in sync with chain state.
     *
     * Blocking — callers run it through the app job functions (the view model enqueues it on the
     * app job pool). Network failures are logged and tolerated per-vault — a flaky electrum
     * should not blank out vaults that did fetch successfully.
     */
    fun refreshBalances()
    {
        val electrum = try { wallet.blockchain.net.getElectrum() }
            catch (e: Throwable)
            {
                if (e is CancellationException) throw e
                LogIt.error("refreshBalances: no electrum connection: $e")
                return
            }
        try
        {
            // Snapshot the (copy-on-write) vault view once so both the inject pass and the balance pass
            // below see the same generation even if a create/claim publishes a new map mid-refresh.
            val held = vaults
            // Read once: the reconciliation below compares every vault against the same history generation.
            val histIndex = buildVaultHistoryIndex()
            // Before reading balances, inject any on-chain UTXOs at each time lock vault address that
            // the wallet doesn't already track. Without this, addVault-created vaults can't be claimed
            // after a restart: the wallet sent the funding tx but never recorded the output as one of
            // its own UTXOs (the vault address only entered the receiving set after the tx was already done),
            // so contract.balance() / filterInputs read 0.
            for ((name, vault) in held)
            {
                val dest = vault
                val utxos = try { electrum.listUnspent(dest) }
                    catch (e: Throwable)
                    {
                        if (e is CancellationException) throw e
                        LogIt.warning("refreshBalances: listUnspent (inject) failed for vault $name: $e")
                        continue
                    }
                // A vault emptied outside this wallet's view - or by a claim whose broadcast landed after wally
                // gave up on it - leaves a deposit that history still counts as unspent, so the card keeps
                // offering a claim the chain can only reject as "Missing inputs". Reconcile against history
                // rather than the UTXO table, because that is what the card reads its balance from, and a
                // deposit can outlive its UTXO row. One miss can be an indexer that has not caught up, so only
                // a second consecutive miss counts.
                val onChain = utxos.mapNotNull { it.outpoint?.toHex() }.toSet()
                val addrKey = dest.address.toString()
                val staleKeys = (histIndex.depositsByAddr[addrKey]?.keys ?: emptySet())
                    .filter { it !in histIndex.spentOutpoints && it !in onChain && !missingVaultUtxos.add(it) }
                    .toSet()
                missingVaultUtxos.removeAll(onChain)
                if (staleKeys.isNotEmpty())
                {
                    // Absence from the indexer is not on its own enough to call coins gone - it has reported a
                    // funded vault address as empty - so find the transaction that actually spent them and hand
                    // that to the wallet. History then carries the claim, which is what the card reads its balance
                    // and its claimed state from. Only when no spender can be produced is the UTXO merely dropped:
                    // that still keeps a doomed claim from being offered, but the card goes on showing the deposit.
                    var recorded = 0
                    try
                    {
                        for ((height, txid) in electrum.getHistory(dest.ungroupedLockingScript()))
                        {
                            val tx = electrum.getTx(txid)
                            if (tx.inputs.none { it.spendable.outpoint?.toHex() in staleKeys }) continue
                            wallet.interestingTx(listOf(tx), null, if (height > 0) height.toLong() else null,
                                null, "time lock vault claim", forceRelevant = true)
                            recorded++
                        }
                    }
                    catch (e: Throwable)
                    {
                        if (e is CancellationException) throw e
                        LogIt.warning("refreshBalances: could not read the spender for vault $name: $e")
                    }
                    if (recorded > 0)
                        LogIt.info("refreshBalances: recorded $recorded spending tx for vault $name")
                    else
                    {
                        // No spender to be had, so at least retire the UTXO: the claim it would arm cannot succeed.
                        val orphans = mutableListOf<iTxOutpoint>()
                        wallet.forEachUtxoWithAddress(dest.address) { sp ->
                            sp.outpoint?.let { if (it.toHex() in staleKeys) orphans += it }
                            false
                        }
                        if (orphans.isNotEmpty()) wallet.deleteTxo(orphans)
                        LogIt.info("refreshBalances: dropped ${orphans.size} stale UTXO(s) for vault $name")
                    }
                    missingVaultUtxos.removeAll(staleKeys)
                }

                val candidates = utxos.filter { sp ->
                    val outpoint = sp.outpoint ?: return@filter false
                    wallet.getTxo(outpoint) == null
                }
                if (candidates.isEmpty()) continue
                // Rostrum's listunspent includes TOKEN UTXOs, but the electrum client maps every
                // entry to a Spendable carrying the address's UNGROUPED locking script. Injected
                // like that, a token UTXO reads as pure native — a later sweep would spend it without
                // re-emitting its group, an illegal melt the node rejects as "group-token-imbalance".
                // Index the vault's token UTXOs by outpoint and rebuild the real grouped script before
                // injecting. null value = known token UTXO whose grouped script cannot be rebuilt (no raw id)
                // — it must not be injected at all.
                val tokenByOutpoint = HashMap<NexaTxOutpoint, Pair<GroupId, Long>?>()
                val tokenIndexOk = try
                {
                    val addrStr = dest.address.toString()
                    var cursor: String? = null
                    do
                    {
                        val page = electrum.getTokenUnspent(addrStr, cursor = cursor)
                        for (u in page.unspent)
                        {
                            tokenByOutpoint[NexaTxOutpoint(u.outpoint_hash)] =
                                if (u.token_id_hex.isEmpty()) null
                                else GroupId(chain, u.token_id_hex.fromHex()) to u.token_amount
                        }
                        cursor = page.cursor
                    } while (cursor != null)
                    true
                }
                catch (e: Throwable)
                {
                    if (e is CancellationException) throw e
                    // Without the token index a token UTXO can't be told apart from a native one, and injecting it
                    // ungrouped would arm an invalid sweep — skip this vault's inject this round.
                    LogIt.warning("refreshBalances: token index failed for vault $name; skipping inject: $e")
                    false
                }
                if (!tokenIndexOk) continue
                val toInject = candidates.mapNotNull { sp ->
                    val outpoint = sp.outpoint ?: return@mapNotNull null
                    if (tokenByOutpoint.containsKey(outpoint))
                    {
                        val (gid, tokenAmount) = tokenByOutpoint[outpoint]
                            ?: run {
                                LogIt.warning("refreshBalances: token UTXO ${outpoint.toHex()} " +
                                    "(vault $name) has no raw group id; leaving it uninjected")
                                return@mapNotNull null
                            }
                        try { sp.priorOutScript = dest.groupedLockingScript(gid, tokenAmount) }
                        catch (e: Throwable)
                        {
                            if (e is CancellationException) throw e
                            LogIt.warning("refreshBalances: cannot rebuild grouped script for " +
                                "${outpoint.toHex()} (vault $name); leaving it uninjected: $e")
                            return@mapNotNull null
                        }
                    }
                    sp.contractId = factory.contractId
                    sp.backingPayDestination = dest
                    sp.dirty = true
                    sp
                }
                if (toInject.isEmpty()) continue
                wallet.injectUnspent(*toInject.toTypedArray())
                val tokenCount = toInject.count { sp ->
                    sp.outpoint?.let { tokenByOutpoint.containsKey(it) } == true
                }
                LogIt.info("refreshBalances: injected ${toInject.size} UTXO(s) " +
                    "($tokenCount token) for vault $name from electrum")
            }

            val out = LinkedHashMap<String, Long>()
            for ((name, vault) in held)
            {
                var nativeSat = 0L
                try
                {
                    val bal = electrum.getBalance(vault.address)
                    nativeSat = bal.confirmed.toLong() + bal.unconfirmed.toLong()
                }
                catch (e: Throwable)
                {
                    if (e is CancellationException) throw e
                    LogIt.warning("refreshBalances: getBalance failed for vault $name: $e")
                    // Keep the last published figure: blanking to 0 on a flake
                    // would show an empty vault and read as a deposit (0 → real,
                    // firing the success animation) when the connection heals.
                    out[name] = _onChainNativeSat.value[name] ?: 0L
                    continue
                }

                // A just-claimed vault whose sweep hasn't landed on-chain yet still reads its pre-claim
                // balance here (electrum's mempool view lags, and the wallet may not have marked the
                // claim's inputs spent). Keep it empty until the on-chain native balance moves off that
                // figure, then trust reality again — so the card holds at 0 from the moment of claim without a bounce.
                val claimingPrior = _claimingVaults.value[name]
                if (claimingPrior != null && nativeSat >= claimingPrior)
                {
                    // Balance hasn't dropped, so the sweep still hasn't landed — keep the vault empty.
                    out[name] = 0L
                }
                else
                {
                    // Balance moved off the pre-claim figure (sweep landed) — the override has done its job; report the real on-chain state.
                    if (claimingPrior != null) _claimingVaults.value = _claimingVaults.value - name
                    out[name] = nativeSat
                }
            }
            _onChainNativeSat.value = out
        }
        finally
        {
            try { wallet.blockchain.net.returnElectrum(electrum) }
                catch (e: Throwable) { LogIt.warning("refreshBalances: returnElectrum failed: $e") }
        }
    }

    /**
     * Subscribe to electrum status notifications for [vaultName]'s address and re-run [refreshBalances]
     * whenever the indexer reports a change (incoming deposit, new confirmation, etc.). Suspends until
     * the caller cancels — wrap the call site in a `LaunchedEffect`/`launch` keyed on the vault you want
     * to watch and cancellation will tear the subscription down via the `finally` block.
     *
     * Only one address subscription is honoured by the underlying `ElectrumClient` at a time, which
     * matches the "only one vault may display its QR at a time" UI invariant — callers should not stack
     * concurrent observations.
     */
    suspend fun observeVaultAddress(vaultName: String) = withContext(wallyApp?.coMiscCtxt ?: Dispatchers.Default) {
        val vault = vaults[vaultName] ?: return@withContext
        val addr = vault.address?.toString() ?: return@withContext
        val electrum = try { wallet.blockchain.net.getElectrum() }
            catch (e: Throwable)
            {
                if (e is CancellationException) throw e
                LogIt.error("observeVaultAddress: no electrum connection for $vaultName: $e")
                return@withContext
            }
        val signals = Channel<Unit>(Channel.CONFLATED)
        try
        {
            electrum.subscribeAddressStatus(addr) { _ -> signals.trySend(Unit) }
            // Refresh once up-front so the user sees the current state immediately.
            refreshBalances()
            for (signal in signals) refreshBalances()
        }
        catch (e: CancellationException) { throw e }
        catch (e: Throwable) { LogIt.warning("observeVaultAddress: subscription for $vaultName ended on error: $e") }
        finally
        {
            signals.close()
            try { electrum.unsubscribeAddress(addr) }
                catch (e: Throwable) { LogIt.warning("observeVaultAddress: unsubscribeAddress failed for $vaultName: $e") }
            try { wallet.blockchain.net.returnElectrum(electrum) }
                catch (e: Throwable) { LogIt.warning("observeVaultAddress: returnElectrum failed for $vaultName: $e") }
        }
    }

    /** Drop a time lock vault from the derived view. Does not delete its on-disk record. */
    fun removeVault(name: String): Boolean
    {
        val prev = vaultsRef.getAndUpdate { if (name in it) LinkedHashMap(it).apply { remove(name) } else it }
        discoveredAtTxCount = -1L  // the held set changed, so the next discovery walks again
        return name in prev
    }

    /** Persist the `(blockHeight, index)` for one vault. */
    fun saveVault(name: String): Boolean
    {
        val vault = vaults[name] ?: return false
        pointFactoryAt(vault)
        factory.save(wallet.wdb)
        return true
    }

    // --- Snapshots -----------------------------------------------------

    /** Snapshot one time lock vault by name; `null` if not held. */
    fun snapshot(name: String): VaultSnapshot? = vaults[name]?.snapshot(buildVaultHistoryIndex())

    /** Snapshot every held time lock vault, in index order. */
    fun snapshotAll(): List<VaultSnapshot>
    {
        // ONE tx-history pass shared by the whole list — balances and claim detection both read from it,
        // instead of each vault re-walking history.
        val index = buildVaultHistoryIndex()
        return vaults.values.map { it.snapshot(index) }
    }

    private class VaultDeposit(val output: iTxOutput, val confirmedHeight: Long)

    /**
     * Everything a vault snapshot needs from wallet tx history, built in one `forEachTx` pass
     * shared by every vault: each time lock vault address's deposit outputs keyed by outpoint
     * hash (`sha256(txIdem ‖ outputIndex)` — the same value a claim input records in
     * [Spendable.outpoint]), plus every outpoint any history input spent. A time lock vault's
     * balance is the sum of its unspent deposits; a time lock vault is claimed when any of its
     * deposit outpoints appear in [spentOutpoints]. Spends are matched by outpoint rather than
     * `input.spendable.priorOutScript` because that script is only populated for outputs the
     * wallet ingested as its own spendables — after a seed-only recovery it stays empty, but
     * the deposit tx itself is in history (it's how the vault was discovered).
     */
    private class VaultHistoryIndex(
        val depositsByAddr: Map<String, Map<String, VaultDeposit>>,
        val spentOutpoints: Set<String>,
    )

    /** Identifies one history generation: a new tx or a confirmation (the synced height moved) invalidates the index. */
    private data class IndexKey(val txCount: Long, val syncedHeight: Long, val vaultAddrs: Set<String>)
    private val indexLock = Mutex("tlvHistoryIndex")
    private var indexCache: Pair<IndexKey, VaultHistoryIndex>? = null

    /** One tx-history walk per generation, shared by every caller (both view models and the poll). */
    private fun buildVaultHistoryIndex(): VaultHistoryIndex
    {
        val vaultAddrs = vaults.values
            .mapNotNullTo(mutableSetOf()) { it.address?.toString() }
        if (vaultAddrs.isEmpty()) return VaultHistoryIndex(emptyMap(), emptySet())
        val key = IndexKey(wallet.getTxCount(), wallet.syncedHeight, vaultAddrs)
        return indexLock.lock {
            indexCache?.takeIf { it.first == key }?.second
                ?: walkVaultHistory(vaultAddrs).also { indexCache = key to it }
        }
    }

    private fun walkVaultHistory(vaultAddrs: Set<String>): VaultHistoryIndex
    {
        val deposits = HashMap<String, HashMap<String, VaultDeposit>>()
        val spent = HashSet<String>()
        wallet.forEachTx { txHist ->
            val tx = txHist.tx
            tx.outputs.forEachIndexed { idx, out ->
                val addr = out.script.address?.toString()
                if (addr != null && addr in vaultAddrs)
                    deposits.getOrPut(addr) { HashMap() }[outpointFor(chain, tx.idem, idx.toLong()).toHex()] =
                        VaultDeposit(out, txHist.confirmedHeight)
            }
            for (inp in tx.inputs)
                inp.spendable.outpoint?.toHex()?.let { spent.add(it) }
            false
        }
        return VaultHistoryIndex(deposits, spent)
    }

    /**
     * Index every time lock vault output in wallet history by its outpoint hash (`sha256(txIdem ‖ outputIndex)`
     * — the same value an input records in [Spendable.outpoint]). Keys identify which prevouts belong to a time
     * lock vault; values are the outputs themselves, so a claim can read the spent amount / token / authority
     * straight from the deposit output it drained. Claim detection resolves the spent prevout through this index
     * rather than `input.spendable.priorOutScript`, because that script is only populated for outputs the wallet
     * ingested as its own spendables. After a seed-only recovery the time lock vault address isn't in the receiving
     * set during the initial sync, so the deposit output is never recorded as a spendable and the claim input's
     * priorOutScript (and the wallet's getTxo) stay empty — but the deposit tx itself is in history
     * (it's how the time lock vault was discovered), so its output is still reachable here by scanning output scripts.
     */
    private fun vaultOutputsByOutpoint(vaultAddrs: Set<String>): Map<String, iTxOutput>
    {
        if (vaultAddrs.isEmpty()) return emptyMap()
        val byOutpoint = HashMap<String, iTxOutput>()
        wallet.forEachTx { txHist ->
            val tx = txHist.tx
            tx.outputs.forEachIndexed { idx, out ->
                if (out.script.address?.toString() in vaultAddrs)
                    byOutpoint[outpointFor(chain, tx.idem, idx.toLong()).toHex()] = out
            }
            false
        }
        return byOutpoint
    }

    /**
     * Time Lock vault addresses whose history contains a claim (sweep): any deposit outpoint
     * later spent by an input. One history pass via [buildVaultHistoryIndex].
     */
    private fun vaultsWithClaimTx(): Set<String>
    {
        val index = buildVaultHistoryIndex()
        return index.depositsByAddr
            .filterValues { deps -> deps.keys.any { it in index.spentOutpoints } }
            .keys
    }

    /**
     * Per-vault transaction history sourced from the wallet's local record of every tx
     * that involves the time lock vault's address. Each tx is unfolded into one [VaultTx]
     * per direction — a tx that both pays into and spends out of the time lock vault
     * (rare for time locks but possible) produces two entries. Sorted newest first by date.
     *
     * The full `wallet.forEachTx` history is walked so claim spends are surfaced: a claim tx
     * pays to the user's main account (not the vault), so we recognise it by matching its
     * inputs' outpoint hashes against this vault's own deposit outputs (see [vaultOutputsByOutpoint]).
     * This survives a seed-only recovery, where the input's `priorOutScript` is empty because the
     * wallet never recorded the time lock vault output as one of its spendables.
     *
     * Returns an empty list when [vaultName] isn't held or no txs have landed yet. The wallet must
     * already know about the txs — both the deposit (funds + change touch the main account) and the
     * claim (it pays the main account) are discovered through normal sync.
     */
    fun vaultTransactions(vaultName: String): List<VaultTx>
    {
        val vault = vaults[vaultName] ?: return emptyList()
        val destAddr = vault.address ?: return emptyList()
        val addrStr = destAddr.toString()
        val vaultOutputs = vaultOutputsByOutpoint(setOf(addrStr))
        val rows = mutableListOf<VaultTx>()
        wallet.forEachTx { txHist ->
            val tx = txHist.tx
            // Deposit side: outputs paying the time lock vault address. A pure-native output adds to the native amount;
            // a token/authority output is surfaced through its own bucket so the row shows the asset that was sent,
            // not the native dust the token output carries.
            var depositAmt = 0L
            val depTokens = LinkedHashMap<GroupId, Long>()
            val depAuths = mutableListOf<GroupInfo>()
            for (out in tx.outputs)
            {
                if (out.script.address?.toString() != addrStr) continue
                val gi = out.groupInfo()
                when
                {
                    gi == null -> depositAmt += out.amount
                    gi.isAuthority() -> depAuths += gi
                    gi.tokenAmount > 0L ->
                        depTokens[gi.groupId] = (depTokens[gi.groupId] ?: 0L) + gi.tokenAmount
                }
            }
            // Claim side: inputs spending this time lock vault's outputs. The spent prevout is resolved
            // through its outpoint hash (see [vaultOutputsByOutpoint]), so the amount / token / authority
            // come from the deposit output the claim drained. Reading the input's own spendable would miss
            // the claim on a recovered wallet, where priorOutScript is empty.
            var claimAmt = 0L
            var claimInputsSat = 0L
            var resolvedInputs = 0
            val claimTokens = LinkedHashMap<GroupId, Long>()
            val claimAuths = mutableListOf<GroupInfo>()
            for (inp in tx.inputs)
            {
                val spent = inp.spendable.outpoint?.toHex()?.let { vaultOutputs[it] } ?: continue
                resolvedInputs++
                claimInputsSat += spent.amount
                val gi = spent.groupInfo()
                when
                {
                    gi == null -> claimAmt += spent.amount
                    gi.isAuthority() -> claimAuths += gi
                    gi.tokenAmount > 0L ->
                        claimTokens[gi.groupId] = (claimTokens[gi.groupId] ?: 0L) + gi.tokenAmount
                }
            }
            // A claim sweeps the whole time lock vault, so when every input drained this time lock vault
            // the miner fee is just what the inputs carried less what the outputs paid out (mirrors [ClaimResult.feeSat]).
            // Only attribute a fee to a pure vault sweep — a mixed tx's fee is not the vault's.
            val claimFeeSat = if (resolvedInputs > 0 && resolvedInputs == tx.inputs.size)
                (claimInputsSat - tx.outputs.sumOf { it.amount }).coerceAtLeast(0L)
            else 0L
            if (depositAmt > 0L || depTokens.isNotEmpty() || depAuths.isNotEmpty()) rows += VaultTx(
                history = txHist,
                kind = VaultTxKind.Deposit,
                amountSat = depositAmt,
                tokenMovements = depTokens,
                authorities = depAuths,
            )
            if (claimAmt > 0L || claimTokens.isNotEmpty() || claimAuths.isNotEmpty()) rows += VaultTx(
                history = txHist,
                kind = VaultTxKind.Claim,
                amountSat = claimAmt,
                tokenMovements = claimTokens,
                authorities = claimAuths,
                feeSat = claimFeeSat,
            )
            false
        }
        return rows.sortedByDescending { it.history.date }
    }

    /**
     * Block-explorer URL for [tx], e.g. `https://explorer.nexa.org/tx/<idem>`.
     * The time lock vault-transactions list opens this when a row is tapped.
     */
    fun explorerUrl(tx: TransactionHistory): String = chain.explorer("/tx/${tx.tx.idem.toHex()}")

    /** The wallet's current synced block height; `null` if the wallet is unsynced. */
    fun currentBlockHeight(): Long?
    {
        val h = wallet.syncedHeight
        return if (h < 0L) null else h
    }

    // --- Send ----------------------------------------------------------

    /**
     * The user's currently-displayed receive address on this account (the same one shown
     * on the receive screen / account pill). Used as the default sweep target by the
     * claim flow so vault funds return to whichever address the user is currently advertising.
     * Reads [Account.currentReceiveObservable] without advancing the HD index.
     *
     * This returns `null` if no receive destination has been allocated yet.
     */
    fun nextReceiveAddress(): PayAddress? =
        account.currentReceiveObservable.value?.address

    /**
     * Re-derive the [vault] guard-free and register it, so the factory's cursor and the wallet's
     * receiving map both hold it. `buildDestination` cannot be used: its creation guard rejects
     * every unlock value at or above the nLockTime threshold, i.e. every timestamp vault.
     */
    private fun pointFactoryAt(vault: TimeLockVaultDestination)
    {
        factory.recoverDestination(vault.unlockValue, vault.hdIndex.toInt())
    }

    /**
     * This is what [claimVault] produces on success — the broadcast tx's idem (hex) plus the actual
     * on-chain payout and fee that is computed *after* the wallet library has selected inputs and
     * sized the fee. UIs that displayed an estimated fee should update from this so the user
     * sees real numbers on the success card.
     */
    data class ClaimResult(
        val txIdem: Hash256,
        val payoutSat: Long,
        val feeSat: Long,
    )

    /**
     * Sweep everything the [vaultName] holds via [TimeLockVault.send], then broadcast the
     * resulting transaction. The native payout goes to [toAddress]; any tokens or authority
     * batons the vault holds come back to this same wallet.
     *
     * The factory is re-pointed at this vault first ([pointFactoryAt]), which also registers the
     * destination so the scope below resolves. `send` itself drains the ENTIRE named vault — the repository no longer walks UTXOs or sizes token/authority
     * outputs:
     *   - Native — one payout output sized to the vault's native balance is passed in;
     *     `send` deducts the real fee from it and folds any other swept native into it.
     *   - Tokens & authorities — auto-swept by `send` to a fresh nexa address of this wallet.
     *     (Batonless authorities and fenced-group coins are left at the vault: re-emitting
     *     either is consensus-invalid, so moving them is impossible without burning them.)
     * `send` also reserves the swept UTXOs while it builds (rolling back on failure) and refuses
     * a premature or unprovable claim.
     *
     * **A claim is scoped to exactly one vault.** Every vault of this wallet shares the factory's contractId,
     * so the claim passes `VaultScope.At(vaultAddr)` and the library gates input selection on that address:
     * only the named vault's UTXOs are spendable by the build, and a sibling is untouched however mature it is.
     * The library also refuses to sign a transaction that reaches outside the scope, so a deficit cannot be
     * covered out of a sibling vault.
     *
     * `TimeLockVault.send` builds and signs but it does **not** push to the network. Broadcasting happens here in two steps:
     *   1. `electrum.sendTx` pushes the transaction **synchronously**, so the node's verdict
     *      is seen here — a rejected sweep (immature lock, fee/relay policy, non-standard)
     *      throws with the node's reason, which the caller surfaces as a user-visible error
     *      instead of a false "claimed". On rejection the built tx is aborted first
     *      ([Bip44Wallet.abortTransaction]) so the time lock vault's UTXOs are released
     *      for a retry instead of staying reserved by a discarded transaction.
     *   2. `wallet.commitWalletTransaction` records the transaction in the wallet's history inline
     *      so it appears in the time lock vault's transaction list at once.
     * If no electrum server is reachable, step 1 is skipped and step 2's P2P broadcast is the best-effort fallback.
     *
     * Throws on failure — unknown vault ([IllegalArgumentException], also thrown by the library when the
     * vault's address is not one it knows), a vault that has not reached its unlock value
     * (`TimeLockImmatureException`), a payout that cannot clear the relay fee (the library's
     * `WalletFeeException` / `TimeLockNativeFundingException`), or any build/broadcast error. The repository never touches the user interface (UI): callers
     * (view models) catch, log, and surface the failure to the user.
     */
    fun claimVault(vaultName: String, toAddress: PayAddress, minConfirms: Int = 0): ClaimResult
    {
        val vault = requireNotNull(vaults[vaultName])
        {
            "Unknown vault '$vaultName' (known: ${vaults.keys})"
        }
        pointFactoryAt(vault)

        val vaultAddr = vault.address ?: error("Vault '$vaultName' has no address")
        // Captured BEFORE the claim — afterwards the UTXOs are spent and the vault reads zero.
        val bal = factory.vaultBalance(vaultAddr)
        val nativeBalance = bal[nativeCoinGroupId(chain)]?.balance ?: 0L
        val hasAnything = bal.any { it.value.balance != 0L }
        // Here it refuse an empty claim up front with a message about THIS time lock vault — otherwise send()
        // would fail deep in tx funding with an error that reads like a wallet problem rather than "nothing to claim".
        check(hasAnything)
        {
            "Vault '$vaultName' has nothing to claim"
        }
        LogIt.info("claimVault: vault=$vaultName native=$nativeBalance " +
            "(syncedHeight=${wallet.syncedHeight}, unlockValue=${vault.unlockValue}, index=${vault.hdIndex})")

        // One native payout output when there is native to pay out; a token-only vault claims through send()'s auto-drain alone,
        //paying the transaction fee from the dust its token UTXOs carry.
        val outputs =
            if (nativeBalance > 0L) arrayOf(txOutputFor(nativeBalance, toAddress))
            else emptyArray()
        // Scope the claim to this vault's address: the no-scope send() the library inherits defaults to
        // every mature vault of the factory. `At` binds to the address rather than the mutable cursor,
        // which another thread's discovery can re-point between here and the build.
        val tx = factory.send(VaultScope.At(vaultAddr), *outputs, minConfirms = minConfirms, title = null, data = null)
        // Synchronous broadcast so a node rejection surfaces here (propagates out → the view model shows the error)
        // rather than being swallowed by the wallet's fire-and-forget broadcast.
        val electrum = runCatching { wallet.blockchain.net.getElectrum() }.getOrNull()
        if (electrum != null)
        {
            try { electrum.sendTx(tx.toByteArray()) }
            catch (e: Throwable)
            {
                // The node refused the claim. send() left the vault's UTXOs reserved for
                // this (now discarded) tx; we have to release them so a retry sees the
                // vault's coins again instead of "nothing spendable" until restart,
                // then rethrow the node's reason.
                runCatching { wallet.abortTransaction(tx) }
                throw e
            }
            finally { runCatching { wallet.blockchain.net.returnElectrum(electrum) } }
        }
        else LogIt.warning("claimVault: no electrum for synchronous broadcast; relying on the wallet's P2P broadcast")
        // Record the claim inline so it appears in the transaction history immediately. Only re-broadcast over
        // P2P when electrum did NOT already send it: a second broadcast of a transaction the node already holds
        // comes back as a "txn-already-in-mempool" rejection, which the wallet will treat as a failure and drop
        // the (perfectly valid, already-broadcast) claim.
        screenOwnsTxFeedback(tx.idem)  // the vault screen plays the claim animation; not the account's receive one too
        wallet.commitWalletTransaction(tx, note = null, broadcast = electrum == null)

        // This is to instantly reflect the claim. It drains the whole time lock vault, so its balance is now zero;
        // publish an empty on-chain entry right away so the time lock vault card drops to 0 the moment the claim is signed
        // instead of waiting for the next electrum poll to notice the spend. Record the pre-claim balance in [_claimingVaults]
        // so the next refreshBalances keeps the time lock vault at zero (rather than restoring electrum's still-stale pre-claim
        // view) until the spend actually shows up on-chain — but only for a time lock vault that HAD native coin: the override
        // clears when the on-chain native balance drops below the recorded figure, and nothing can drop under zero,
        // so recording 0 for a token-only claim would mask the time lock vault's real balance forever.
        if (nativeBalance > 0L)
            _claimingVaults.value = _claimingVaults.value + (vaultName to nativeBalance)
        _onChainNativeSat.value = _onChainNativeSat.value + (vaultName to 0L)
        val inputsSum = tx.inputs.sumOf { it.spendable.amount }
        val outputsSum = tx.outputs.sumOf { it.amount }
        // What the user actually received: the ungrouped outputs paying [toAddress]. The unfiltered
        // total also counts native change, token change, swept token and re-emitted authority outputs.
        val payoutSat = tx.outputs
            .filter { it.groupInfo() == null && it.script.address?.toString() == toAddress.toString() }
            .sumOf { it.amount }
        LogIt.info("claimVault: success tx=${tx.idem.toHex()} " +
            "in=$inputsSum out=$outputsSum payout=$payoutSat fee=${inputsSum - outputsSum}")
        return ClaimResult(
            txIdem = tx.idem,
            payoutSat = payoutSat,
            feeSat = inputsSum - outputsSum,
        )
    }


    // --- Internals -----------------------------------------------------

    private fun TimeLockVaultDestination.snapshot(index: VaultHistoryIndex): VaultSnapshot
    {
        val tip = wallet.syncedHeight
        val unlockHeight = unlockValue
        val unlockable = tip >= 0L && tip >= unlockHeight
        val blocksUntil = if (tip < 0L) null
            else (unlockHeight - tip).coerceAtLeast(0L)
        val addrStr = address?.toString()
        val deposits = addrStr?.let { index.depositsByAddr[it] } ?: emptyMap()
        val hasClaimTx = deposits.keys.any { it in index.spentOutpoints }

        // The balance is reconstructed from the wallet's transaction history (via [buildVaultHistoryIndex])
        // — the same source [vaultTransactions] uses — not from electrum. Electrum proved unreliable for time
        // lock vault addresses: on mainnet it can be unreachable, and even when connected its `getBalance`/`listUnspent`
        // have been observed to report a funded P2ST time lock vault address as empty while a sibling vault reads correctly.
        // History is authoritative locally: a deposit output lives there even when it was never materialised as a tracked
        // UTXO (an external top-up, or any deposit that landed before this install began watching the vault address, e.g.
        // a seed-only recovery), which the spendable-UTXO walk and electrum both miss. Sum the outputs paying the time
        // lock vault address minus those an input later spent; what remains is the current balance, with no electrum dependency.
        var nativeSat = 0L
        var unspentOutputs = 0
        val tokenBalances = LinkedHashMap<GroupId, Long>()
        val authorities = mutableListOf<GroupInfo>()
        var earliest: Long = Long.MAX_VALUE
        for ((op, dep) in deposits)
        {
            if (op in index.spentOutpoints) continue
            unspentOutputs++
            if (dep.confirmedHeight > 0L && dep.confirmedHeight < earliest) earliest = dep.confirmedHeight
            val gi = dep.output.groupInfo()
            if (gi == null)
            {
                nativeSat += dep.output.amount
            }
            else if (gi.isAuthority())
            {
                authorities.add(gi)
            }
            else if (gi.tokenAmount != 0L)
            {
                tokenBalances[gi.groupId] = (tokenBalances[gi.groupId] ?: 0L) + gi.tokenAmount
            }
        }

        return VaultSnapshot(
            name = uiName,
            address = address,
            unlockBlockHeight = unlockHeight,
            isUnlockable = unlockable,
            blocksUntilUnlock = blocksUntil,
            nativeBalanceSat = nativeSat,
            claimFeeSat = if (unspentOutputs == 0) 0L
                          else wallet.feeForSize(
                              CLAIM_TX_OVERHEAD_BYTES + unspentOutputs * VAULT_INPUT_BYTES + CLAIM_OUTPUT_BYTES),
            tokenBalances = tokenBalances,
            authorities = authorities,
            earliestDepositHeight = if (earliest == Long.MAX_VALUE) null else earliest,
            isBalanceLoaded = addrStr != null,
            hasClaimTx = hasClaimTx,
        )
    }
}
