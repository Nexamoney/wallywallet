@file:OptIn(ExperimentalUnsignedTypes::class)

import info.bitcoinunlimited.www.wally.EfficientFile
import info.bitcoinunlimited.www.wally.nftCardFront
import info.bitcoinunlimited.www.wally.nftData
import info.bitcoinunlimited.www.wally.zipForeach
import io.ktor.http.ContentDisposition.Companion.File
import okio.*
import okio.Path.Companion.toPath
import org.nexa.libnexakotlin.*
import kotlin.test.*

class CommonTest
{
    @Test
    fun testManyNfts()
    {
        initializeLibNexa()
        val ProjectDir = "../" // Note that this is not correct on macos/ios
        /* for debugging specific zip files
        if (true)
        {
            val nftyZip = FileSystem.SYSTEM.source("../exampleNft/cacf3d958161a925c28a970d3c40deec1a3fe06796fe1b4a7b68f377cdb900004ca0f7b7cde2254e62907c1656cf997091c7a92dc98635a372c51b98d4daaf9a.zip".toPath()).buffer()
            zipForeach(nftyZip) { info, data ->
                println("${info.fileName}: ${info.uncompressedSize}")
                false
            }
        }
         */
        try
        {

            val fname = (ProjectDir + "/exampleNft/tr9v70v4s9s6jfwz32ts60zqmmkp50lqv7t0ux620d50xa7dhyqqqx9r0ktuypj5t6sj7gplp7tl50lwr893e9y6vf33gmn4tmt3agjdr7nd0lhk.zip").toPath()
            println("file: ${fname}")
            val nftyZip = FileSystem.SYSTEM.source(fname).buffer()
            zipForeach(nftyZip) { info, data ->
                println("${info.fileName}: ${info.uncompressedSize}")
                if (info.fileName == "cardf.jpg")
                {
                    check(libnexa.sha256(data!!.readByteArray()) contentEquals "6338e378724ac0822921b8b2ef791457b2bcb69ba153492fdb10276c22e134db".fromHex())
                }
                else if (info.fileName == "public.jpg")
                {
                    check(libnexa.sha256(data!!.readByteArray()) contentEquals "53e9fc54356b3e276f9009941207f2b2f294ea4fe0abec9189a1d43f30efa269".fromHex())
                }
                else if (info.fileName == "info.json")
                {
                    check(libnexa.sha256(data!!.readByteArray()) contentEquals "b200e71bdedd7b81eb8b2f95e402fced7e0dde2470a593db7e09588d8a6aafa8".fromHex())
                }
                else
                {
                    check(false) // should be no other files
                }

                false
            }


            if (true)
            {
                for (file in FileSystem.SYSTEM.list((ProjectDir + "/exampleNft").toPath()))
                {
                    val nftyZip2 = FileSystem.SYSTEM.source(file).buffer()
                    println("file: ${file.name}")
                    zipForeach(nftyZip2) { info, datastrm: BufferedSource? ->
                        val d = datastrm?.readByteArray() ?: byteArrayOf()
                        println("${info.fileName}: ${info.uncompressedSize} actual size: ${d.size}")
                        false
                    }
                }
            }
        }
        catch(e: FileNotFoundException)
        {
            println("UnitTests.kt: testManyNfts [You did not provide the correct path to run this test]")
        }
    }


