package graphmlio

import org.apache.commons.io.FileUtils
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.net.URL
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

val XSD_URL = URL("http://graphml.graphdrawing.org/xmlns/1.1/graphml.xsd")

fun validate(graphml:File) : Boolean{
    // create temporary file to download the graphml.xsd
    val xsdFile = createTempFile("tmp.xsd", null, null)
    xsdFile.deleteOnExit()
    FileUtils.copyURLToFile(XSD_URL, xsdFile)
    // validate the given file
    try {
        val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        val schema = factory.newSchema(xsdFile)
        val validator = schema.newValidator()
        validator.validate(StreamSource(graphml))
    } catch (e: IOException) {
        println("IOException on validating graphml file: " + e.message)
        return false
    } catch (e: SAXException) {
        println("SAXException on validating graphml file: " + e.message)
        return false
    }
    return true
}