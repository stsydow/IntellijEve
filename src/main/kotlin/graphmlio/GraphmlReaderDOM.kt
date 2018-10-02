package graphmlio

import editor.*
import gherkin.lexer.El
import org.w3c.dom.DOMException
import org.w3c.dom.Element
import org.w3c.dom.NodeList as DomNodeList
import org.w3c.dom.Node as DomNode
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class NodeList(private val nodes: DomNodeList):Iterable<DomNode> {
    override fun iterator(): Iterator<org.w3c.dom.Node> = NodeIterator(nodes)

    private class NodeIterator(private val nodes: DomNodeList) : Iterator<DomNode> {
        var index: Int = 0
        override fun hasNext(): Boolean = index < nodes.length

        override fun next(): org.w3c.dom.Node {
            require(hasNext())
            val node = nodes.item(index)
            index++
            return node
        }
    }
}

fun read(graphml : File, scene: Viewport) : RootNode?{
    val dbFactory = DocumentBuilderFactory.newInstance()
    val docBuilder = dbFactory.newDocumentBuilder()
    try {
        val doc = docBuilder.parse(graphml)
        doc.documentElement.normalize()
        val root = RootNode(scene, false)
        val childNodes = NodeList(doc.documentElement.childNodes)
        childNodes
                .filterIsInstance<Element>()
                .filter { e -> (e.tagName == "graph")}
                .forEach { g ->
                    createNodesOfGraphFromDOM(root, g, scene)
                    createsEdgesOfGraphFromDOM(root, g, scene)
                }

        root.keepInSync = true
        return root
    } catch (e : Exception) {
        e.printStackTrace()
        return null
    }
}

private fun extractDataStringValue(node: org.w3c.dom.Node, key: String) : String? {
    val resultNode = NodeList(node.childNodes)
            .filterIsInstance<Element>()
            .find { e -> (e.tagName == "data") && e.getAttribute("key") == key }

    return resultNode?.firstChild?.nodeValue
}

private fun createPortFromDOM(parent: Node, port: Element, scene: Viewport){
    // get port id
    val portId = extractDataStringValue(port, "port_id")
    if (portId == null)
        throw DOMException(DOMException.INVALID_STATE_ERR, "Port needs nonnull id")
    if (portId == "")
        throw DOMException(DOMException.INVALID_STATE_ERR, "Port needs nonempty id")
    updateSceneUIElementIndex(portId, scene)
    // get port message type
    var portMsgType = extractDataStringValue(port, "port_message_type")
    if (portMsgType == null)
        portMsgType = ""
    // get port structName
    val portName = port.getAttribute("structName")
    // get port direction
    val portDirection = extractDataStringValue(port, "port_direction")
    if (portDirection == Direction.IN.toString()){
        parent.in_port.id = portId
        parent.in_port.message_type = portMsgType
        parent.in_port.name = portName
    } else {
        // check that port id is unique for parent node
        if (parent.getPortById(portId) != null)
            throw DOMException(DOMException.INVALID_STATE_ERR, "Port id $portId is already taken by other port of node " + parent.name)
        val newPort = Port(Direction.OUT, portMsgType, parent, scene)
        newPort.id = portId
        newPort.name = portName
        parent.addPort(newPort)
    }
}

private fun createPortsForNodeFromDOM(parent: Node, node: Element, scene: Viewport){
    val childNodes = node.childNodes
    for (i in 0..childNodes.length-1){
        val port = childNodes.item(i)
        if ((port is Element) && (port.tagName == "port")) {
            createPortFromDOM(parent, port, scene)
        }
    }
}

