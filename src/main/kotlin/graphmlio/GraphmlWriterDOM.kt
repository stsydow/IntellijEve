/*
    This code is heavily inspired by
    com.tinkerpop.blueprints.util.io.graphml.GraphMLWriter
    code available at:
    https://github.com/tinkerpop/blueprints
 */

package graphmlio

import editor.*
import java.io.File
import javax.xml.XMLConstants
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter

const val W3C_XML_SCHEMA_INSTANCE_NS_URI = "http://www.w3.org/2001/XMLSchema-instance"

class GraphmlConstants {
    companion object {
        const val ATTR_NAME = "attr.structName"
        const val ATTR_TYPE = "attr.type"
        const val DATA = "data"
        const val DEFAULT_GRAPHML_SCHEMA_LOCATION = "http://graphml.graphdrawing.org/xmlns/1.1/graphml.xsd"
        const val DIRECTED = "directed"
        const val DOUBLE = "double"
        const val EDGE = "edge"
        const val EDGE_DEFAULT = "edgedefault"
        const val FOR = "for"
        const val GRAPH = "graph"
        const val GRAPHML = "graphml"
        const val GRAPHML_XMLNS = "http://graphml.graphdrawing.org/xmlns"
        const val ID = "id"
        const val KEY = "key"
        const val NAME = "structName"
        const val NODE = "node"
        const val PORT = "port"
        const val SOURCE = "source"
        const val SOURCE_PORT = "sourceport"
        const val STRING = "string"
        const val TARGET = "target"
        const val TARGET_PORT = "targetport"
        const val UTF8_ENCODING = "UTF-8"
        const val XMLNS = "xmlns"
        const val XML_SCHEMA_LOCATION_ATTRIBUTE = "schemaLocation"
        const val XML_SCHEMA_NAMESPACE_TAG = "xsi"
        const val XML_VERSION = "1.0"
    }
}

class EveamcpConstants {
    companion object {
        const val NODE_BOUNDS_XMAX = "node_bounds_xmax"
        const val NODE_BOUNDS_XMAX_NAME = "innerBounds_xmax"
        const val NODE_BOUNDS_XMIN = "node_bounds_xmin"
        const val NODE_BOUNDS_XMIN_NAME = "innerBounds_xmin"
        const val NODE_BOUNDS_YMAX = "node_bounds_ymax"
        const val NODE_BOUNDS_YMAX_NAME = "innerBounds_ymax"
        const val NODE_BOUNDS_YMIN = "node_bounds_ymin"
        const val NODE_BOUNDS_YMIN_NAME = "innerBounds_ymin"
        const val NODE_COLOR = "node_color"
        const val NODE_COLOR_NAME = "color"
        const val NODE_CONTEXT = "node_context"
        const val NODE_CONTEXT_NAME = "context"
        const val NODE_FILTER = "node_filter"
        const val NODE_FILTER_NAME = "filter"
        const val NODE_NAME = "node_name"
        const val NODE_NAME_NAME = "structName"
        const val NODE_ORDER = "node_order"
        const val NODE_ORDER_NAME = "order"
        const val NODE_FILE = "node_file"
        const val NODE_FILE_NAME = "file"
        const val NODE_TRANSFORM_SCALE = "node_transform_scale"
        const val NODE_TRANSFORM_SCALE_NAME = "transform_scale"
        const val NODE_TRANSFORM_X = "node_transform_x"
        const val NODE_TRANSFORM_X_NAME = "transform_x_offset"
        const val NODE_TRANSFORM_Y = "node_transform_y"
        const val NODE_TRANSFORM_Y_NAME = "transform_y_offset"
        const val PORT_DIRECTION = "port_direction"
        const val PORT_DIRECTION_NAME = "direction"
        const val PORT_ID = "port_id"
        const val PORT_ID_NAME = "id"
        const val PORT_MESSAGE_TYPE = "port_message_type"
        const val PORT_MESSAGE_TYPE_NAME = "message_type"
    }
}

const val SPACES_PER_TAB = 4
var depth: Int = 0

fun write(path: String, root: Node) {
    val file = File(path)
    file.createNewFile()
    val xmlOutFactory = XMLOutputFactory.newInstance()
    val writeTarget = file.outputStream()
    val writer = xmlOutFactory.createXMLStreamWriter(writeTarget, GraphmlConstants.UTF8_ENCODING)

    writeXmlHeaderElement(writer)
    writeGraphmlElement(writer)
    writeKeyInfo(writer)
    writeGraph(writer, root)

    finishUp(writer)
}

private fun writeXmlHeaderElement(writer: XMLStreamWriter) {
    writer.writeStartDocument(GraphmlConstants.UTF8_ENCODING, GraphmlConstants.XML_VERSION)
    writeNewline(writer)
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
                    W3C_XML_SCHEMA_INSTANCE_NS_URI
    )
    writer.writeAttribute(
            GraphmlConstants.XML_SCHEMA_NAMESPACE_TAG
                    + ":"
                    + GraphmlConstants.XML_SCHEMA_LOCATION_ATTRIBUTE,
            GraphmlConstants.GRAPHML_XMLNS
                    + " "
                    + GraphmlConstants.DEFAULT_GRAPHML_SCHEMA_LOCATION
    )
    writeNewline(writer)
}