    @Test
    fun testParseZip()
    {
        // This is a small example NFT zip file
        val nftyZip = "504b03040a00000000005c8333582f3d08f59e0100009e01000009001c0063617264662e706e6755540900031fe9aa65b2e8aa6575780b000104e803000004e803000089504e470d0a1a0a0000000d494844520000000a0000000a08060000008d32cfbd000001654944415418d335d0bf4b94711cc0f1f7e77bcf43c705a6965acf1d1ae2a4632e2d8f939b2e6781b438044a04de2e82d0e6a8a0706eb5393cfe0fe720482987884a461019fa44433e773eddf3f3e3a0bde7d7f416ee8bfd72550a2f6a6a8fbb0268f265378f0fd78acec50e80b943237578e4697ee92225544a6876e96a9c7b3747e53a80c4fef32af62b4fac49f2a889e4dba880f206d27ea28b65b25661c682bc661eccb27fda4babe53031d64173d83d99a4e2f431dc3740fcfbdda2111257cd008b1f3ed23cd8238acafcb91ee1ecf833ab1b1ea67b942c94090342d23e66e5fd34a5ee216ceb2b3dc513ccc30aafa75e12fe3c24fb6790c47fd6d05cdc8825447f61c927b40d9d708eb4f398ecc72649601a925c395504cff7952050061d431608df4fa118db74894d7ac38c00c4574e5d85f9b00de7cd94b46d187a52c40a0b2481d9aabc3d5f90ffc323dfa96a4c2d0bc44dae2dd2bf85461a98f5a7b3df76006e01b96e9d104f2eba150000000049454e44ae426082504b03041400000008000f878757428b8bb389000000b300000009001c00696e666f2e6a736f6e55540900038e3f72651ae8aa6575780b000104e803000004e8030000abe6525050cacb4c2ba90c4b2d52b25232d23350d20189956496e4a42a59292805e766e6a456820515401289a52519f9452019c7bc94a2d4728547d3f640b464a75696e717a51403e5a2151462c162c98925a9e9f9459540a3fddc4220ea120b0a4a8b32412640f8997969f9701e482025b1245149c14aa1ba162c9f93999c9a570c768c12572d1700504b01021e030a00000000005c8333582f3d08f59e0100009e010000090018000000000000000000b4810000000063617264662e706e6755540500031fe9aa6575780b000104e803000004e8030000504b01021e031400000008000f878757428b8bb389000000b3000000090018000000000001000000b481e1010000696e666f2e6a736f6e55540500038e3f726575780b000104e803000004e8030000504b050600000000020002009e000000ad0200000000".fromHex()
        // This is the image file it contains
        val cardfPng = "89504e470d0a1a0a0000000d494844520000000a0000000a08060000008d32cfbd000001654944415418d335d0bf4b94711cc0f1f7e77bcf43c705a6965acf1d1ae2a4632e2d8f939b2e6781b438044a04de2e82d0e6a8a0706eb5393cfe0fe720482987884a461019fa44433e773eddf3f3e3a0bde7d7f416ee8bfd72550a2f6a6a8fbb0268f265378f0fd78acec50e80b943237578e4697ee92225544a6876e96a9c7b3747e53a80c4fef32af62b4fac49f2a889e4dba880f206d27ea28b65b25661c682bc661eccb27fda4babe53031d64173d83d99a4e2f431dc3740fcfbdda2111257cd008b1f3ed23cd8238acafcb91ee1ecf833ab1b1ea67b942c94090342d23e66e5fd34a5ee216ceb2b3dc513ccc30aafa75e12fe3c24fb6790c47fd6d05cdc8825447f61c927b40d9d708eb4f398ecc72649601a925c395504cff7952050061d431608df4fa118db74894d7ac38c00c4574e5d85f9b00de7cd94b46d187a52c40a0b2481d9aabc3d5f90ffc323dfa96a4c2d0bc44dae2dd2bf85461a98f5a7b3df76006e01b96e9d104f2eba150000000049454e44ae426082".fromHex()

        val infoJson = """{
  "niftyVer":"2.0",
  "title": "Smiley",
   
  "author": "Andrew ▼",
  "keywords": [  ],
  "category":"NFT",
  "appuri": "",
  "info": "",
  
  "data" : {},
  "license": ""
}
"""

        val b = Buffer()
        b.write(nftyZip)
        zipForeach(b) { info, data ->
            if (info.fileName == "cardf.png")
            {
                check(data!!.readByteArray() contentEquals  cardfPng)
            }
            if (info.fileName == "info.json")
            {
                val json = data!!.readByteArray().decodeUtf8()
                check(json == infoJson)
            }
            false
        }
    }


