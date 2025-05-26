package ui

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.*
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.GetLog
import org.nexa.threads.millisleep
import kotlin.test.Test

private val LogIt = GetLog("wally.test")

val NiftyArtTandC_1_0 = """NiftyArt Terms and Conditions V1.0
    
 Definitions:
    NiftyArt NFT: A unique piece of data that is described by a record in a blockchain.  This record contains a probabilistically unique identifier of a data file containing and/or describing the represented Work.
    Owner/Ownership: (of the NiftyArt NFT)  Modifying, destroying, or transferring an NiftyArt NFT record (called a transaction) is defined by rules specified by the blockchain and by each NiftyArt NFT record.  Successfully following these rules requires pieces of data that are provided as part of the transaction.  Ownership of an NiftyArt NFT is defined as possessing the ability to provide the data required to confirm a transaction on the blockchain.  Proving ownership occurs solely by one of two mechanisms.  First, by executing and confirming said transaction on the blockchain.  This confirms Ownership for the exact duration of the transaction.  Second, by producing a unique transaction upon demand with the correct required data but that is not admissible onto the blockchain for some other reason.  This confirms Ownership until the NFT blockchain record is modified or destroyed.  Every use of the word 'Owner' in these Terms and Conditions implies cryptographic verification of ownership as described.
    Creator: (of the NiftyArt NFT) The entity that originally created the NiftyArt NFT on the blockchain.
    Work: The artwork referenced by this NiftyArt NFT.  The Work may be fully embodied within files named 'public' and/or 'private'.  Additionally, the Work may include a physical and/or external components as specified by the 'info' field within the 'info.json' file.
    Owner Work:  The portion of the Work visible only by the Owner, as described below.
    Public Work:  The portion of the Work visible to anyone, as authorized by the Owner and these Terms and Conditions.
    Service: A third party entity that facilitates the use, inclusion, participation, management, storage, display or trade of NiftyArt NFTs.
   
 Inseparability of Work from the NiftyArt NFT:
    Ownership of this NiftyArt NFT confers all licenses to the Work described herein.  Transfer of this NiftyArt NFT transfers the same.  The licenses described below are forever conferred by and inseparable from Ownership of this NiftyArt NFT.
    The Owner does NOT have the right to create or enter into any contract, agreement or document, electronic, physical or otherwise that changes or overrides the NiftyArt NFT's licenses to the Work as described herein.
 
Visibility of the Work:
   Third parties may reproduce and publish the media files located within this NiftyArt NFT prefixed by 'cardf' and 'cardb' (with various suffixes and extensions) and the data within the 'info.json' file within the context of identifying this NiftyArt NFT.  The fair use of the title and author of a book shall act as precedent for when this data may be published.
   If media files or directories prefixed by 'owner' (with various suffixes and extensions) exist, this data is the Owner Work.   No Service may use, copy, display, or publish this file except to a proven current Owner.  Services that manage, store, use, or trade NiftyArt NFTs may store this file on behalf of and for sole use by the Owner.
   If media files or directories prefixed by 'public' (with various suffixes and extensions) exist, this data is the Public Work.  'cardf' and 'cardb' prefixed files are also within the Public Work.  Control of the Public Work is specific to each NFT and described in the following sections.

Creator Copyright Ownership:
  The Creator of the NiftyArt NFT asserts that they had sole ownership of exclusive copyright to the Work, had the right to transfer that ownership, and that the creation of this NiftyArt NFT inseparably binds the copyright and/or licenses described herein to Ownership of this NiftyArt NFT.
  
"""

val LicenseToPublish = "The Owner of the NiftyArt NFT that includes this notice is hereby granted an unlimited, worldwide license to use, copy, display and publish the Public Work described by the NiftyArt NFT for personal or commercial use.  This right may not be delegated.  This right is not exclusive.  Upon transfer of this NiftyArt NFT, the previous Owner loses all rights to reproduce and publish the public Work.  The public Work must be removed from all locations under the control of the previous Owner, including but not limited to web sites, software, and mobile/tablet applications."

val beaverNFT = "Nexa Beef Beaver is the fearless Beaver who has no issues starting beefs with other coins and thinks Nexa is the best. Keeps stacking it under his dams he’s building and is not selling.  Lives all across Canada, and knows Canada is the best country. Believes in self-sufficiency, prepping, hunting, camping, swimming and outdoors life. Happy to tell the world about his domain."

@OptIn(ExperimentalTestApi::class)
class UtilsTest:WallyUiTestBase()
{
    val cs = ChainSelector.NEXA

    @Test
    fun htmlOrTextDisplayTest()
    {
        runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner
            {
                override val viewModelStore = ViewModelStore()
            }

            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {

                    Column(modifier = Modifier.fillMaxWidth(0.95f).verticalScroll(rememberScrollState())) {
                        Text("THIS IS A MANUAL VISUAL TEST.\nScroll down and check that the following text looks ok.", Modifier.background(Color.Red))
                        Text("Text test:")
                        impreciseDisplayHtml("test of normal text, includingline breaks. HERE\n and again HERE\n and HERE\ndone.", textAlign = TextAlign.Justify)
                        Spacer(Modifier.height(8.dp).fillMaxWidth().background(Color.Blue))
                        Text("HTML Test:")
                        impreciseDisplayHtml("<body><p>html test of normal text CR HERE\n and html para end HERE</p>, including CR HERE\n p and line breaks.</body>", textAlign = TextAlign.Justify)

                        Spacer(Modifier.height(8.dp).fillMaxWidth().background(Color.Blue))
                        Text("Typical Legalese:")
                        impreciseDisplayHtml(NiftyArtTandC_1_0 + LicenseToPublish, textAlign = TextAlign.Justify)

                        Spacer(Modifier.height(8.dp).fillMaxWidth().background(Color.Blue))
                        Text( "Example NFT info:")
                        impreciseDisplayHtml(beaverNFT, textAlign = TextAlign.Justify)
                        Spacer(Modifier.height(8.dp).fillMaxWidth().background(Color.Blue))
                    }
                }
            }
            settle()
            millisleep(15000U)
            settle()
        }
    }
}