private fun createNodeFromDOM(parent: Node, node: Element, scene: Viewport){
    val newNode: Node
    // get node id
    val nodeId = node.getAttribute("id")
    updateSceneUIElementIndex(nodeId, scene)
    if (parent.getChildNodeById(nodeId) != null)
        throw DOMException(DOMException.INVALID_STATE_ERR, "Node structName $nodeId is already taken by another Node.")
    else {
        newNode = Node(parent, scene)
        newNode.id = nodeId
        // get node structName
        val nodeName = extractDataStringValue(node, "node_name")
        if (nodeName == null)
            newNode.name = ""
        else
            newNode.name = nodeName
        // get node filter property
        val nodeFilterProperty = extractDataStringValue(node, EveamcpConstants.NODE_FILTER)
        if (nodeFilterProperty != null)
            newNode.setProperty(PropertyType.Filter, nodeFilterProperty)
        // get node context property
        val nodeContextProperty = extractDataStringValue(node, "node_context")
        if (nodeContextProperty != null)
            newNode.setProperty(PropertyType.ContextId, nodeContextProperty)
        // get node order property
        val nodeOrderProperty = extractDataStringValue(node, "node_order")
        if (nodeOrderProperty != null)
            newNode.setProperty(PropertyType.Order, nodeOrderProperty)
        // get node color
        val nodeColor = extractDataStringValue(node, "node_color")
        if (nodeColor != null)
            newNode.color = hex2Rgb(nodeColor)
        // get transform
        val nodeTransformX = extractDataStringValue(node, "node_transform_x")
        val nodeTransformY = extractDataStringValue(node, "node_transform_y")
        val nodeTransformScale = extractDataStringValue(node, "node_transform_scale")
        if (    (nodeTransformX != null) &&
                (nodeTransformY != null) &&
                (nodeTransformScale != null))
            newNode.transform = Transform(
                    nodeTransformX.toDouble(),
                    nodeTransformY.toDouble(),
                    nodeTransformScale.toDouble())
        // get bounds
        val nodeBoundsXMin = extractDataStringValue(node, "node_bounds_xmin")
        val nodeBoundsYMin = extractDataStringValue(node, "node_bounds_ymin")
        val nodeBoundsXMax = extractDataStringValue(node, "node_bounds_xmax")
        val nodeBoundsYMax = extractDataStringValue(node, "node_bounds_ymax")
        if (    (nodeBoundsXMin != null) &&
                (nodeBoundsYMin != null) &&
                (nodeBoundsXMax != null) &&
                (nodeBoundsYMax != null))
            newNode.innerBounds = Bounds(
                    nodeBoundsXMin.toDouble(),
                    nodeBoundsYMin.toDouble(),
                    nodeBoundsXMax.toDouble(),
                    nodeBoundsYMax.toDouble())
        newNode.positionChildren()
    }
    createPortsForNodeFromDOM(newNode, node, scene)
    val nodeSubgraphs = node.childNodes
    for (j in 0..nodeSubgraphs.length - 1) {
        val graph = nodeSubgraphs.item(j)
        if ((graph is Element) && (graph.tagName == "graph")) {
            createNodesOfGraphFromDOM(newNode, graph, scene)
            createsEdgesOfGraphFromDOM(newNode, graph, scene)
        }
    }
}

private fun createNodesOfGraphFromDOM(parent: Node, graph: Element, scene: Viewport) {
    val childNodes = graph.childNodes
    for (i in 0..childNodes.length-1) {
        val node = childNodes.item(i)
        if ((node is Element) && (node.tagName == "node")) {
            createNodeFromDOM(parent, node, scene)
        }
    }
}

private fun createEdgeFromDOM(parent: Node, edge: Element, scene: Viewport){
    val srcNodeId = edge.getAttribute("source")
    val tgtNodeId = edge.getAttribute("target")

    val srcNode: Node? = if (parent.id == srcNodeId)
        parent
    else
        parent.getChildNodeById(srcNodeId)

    if (srcNode == null)
        throw DOMException(DOMException.NOT_FOUND_ERR, "Could not locate source node with id $srcNodeId")

    val tgtNode: Node? = if (parent.id == tgtNodeId)
        parent
    else
        parent.getChildNodeById(tgtNodeId)

    if (tgtNode == null)
        throw DOMException(DOMException.NOT_FOUND_ERR, "Could not locate target node with id $tgtNodeId")
    val srcPortId = edge.getAttribute("sourceport")
    val srcPort = srcNode.getPortById(srcPortId)
    if (srcPort == null)
        throw DOMException(DOMException.NOT_FOUND_ERR, "Could not locate source port with id $srcPortId of node $srcNodeId")
    val tgtPortId = edge.getAttribute("targetport")
    val tgtPort = tgtNode.getPortById(tgtPortId)
    if (tgtPort == null)
        throw DOMException(DOMException.NOT_FOUND_ERR, "Could not locate target port with id $tgtPortId of node $tgtNodeId")
    val newEdge = Edge(Transform(), parent, srcPort, tgtPort, scene)
    parent.addEdge(newEdge)
}

private fun createsEdgesOfGraphFromDOM(parent: Node, graph: Element, scene: Viewport) {
    val childNodes = graph.childNodes
    for (i in 0..childNodes.length-1){
        val edge = childNodes.item(i)
        if ((edge is Element) && (edge.tagName == "edge")){
            createEdgeFromDOM(parent, edge, scene)
        }
    }
}

private fun updateSceneUIElementIndex(id: String, scene: Viewport){
    val index = extractIndexFromString(id)
    if (index != null) {
        scene.idx = Math.max(scene.idx, index)
    }
}