private fun writeKeyInfo(writer: XMLStreamWriter){
    writeKeyElement(writer,
            EveamcpConstants.NODE_BOUNDS_XMAX, GraphmlConstants.NODE,
            EveamcpConstants.NODE_BOUNDS_XMAX_NAME, GraphmlConstants.DOUBLE)
    writeNewline(writer)
    writeKeyElement(writer,
            EveamcpConstants.NODE_BOUNDS_XMIN, GraphmlConstants.NODE,
            EveamcpConstants.NODE_BOUNDS_XMIN_NAME, GraphmlConstants.DOUBLE)
    writeNewline(writer)
    writeKeyElement(writer,
            EveamcpConstants.NODE_BOUNDS_YMAX, GraphmlConstants.NODE,
            EveamcpConstants.NODE_BOUNDS_YMAX_NAME, GraphmlConstants.DOUBLE)
    writeNewline(writer)
    writeKeyElement(writer,
            EveamcpConstants.NODE_BOUNDS_YMIN, GraphmlConstants.NODE,
            EveamcpConstants.NODE_BOUNDS_YMIN_NAME, GraphmlConstants.DOUBLE)
    writeNewline(writer)
    writeKeyElement(writer,
            EveamcpConstants.NODE_COLOR, GraphmlConstants.NODE,
            EveamcpConstants.NODE_COLOR_NAME, GraphmlConstants.STRING)
    writeNewline(writer)
    writeKeyElement(writer,
            EveamcpConstants.NODE_CONTEXT, GraphmlConstants.NODE,
            EveamcpConstants.NODE_CONTEXT_NAME, GraphmlConstants.STRING)
    writeNewline(writer)
    writeKeyElement(writer,
            EveamcpConstants.NODE_FILTER, GraphmlConstants.NODE,
            EveamcpConstants.NODE_FILTER_NAME, GraphmlConstants.STRING)
    writeNewline(writer)
    writeKeyElement(writer,
            EveamcpConstants.NODE_NAME, GraphmlConstants.NODE,
            EveamcpConstants.NODE_NAME_NAME, GraphmlConstants.STRING)
    writeNewline(writer)
    writeKeyElement(writer,
            EveamcpConstants.NODE_ORDER, GraphmlConstants.NODE,
            EveamcpConstants.NODE_ORDER_NAME, GraphmlConstants.STRING)
    writeNewline(writer)
    writeKeyElement(writer,
            EveamcpConstants.NODE_FILE, GraphmlConstants.NODE,
            EveamcpConstants.NODE_FILE_NAME, GraphmlConstants.STRING)
    writeNewline(writer)
    writeKeyElement(writer,
            EveamcpConstants.NODE_TRANSFORM_SCALE, GraphmlConstants.NODE,
            EveamcpConstants.NODE_TRANSFORM_SCALE_NAME, GraphmlConstants.DOUBLE)
    writeNewline(writer)
    writeKeyElement(writer,
            EveamcpConstants.NODE_TRANSFORM_X, GraphmlConstants.NODE,
            EveamcpConstants.NODE_TRANSFORM_X_NAME, GraphmlConstants.DOUBLE)
    writeNewline(writer)
    writeKeyElement(writer,
            EveamcpConstants.NODE_TRANSFORM_Y, GraphmlConstants.NODE,
            EveamcpConstants.NODE_TRANSFORM_Y_NAME, GraphmlConstants.DOUBLE)
    writeNewline(writer)
    writeKeyElement(writer,
            EveamcpConstants.PORT_DIRECTION, GraphmlConstants.PORT,
            EveamcpConstants.PORT_DIRECTION_NAME, GraphmlConstants.STRING)
    writeNewline(writer)
    writeKeyElement(writer,
            EveamcpConstants.PORT_ID, GraphmlConstants.PORT,
            EveamcpConstants.PORT_ID_NAME, GraphmlConstants.STRING)
    writeNewline(writer)
    writeKeyElement(writer,
            EveamcpConstants.PORT_MESSAGE_TYPE, GraphmlConstants.PORT,
            EveamcpConstants.PORT_MESSAGE_TYPE_NAME, GraphmlConstants.STRING)
    writeNewline(writer)
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
    depth += 1
    writeIndentation(writer)
    writer.writeStartElement(GraphmlConstants.GRAPH)    // start <graph>
        writer.writeAttribute(GraphmlConstants.ID, root.id + ":0")
        writer.writeAttribute(GraphmlConstants.EDGE_DEFAULT, GraphmlConstants.DIRECTED)
        writeNewline(writer)
        depth += 1
        // write all children nodes of root node
        root.childNodes.forEach { iter ->
            writeIndentation(writer)
            writeNodeElement(writer, iter)
        }
    // write the top level edges because we will miss them otherwise
        root.childEdges.forEach { iter ->
            writeIndentation(writer)
            writeEdgeElement(writer, iter)
        }
        depth -= 1
    writeIndentation(writer)
    writer.writeEndElement()    // end <graph>
    writeNewline(writer)
    depth -= 1
}

