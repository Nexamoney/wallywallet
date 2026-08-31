@file:OptIn(ExperimentalUnsignedTypes::class)

import info.bitcoinunlimited.www.wally.ui.ECDSV_SIG_TAG
import info.bitcoinunlimited.www.wally.ui.ecdsvTaggedDigest
import org.nexa.libnexakotlin.*
import kotlin.test.*

/**
 * Covers the sigalg selection added to the nexid op=sign handlers (ActionPermissionScreens.kt):
 *   - "ecdsv" signs the domain-separated digest SHA256(SHA256(tag) || SHA256(tag) || msg) with tag
 *     "nid1" (spec docs/nexid.md "Data signature computation"), so an ecdsv signature
 *     cannot be replayed onto a transaction sighash.
 *   - default/"ecmsg" path keeps producing the wrapped signMessage form.
 *
 * This test calls the wallet's own ecdsvTaggedDigest so a regression in the construction (e.g.
 * reverting to the bare hash, or changing the tag) fails here rather than silently changing the
 * wire format.
 */
class EcdsvSignatureTest
{
    @Test
    fun testEcdsvTaggedDigestVector()
    {
        initializeLibNexa()

        // the tag and its SHA256 are wallet constants; set them against the spec's declared value
        assertEquals("nid1", ECDSV_SIG_TAG)
        val tagHash = libnexa.sha256(ECDSV_SIG_TAG.encodeToByteArray())
        assertEquals("5969f37ee634de5dfd3cbd2cacb875665610815695d017cf22e7a68ca485b28e", tagHash.toHex())

        // digest for the message "list pass 0001".
        val msg = "list pass 0001".encodeToByteArray()
        val digest = ecdsvTaggedDigest(msg)
        assertEquals("a344369ca25fde5c1e6e3ba3481819cd7de8ff5e35d325e16d8dc85449981c1d", digest.toHex())

        // Domain separation must actually be applied: the signed digest is NOT the bare SHA256(msg)
        // that a transaction-sighash forgery would need.
        assertFalse(digest.contentEquals(libnexa.sha256(msg)))
        // And it is exactly the tagged construction.
        assertTrue(digest.contentEquals(libnexa.sha256(tagHash + tagHash + msg)))
    }

    @Test
    fun testEcdsvSignatureForm()
    {
        initializeLibNexa()
        val secret = libnexa.sha256("EcdsvSignatureTest deterministic secret".encodeToByteArray())
        val msg = "test".encodeToByteArray()

        // what the ecdsv branch produces: a 64-byte Schnorr signature over the tagged digest
        val digest = ecdsvTaggedDigest(msg)
        val sig = libnexa.signHashSchnorr(digest, secret)
        check(sig.size == 64)
        check(libnexa.verifySignedHashSchnorr(digest, libnexa.getPubKey(secret), sig))
        // a signature over a different message's tagged digest must not verify
        check(!libnexa.verifySignedHashSchnorr(ecdsvTaggedDigest("other".encodeToByteArray()), libnexa.getPubKey(secret), sig))

        // default (ecmsg) wrapped form is unchanged and does not cross-verify as a data signature:
        // sizes differ, and even its first 64 bytes must not verify as one.
        val wrapped = libnexa.signMessage(msg, secret)
        check(wrapped != null && wrapped.isNotEmpty())
        check(wrapped.size != 64)
        check(!libnexa.verifySignedHashSchnorr(digest, libnexa.getPubKey(secret), wrapped.copyOfRange(0, 64)))
    }
}
