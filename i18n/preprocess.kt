// kotlinc hello.kt -include-runtime -d hello.jar
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.XMLReader
import org.xml.sax.helpers.DefaultHandler
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.text.*
import kotlin.io.*
import java.io.*
import org.w3c.dom.*

var packageName = "info.bitcoinunlimited.www.wally"

var langs = listOf("ca","de","es","et","hi","in","it","nb","no","round","sl","sv","tr","ko")

fun readStringsXml(filename: String):MutableMap<String,String>
{
    val istream = File(filename).inputStream()
    val builderFactory = DocumentBuilderFactory.newInstance()
    val docBuilder = builderFactory.newDocumentBuilder()
    val doc = docBuilder.parse(istream)
    val res:Node = doc.getElementsByTagName("resources").item(0)
    val strings = res.getChildNodes()
    val xlat = mutableMapOf<String,String>()
    for (idx in 0 until strings.length)
    {
        val s = strings.item(idx)
        //println(s.localName)
        //println(s.nodeName)
        //println(s.baseURI)
        if (s.nodeName == "string")
        {
            val attrs = s.attributes
            val name = attrs.getNamedItem("name").textContent
            val v = s.textContent
            val xv = v.replace("\\n","\n").replace("\\'","'").replace("\\\"","\"").replace("\\r","\r")
            xlat[name] = xv
            println("$name -> $xv")
        }
    }

    return xlat
}

fun writeXlatBin(localeCode: String, keys:List<String>, langXlat:Map<String,String>, defaultXlat:Map<String,String>)
{
    val vs = keys.map { langXlat[it]?.toByteArray() ?: {
        println("WARNING: ${localeCode} is missing a translation for $it")
        var tmp = defaultXlat[it]
        tmp = tmp!!.replace("\\n", "\n")
        println(tmp)
        tmp.encodeToByteArray()
        }()
    }

    val out = File("strings_${localeCode}.bin").outputStream()
    for (v in vs)
    {
        out.write(v)
        out.write(0)
    }
}

fun main()
{
    val baseXlat = readStringsXml("res/values/strings.xml")
    val baseKeys = baseXlat.keys.toList().sortedBy { it }

    writeXlatBin("en", baseKeys, baseXlat, baseXlat)

    for (lang in langs)
    {
        val xlat = readStringsXml("res/values-$lang/strings.xml")
        writeXlatBin(lang, baseKeys, xlat, baseXlat)
    }

    val ktprog = StringBuilder()

    ktprog.append("package $packageName\n")
    ktprog.append("object S { \n")
    for (idx in 0 until baseKeys.size)
    {
        ktprog.append("val ${baseKeys[idx]}:Int = $idx // ${baseXlat[baseKeys[idx]]!!.replace('\n',' ')}\n")
    }
    ktprog.append("}")

    File("strings.kt").writeText(ktprog.toString())

}