    @Test
    fun testParseNftZip()
    {
        // This is a small example NFT zip file
        val nftyZip = "504b03040a00000000005c8333582f3d08f59e0100009e01000009001c0063617264662e706e6755540900031fe9aa65b2e8aa6575780b000104e803000004e803000089504e470d0a1a0a0000000d494844520000000a0000000a08060000008d32cfbd000001654944415418d335d0bf4b94711cc0f1f7e77bcf43c705a6965acf1d1ae2a4632e2d8f939b2e6781b438044a04de2e82d0e6a8a0706eb5393cfe0fe720482987884a461019fa44433e773eddf3f3e3a0bde7d7f416ee8bfd72550a2f6a6a8fbb0268f265378f0fd78acec50e80b943237578e4697ee92225544a6876e96a9c7b3747e53a80c4fef32af62b4fac49f2a889e4dba880f206d27ea28b65b25661c682bc661eccb27fda4babe53031d64173d83d99a4e2f431dc3740fcfbdda2111257cd008b1f3ed23cd8238acafcb91ee1ecf833ab1b1ea67b942c94090342d23e66e5fd34a5ee216ceb2b3dc513ccc30aafa75e12fe3c24fb6790c47fd6d05cdc8825447f61c927b40d9d708eb4f398ecc72649601a925c395504cff7952050061d431608df4fa118db74894d7ac38c00c4574e5d85f9b00de7cd94b46d187a52c40a0b2481d9aabc3d5f90ffc323dfa96a4c2d0bc44dae2dd2bf85461a98f5a7b3df76006e01b96e9d104f2eba150000000049454e44ae426082504b03041400000008000f878757428b8bb389000000b300000009001c00696e666f2e6a736f6e55540900038e3f72651ae8aa6575780b000104e803000004e8030000abe6525050cacb4c2ba90c4b2d52b25232d23350d20189956496e4a42a59292805e766e6a456820515401289a52519f9452019c7bc94a2d4728547d3f640b464a75696e717a51403e5a2151462c162c98925a9e9f9459540a3fddc4220ea120b0a4a8b32412640f8997969f9701e482025b1245149c14aa1ba162c9f93999c9a570c768c12572d1700504b01021e030a00000000005c8333582f3d08f59e0100009e010000090018000000000000000000b4810000000063617264662e706e6755540500031fe9aa6575780b000104e803000004e8030000504b01021e031400000008000f878757428b8bb389000000b3000000090018000000000001000000b481e1010000696e666f2e6a736f6e55540500038e3f726575780b000104e803000004e8030000504b050600000000020002009e000000ad0200000000".fromHex()
        val ef = EfficientFile(nftyZip)
        // This is the image file it contains
        val cardfPng = "89504e470d0a1a0a0000000d494844520000000a0000000a08060000008d32cfbd000001654944415418d335d0bf4b94711cc0f1f7e77bcf43c705a6965acf1d1ae2a4632e2d8f939b2e6781b438044a04de2e82d0e6a8a0706eb5393cfe0fe720482987884a461019fa44433e773eddf3f3e3a0bde7d7f416ee8bfd72550a2f6a6a8fbb0268f265378f0fd78acec50e80b943237578e4697ee92225544a6876e96a9c7b3747e53a80c4fef32af62b4fac49f2a889e4dba880f206d27ea28b65b25661c682bc661eccb27fda4babe53031d64173d83d99a4e2f431dc3740fcfbdda2111257cd008b1f3ed23cd8238acafcb91ee1ecf833ab1b1ea67b942c94090342d23e66e5fd34a5ee216ceb2b3dc513ccc30aafa75e12fe3c24fb6790c47fd6d05cdc8825447f61c927b40d9d708eb4f398ecc72649601a925c395504cff7952050061d431608df4fa118db74894d7ac38c00c4574e5d85f9b00de7cd94b46d187a52c40a0b2481d9aabc3d5f90ffc323dfa96a4c2d0bc44dae2dd2bf85461a98f5a7b3df76006e01b96e9d104f2eba150000000049454e44ae426082".fromHex()
        val (name, contents) = nftCardFront(ef)
        assert(name == "cardf.png")
        assert(contents contentEquals cardfPng)

        val nftData = nftData(ef)
        assert(nftData != null)
        if (nftData != null)
        {
            assert(nftData.niftyVer == "2.0")
            assert(nftData.author == "Andrew ▼")
            assert(nftData.appuri == "")
            assert(nftData.info == "")
            assert(nftData.license == "")
        }
    }


}
