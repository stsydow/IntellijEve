package editor

class Edge(transform: Transform, parent: Node, internal val sourcePort: Port, internal val targetPort: Port, scene: Viewport) : UIElement(transform, parent, scene) {
    companion object {
        const val PICK_DISTANCE = 5
    }

    private val curve: CubicBezierCurve

    init {
        require(isValidEdge(sourcePort, targetPort))
        curve = CubicBezierCurve(this)}

    override val bounds: Bounds
        get() = Bounds.minimalBounds(sourceCoord, targetCoord)

    val targetNode: Node get() = targetPort.parent!!
    val sourceNode: Node get() = sourcePort.parent!!

    val sourceCoord: Coordinate get() {
        if (sourceNode == parent)    // edge from in port to inner node
            return sourcePort.transform * sourcePort.connectionPointRight
        else {  // edge between two nodes on same level
            check(sourceNode.parent == parent)
            return sourceNode.transform * sourcePort.transform * sourcePort.connectionPointRight
        }
    }

    val targetCoord: Coordinate get() {
        return if (targetNode == parent)    // edge from inner node to out port
            targetPort.transform * targetPort.connectionPointLeft
        else {  // edge between two nodes on same level
            check(targetNode.parent == parent)
            targetNode.transform * targetPort.transform * targetPort.connectionPointLeft
        }
    }



    override fun render(g: GraphicsProxy) {
        curve.paint(g)
    }

    override fun pick(c: Coordinate, screenTransform: Transform, action: PickAction): Pickable? {
        if(c.x < bounds.x_min || c.x >= bounds.x_max)
            return null
        val dist = curve.shortestDistancePointToCurve(c.x, c.y)
        if(dist > PICK_DISTANCE) return null

        return when (action) {
            PickAction.Select -> this
            PickAction.Connect -> null
            PickAction.Drag -> null
            PickAction.Debug -> this
            PickAction.Menu -> this
        }
    }
}

fun isValidEdge(source: Port, destination: Port): Boolean {
    val srcDir = source.direction
    val dstDir = destination.direction
    val srcNode = source.parent!!
    val dstNode = destination.parent!!
    if (srcNode == dstNode.parent!! && srcDir == Direction.IN && dstDir == Direction.IN) {
        return true /* a forwarding edge from a parent inport to a child inport is ok */
    }
    if (srcNode.parent == dstNode && srcDir == Direction.OUT && dstDir == Direction.OUT) {
        return true /* a forwarding edge from a child outport to a parent outport is ok */
    }
    if (srcNode.parent == dstNode.parent && srcNode != dstNode &&
            srcDir == Direction.OUT && dstDir == Direction.IN) {
        return true /* a direct edge from a sibling outport to a sibling inport is ok, but not from a nodes output to its input */
    }
    return false
}