private fun writeNodeElement(writer: XMLStreamWriter, node: Node) {
    writer.writeStartElement(GraphmlConstants.NODE) // start <node>
    writer.writeAttribute(GraphmlConstants.ID, node.id)
    writeNewline(writer)
    depth += 1
    // write ports
    writeIndentation(writer)
    writePortElement(writer, node.in_port)
    node.out_ports.forEach { iter ->
        writeIndentation(writer)
        writePortElement(writer, iter)
    }
    // write data fields
    writeIndentation(writer)
    writeDataElement(writer,
            EveamcpConstants.NODE_BOUNDS_XMAX, node.innerBounds.x_max.toString())
    writeIndentation(writer)
    writeDataElement(writer,
            EveamcpConstants.NODE_BOUNDS_XMIN, node.innerBounds.x_min.toString())
    writeIndentation(writer)
    writeDataElement(writer,
            EveamcpConstants.NODE_BOUNDS_YMAX, node.innerBounds.y_max.toString())
    writeIndentation(writer)
    writeDataElement(writer,
            EveamcpConstants.NODE_BOUNDS_YMIN, node.innerBounds.y_min.toString())
    writeIndentation(writer)
    writeDataElement(writer,
            EveamcpConstants.NODE_COLOR, colorToHexstring(node.color))
    if (node.hasContext) {
        writeIndentation(writer)
        writeDataElement(writer, EveamcpConstants.NODE_CONTEXT, node.context.asExpression())
    }

    if (node.hasFilter){
        writeIndentation(writer)
        writeDataElement(writer, EveamcpConstants.NODE_FILTER, node.filterExpression)
    }
    writeIndentation(writer)
    writeDataElement(writer,
            EveamcpConstants.NODE_NAME, node.name)

    if (node.hasOrder) {
        writeIndentation(writer)
        writeDataElement(writer, EveamcpConstants.NODE_ORDER, node.orderExpression)
    }
//    if(node.fileName != "")
//        writeDataElement(writer, EveamcpConstants.NODE_FILE, node.fileName)
    writeIndentation(writer)
    writeDataElement(writer,
            EveamcpConstants.NODE_TRANSFORM_SCALE, node.transform.scale.toString())
    writeIndentation(writer)
    writeDataElement(writer,
            EveamcpConstants.NODE_TRANSFORM_X, node.transform.x_offset.toString())
    writeIndentation(writer)
    writeDataElement(writer,
            EveamcpConstants.NODE_TRANSFORM_Y, node.transform.y_offset.toString())
    // write child nodes
    if (node.childNodes.size > 0)
        writeGraph(writer, node)
    depth -= 1
    writeIndentation(writer)
    writer.writeEndElement()    // end <node>
    writeNewline(writer)
}

private fun writePortElement(writer: XMLStreamWriter, port: Port) {
    writer.writeStartElement(GraphmlConstants.PORT) // start <port>
        writer.writeAttribute(GraphmlConstants.NAME, port.name)
        writeNewline(writer)
        depth += 1
        writeIndentation(writer)
        writeDataElement(writer, EveamcpConstants.PORT_DIRECTION, port.direction.toString())
        writeIndentation(writer)
        writeDataElement(writer, EveamcpConstants.PORT_ID, port.id)
        if (port.message_type.isNotEmpty()) {
            writeIndentation(writer)
            writeDataElement(writer, EveamcpConstants.PORT_MESSAGE_TYPE, port.message_type)
        }
    depth -= 1
    writeIndentation(writer)
    writer.writeEndElement()    // end <port>
    writeNewline(writer)
}

private fun writeEdgeElement(writer: XMLStreamWriter, edge: Edge) {
    writer.writeStartElement(GraphmlConstants.EDGE) // start <edge>
        writer.writeAttribute(GraphmlConstants.SOURCE, edge.sourcePort.parent!!.id)
        writer.writeAttribute(GraphmlConstants.TARGET, edge.targetPort.parent!!.id)
        writer.writeAttribute(GraphmlConstants.SOURCE_PORT, edge.sourcePort.id)
        writer.writeAttribute(GraphmlConstants.TARGET_PORT, edge.targetPort.id)
    writer.writeEndElement()    // end <edge>
    writeNewline(writer)
}

private fun writeDataElement(writer: XMLStreamWriter, id: String, value: String) {
    writer.writeStartElement(GraphmlConstants.DATA) // start <data>
        writer.writeAttribute(GraphmlConstants.KEY, id)
        writer.writeCharacters(value)
    writer.writeEndElement()    // end <data>
    writeNewline(writer)
}

private fun finishUp(writer: XMLStreamWriter){
    writer.writeEndElement()    // close the <graphml> element
    writer.writeEndDocument()
    writer.flush()
    writer.close()
}

private fun writeNewline(writer: XMLStreamWriter){
    writer.writeCharacters(System.getProperty("line.separator"))
}

private fun writeIndentation(writer: XMLStreamWriter){
    for (i in 0 until depth-1){
        for (j in 0 until SPACES_PER_TAB-1)
            writer.writeCharacters(" ")
    }
}