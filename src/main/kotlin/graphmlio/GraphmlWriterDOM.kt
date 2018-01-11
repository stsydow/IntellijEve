/*
    This code is heavily inspired by
    com.tinkerpop.blueprints.util.io.graphml.GraphMLWriter
    code available at:
    https://github.com/tinkerpop/blueprints
 */

package graphmlio

import com.sun.xml.internal.txw2.output.IndentingXMLStreamWriter
import editor.*
import java.io.File
import javax.xml.XMLConstants
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter

class GraphmlConstants() {
    companion object {
        val ATTR_NAME = "attr.name"
        val ATTR_TYPE = "attr.type"
        val DATA = "data"
        val DEFAULT_GRAPHML_SCHEMA_LOCATION = "http://graphml.graphdrawing.org/xmlns/1.1/graphml.xsd"
        val DIRECTED = "directed"
        val DOUBLE = "double"
        val EDGE = "edge"
        val EDGEDEFAULT = "edgedefault"
        val FOR = "for"
        val GRAPH = "graph"
        val GRAPHML = "graphml"
        val GRAPHML_XMLNS = "http://graphml.graphdrawing.org/xmlns"
        val ID = "id"
        val KEY = "key"
        val NAME = "name"
        val NODE = "node"
        val PORT = "port"
        val SOURCE = "source"
        val SOURCEPORT = "sourceport"
        val STRING = "string"
        val TARGET = "target"
        val TARGETPORT = "targetport"
        val UTF8_ENCODING = "UTF-8"
        val XMLNS = "xmlns"
        val XML_SCHEMA_LOCATION_ATTRIBUTE = "schemaLocation"
        val XML_SCHEMA_NAMESPACE_TAG = "xsi"
        val XML_VERSION = "1.0"
    }
}

class EveamcpConstants() {
    companion object {
        val NODE_BOUNDS_XMAX = "node_bounds_xmax"
        val NODE_BOUNDS_XMAX_NAME = "innerBounds_xmax"
        val NODE_BOUNDS_XMIN = "node_bounds_xmin"
        val NODE_BOUNDS_XMIN_NAME = "innerBounds_xmin"
        val NODE_BOUNDS_YMAX = "node_bounds_ymax"
        val NODE_BOUNDS_YMAX_NAME = "innerBounds_ymax"
        val NODE_BOUNDS_YMIN = "node_bounds_ymin"
        val NODE_BOUNDS_YMIN_NAME = "innerBounds_ymin"
        val NODE_COLOR = "node_color"
        val NODE_COLOR_NAME = "color"
        val NODE_CONTEXT = "node_context"
        val NODE_CONTEXT_NAME = "context"
        val NODE_FILTER = "node_filter"
        val NODE_FILTER_NAME = "filter"
        val NODE_NAME = "node_name"
        val NODE_NAME_NAME = "name"
        val NODE_ORDER = "node_order"
        val NODE_ORDER_NAME = "order"
        val NODE_LINKED_FILE = "linked_file"
        val NODE_LINKED_FILE_NAME = "node_linked_file"
        val NODE_TRANSFORM_SCALE = "node_transform_scale"
        val NODE_TRANSFORM_SCALE_NAME = "transform_scale"
        val NODE_TRANSFORM_X = "node_transform_x"
        val NODE_TRANSFORM_X_NAME = "transform_x_offset"
        val NODE_TRANSFORM_Y = "node_transform_y"
        val NODE_TRANSFORM_Y_NAME = "transform_y_offset"
        val PORT_DIRECTION = "port_direction"
        val PORT_DIRECTION_NAME = "direction"
        val PORT_ID = "port_id"
        val PORT_ID_NAME = "id"
        val PORT_MESSAGETYPE = "port_message_type"
        val PORT_MESSAGETYPE_NAME = "message_type"
    }
}

fun write(path: String, root: Node) {
    val file = File(path)
    file.createNewFile()
    val xmlOutFactory = XMLOutputFactory.newInstance()
    val writeTarget = file.outputStream()
    val writer = IndentingXMLStreamWriter(xmlOutFactory.createXMLStreamWriter(writeTarget, GraphmlConstants.UTF8_ENCODING))
    writer.setIndentStep("    ")

    writeXmlHeaderElement(writer)
    writeGraphmlElement(writer)
    writeKeyInfo(writer)
    writeGraph(writer, root)

    finishUp(writer)
}

private fun writeXmlHeaderElement(writer: XMLStreamWriter) {
    writer.writeStartDocument(GraphmlConstants.UTF8_ENCODING, GraphmlConstants.XML_VERSION)
}

