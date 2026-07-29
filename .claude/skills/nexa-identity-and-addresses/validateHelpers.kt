/*
 * validateHelpers.kt — drop-in address-validation guards for app code.
 *
 * IMPORTANT: these are APP-LEVEL helpers, not libnexakotlin APIs. The library has no
 * `requireP2PKT` / `requireLooksLikeNexaAddress` function — these wrap the real PayAddress API
 * (PayAddress(String), .type, PayAddressType.isValid(), lockingScript()). Copy into your app and
 * adapt the exception messages to your error model.
 *
 * See addressTypesTable.md for the PayAddressType behavior these rely on, and SKILL.md for the
 * P2PKH-identity vs P2PKT-payout distinction.
 */

import org.nexa.libnexakotlin.PayAddress
import org.nexa.libnexakotlin.PayAddressType
import org.nexa.libnexakotlin.ChainSelector

class AddressValidationException(msg: String) : IllegalArgumentException(msg)

/**
 * Parse and sanity-check an address string. Returns the parsed PayAddress, or throws
 * AddressValidationException with a clear message. Optionally enforce the expected chain.
 *
 * "Looks like a Nexa address" = parses, has a valid+payable type, and (if given) is on
 * `expectedChain`. It does NOT prove the address is funded or that you control it.
 */
fun requireLooksLikeNexaAddress(address: String, expectedChain: ChainSelector? = null): PayAddress {
    if (address.isBlank()) throw AddressValidationException("address is blank")
    val addr = try {
        PayAddress(address)                       // throws PayAddressDecodeException / PayAddressBlankException
    } catch (e: Exception) {
        throw AddressValidationException("not a decodable address: ${e.message}")
    }
    if (!addr.type.isValid())
        throw AddressValidationException("address has an invalid type byte (${addr.type.v})")
    // Reject the non-payable / non-address types up front (NONE, P2PUBKEY, and GROUP — a token type).
    when (addr.type) {
        PayAddressType.NONE, PayAddressType.P2PUBKEY ->
            throw AddressValidationException("address type ${addr.type} is not a payable destination")
        PayAddressType.GROUP ->
            throw AddressValidationException("this is a token GROUP id, not a payable address")
        else -> { /* P2PKH, P2SH, TEMPLATE/P2PKT are fine */ }
    }
    if (expectedChain != null && addr.chainSelector != expectedChain)
        throw AddressValidationException("address is for ${addr.chainSelector}, expected $expectedChain")
    // Final proof it can produce a locking script (catches malformed template data, etc.).
    try { addr.lockingScript() } catch (e: Exception) {
        throw AddressValidationException("address cannot produce a locking script: ${e.message}")
    }
    return addr
}

/**
 * Require a P2PKT (pay-to-pub-key-template) PAYOUT address. Use this where a contract or payout
 * flow needs a template address it can read args off of on-chain — NOT a P2PKH identity address.
 *
 * Note: a parsed template address reports type TEMPLATE (byte 19); P2PKT shares that byte value,
 * so this accepts either by comparing the type byte.
 */
fun requireP2PKT(address: String, expectedChain: ChainSelector? = null): PayAddress {
    val addr = requireLooksLikeNexaAddress(address, expectedChain)
    return requireP2PKT(addr)
}

fun requireP2PKT(addr: PayAddress): PayAddress {
    if (addr.type.v != PayAddressType.P2PKT.v && addr.type.v != PayAddressType.TEMPLATE.v)
        throw AddressValidationException(
            "expected a P2PKT/template payout address, got ${addr.type}. " +
            "A P2PKH identity address cannot be read as a template (see addressTypesTable.md).")
    return addr
}

/** Require a P2PKH IDENTITY address (the stable, signature-verified auth identity — not a payout). */
fun requireP2PKH(address: String, expectedChain: ChainSelector? = null): PayAddress {
    val addr = requireLooksLikeNexaAddress(address, expectedChain)
    if (addr.type != PayAddressType.P2PKH)
        throw AddressValidationException("expected a P2PKH identity address, got ${addr.type}")
    return addr
}