private fun writeGraphmlElement(writer: XMLStreamWriter){
    writer.writeStartElement(GraphmlConstants.GRAPHML) // starting the <graphml> tag
    writer.writeAttribute(
            GraphmlConstants.XMLNS,
            GraphmlConstants.GRAPHML_XMLNS
    )
    writer.writeAttribute(
            XMLConstants.XMLNS_ATTRIBUTE
                    + ":"
                    + GraphmlConstants.XML_SCHEMA_NAMESPACE_TAG,
            XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI
    )
    writer.writeAttribute(
            GraphmlConstants.XML_SCHEMA_NAMESPACE_TAG
                    + ":"
                    + GraphmlConstants.XML_SCHEMA_LOCATION_ATTRIBUTE,
            GraphmlConstants.GRAPHML_XMLNS
                    + " "
                    + GraphmlConstants.DEFAULT_GRAPHML_SCHEMA_LOCATION
    )
}

private fun writeKeyInfo(writer: XMLStreamWriter){
    writeKeyElement(writer,
            EveamcpConstants.NODE_BOUNDS_XMAX, GraphmlConstants.NODE,
            EveamcpConstants.NODE_BOUNDS_XMAX_NAME, GraphmlConstants.DOUBLE)
    writeKeyElement(writer,
            EveamcpConstants.NODE_BOUNDS_XMIN, GraphmlConstants.NODE,
            EveamcpConstants.NODE_BOUNDS_XMIN_NAME, GraphmlConstants.DOUBLE)
    writeKeyElement(writer,
            EveamcpConstants.NODE_BOUNDS_YMAX, GraphmlConstants.NODE,
            EveamcpConstants.NODE_BOUNDS_YMAX_NAME, GraphmlConstants.DOUBLE)
    writeKeyElement(writer,
            EveamcpConstants.NODE_BOUNDS_YMIN, GraphmlConstants.NODE,
            EveamcpConstants.NODE_BOUNDS_YMIN_NAME, GraphmlConstants.DOUBLE)
    writeKeyElement(writer,
            EveamcpConstants.NODE_COLOR, GraphmlConstants.NODE,
            EveamcpConstants.NODE_COLOR_NAME, GraphmlConstants.STRING)
    writeKeyElement(writer,
            EveamcpConstants.NODE_CONTEXT, GraphmlConstants.NODE,
            EveamcpConstants.NODE_CONTEXT_NAME, GraphmlConstants.STRING)
    writeKeyElement(writer,
            EveamcpConstants.NODE_FILTER, GraphmlConstants.NODE,
            EveamcpConstants.NODE_FILTER_NAME, GraphmlConstants.STRING)
    writeKeyElement(writer,
            EveamcpConstants.NODE_NAME, GraphmlConstants.NODE,
            EveamcpConstants.NODE_NAME_NAME, GraphmlConstants.STRING)
    writeKeyElement(writer,
            EveamcpConstants.NODE_ORDER, GraphmlConstants.NODE,
            EveamcpConstants.NODE_ORDER_NAME, GraphmlConstants.STRING)
    writeKeyElement(writer,
            EveamcpConstants.NODE_LINKED_FILE, GraphmlConstants.NODE,
            EveamcpConstants.NODE_LINKED_FILE_NAME, GraphmlConstants.STRING)
    writeKeyElement(writer,
            EveamcpConstants.NODE_TRANSFORM_SCALE, GraphmlConstants.NODE,
            EveamcpConstants.NODE_TRANSFORM_SCALE_NAME, GraphmlConstants.DOUBLE)
    writeKeyElement(writer,
            EveamcpConstants.NODE_TRANSFORM_X, GraphmlConstants.NODE,
            EveamcpConstants.NODE_TRANSFORM_X_NAME, GraphmlConstants.DOUBLE)
    writeKeyElement(writer,
            EveamcpConstants.NODE_TRANSFORM_Y, GraphmlConstants.NODE,
            EveamcpConstants.NODE_TRANSFORM_Y_NAME, GraphmlConstants.DOUBLE)
    writeKeyElement(writer,
            EveamcpConstants.PORT_DIRECTION, GraphmlConstants.PORT,
            EveamcpConstants.PORT_DIRECTION_NAME, GraphmlConstants.STRING)
    writeKeyElement(writer,
            EveamcpConstants.PORT_ID, GraphmlConstants.PORT,
            EveamcpConstants.PORT_ID_NAME, GraphmlConstants.STRING)
    writeKeyElement(writer,
            EveamcpConstants.PORT_MESSAGETYPE, GraphmlConstants.PORT,
            EveamcpConstants.PORT_MESSAGETYPE_NAME, GraphmlConstants.STRING)
}

private fun writeKeyElement(writer: XMLStreamWriter, id: String, forWhat: String, name: String, type: String) {
    writer.writeStartElement(GraphmlConstants.KEY)  // start <key>
        writer.writeAttribute(GraphmlConstants.ID, id)
        writer.writeAttribute(GraphmlConstants.FOR, forWhat)
        writer.writeAttribute(GraphmlConstants.ATTR_NAME, name)
        writer.writeAttribute(GraphmlConstants.ATTR_TYPE, type)
    writer.writeEndElement()    // end <key>
}

private fun writeGraph(writer: XMLStreamWriter, root: Node) {
    writer.writeStartElement(GraphmlConstants.GRAPH)    // start <graph>
        writer.writeAttribute(GraphmlConstants.ID, root.id + ":0")
        writer.writeAttribute(GraphmlConstants.EDGEDEFAULT, GraphmlConstants.DIRECTED)
        // write all children nodes of root node
        root.childNodes.forEach( {iter -> writeNodeElement(writer, iter) })
        // write the top level edges because we will miss them otherwise
        root.childEdges.forEach({ iter -> writeEdgeElement(writer, iter) })
    writer.writeEndElement()    // end <graph>
}

private fun writeNodeElement(writer: XMLStreamWriter, node: Node){
    writer.writeStartElement(GraphmlConstants.NODE) // start <node>
        writer.writeAttribute(GraphmlConstants.ID, node.id)
        // write child nodes
        if (node.childNodes.size > 0)
            writeGraph(writer, node)
        // write ports
        writePortElement(writer, node.in_port)
        node.out_ports.forEach({ iter -> writePortElement(writer, iter) })
        // write data fields
        writeDataElement(writer,
                EveamcpConstants.NODE_BOUNDS_XMAX, node.innerBounds.x_max.toString())
        writeDataElement(writer,
                EveamcpConstants.NODE_BOUNDS_XMIN, node.innerBounds.x_min.toString())
        writeDataElement(writer,
                EveamcpConstants.NODE_BOUNDS_YMAX, node.innerBounds.y_max.toString())
        writeDataElement(writer,
                EveamcpConstants.NODE_BOUNDS_YMIN, node.innerBounds.y_min.toString())
        writeDataElement(writer,
                EveamcpConstants.NODE_COLOR, colorToHexstring(node.color))
        val context = node.getProperty(PropertyType.ContextId)
        if (context != null)
            writeDataElement(writer, EveamcpConstants.NODE_CONTEXT, context)
        val filter = node.getProperty(PropertyType.Filter)
        if (filter != null)
            writeDataElement(writer, EveamcpConstants.NODE_FILTER, filter)
        writeDataElement(writer,
                EveamcpConstants.NODE_NAME, node.name)
        val order = node.getProperty(PropertyType.Order)
        if (order != null)
            writeDataElement(writer, EveamcpConstants.NODE_ORDER, order)
        val linkedFile = node.linkedFilePath
        if (linkedFile != "")
            writeDataElement(writer, EveamcpConstants.NODE_LINKED_FILE, linkedFile)
        writeDataElement(writer,
                EveamcpConstants.NODE_TRANSFORM_SCALE, node.transform.scale.toString())
        writeDataElement(writer,
                EveamcpConstants.NODE_TRANSFORM_X, node.transform.x_offset.toString())
        writeDataElement(writer,
                EveamcpConstants.NODE_TRANSFORM_Y, node.transform.y_offset.toString())
    writer.writeEndElement()    // end <node>
}

private fun writePortElement(writer: XMLStreamWriter, port: Port) {
    writer.writeStartElement(GraphmlConstants.PORT) // start <port>
        writer.writeAttribute(GraphmlConstants.NAME, port.name)
        writeDataElement(writer, EveamcpConstants.PORT_DIRECTION, port.direction.toString())
        writeDataElement(writer, EveamcpConstants.PORT_ID, port.id)
        if (port.message_type.length > 0)
            writeDataElement(writer, EveamcpConstants.PORT_MESSAGETYPE, port.message_type)
    writer.writeEndElement()    // end <port>
}

private fun writeEdgeElement(writer: XMLStreamWriter, edge: Edge) {
    writer.writeStartElement(GraphmlConstants.EDGE) // start <edge>
        writer.writeAttribute(GraphmlConstants.SOURCE, edge.source.parent!!.id)
        writer.writeAttribute(GraphmlConstants.TARGET, edge.target.parent!!.id)
        writer.writeAttribute(GraphmlConstants.SOURCEPORT, edge.source.id)
        writer.writeAttribute(GraphmlConstants.TARGETPORT, edge.target.id)
    writer.writeEndElement()    // end <edge>
}

private fun writeDataElement(writer: XMLStreamWriter, id: String, value: String) {
    writer.writeStartElement(GraphmlConstants.DATA) // start <data>
        writer.writeAttribute(GraphmlConstants.KEY, id)
        writer.writeCharacters(value)
    writer.writeEndElement()    // end <data>
}

private fun finishUp(writer: XMLStreamWriter){
    writer.writeEndElement()    // close the <graphml> element
    writer.writeEndDocument()
    writer.flush()
    writer.close()
}