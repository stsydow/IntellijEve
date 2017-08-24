package editor

import javax.swing.JPopupMenu

class Edge(transform: Transform, parent: Node, val source: Port, val destination: Port, scene: Viewport) : UIElement(transform, parent, scene) {
    override val bounds: Bounds
        get() = Bounds.minimalBounds(source_coord, destination_coord)

    init {
        assert(isValidEdge(source, destination))
    }

    val source_coord: Coordinate get() {
        if (source.parent == parent)
            return source.getExternalCoordinate()
        else {
            assert(source.parent!!.parent == parent)
            return source.parent.transform * source.getExternalCoordinate()
        }
    }

    val destination_coord: Coordinate get() {
        if (destination.parent == parent)
            return destination.getExternalCoordinate()
        else {
            assert(destination.parent!!.parent == parent)
            return destination.parent.transform * destination.getExternalCoordinate()
        }
    }

    override fun render(g: GraphicsProxy) {
        g.line(source_coord, destination_coord)

        val dir = (destination_coord - source_coord).normalize()
        val normal = dir.normal()
        g.line(destination_coord, destination_coord - 0.5 * UNIT * (dir + 0.5 * normal))
        g.line(destination_coord, destination_coord - 0.5 * UNIT * (dir - 0.5 * normal))
    }

    override fun getContextMenu(at: Coordinate): JPopupMenu {
        return EdgeContextMenu(this, at)
    }

    override fun pick(c: Coordinate, operation: Operation, screenTransform: Transform): UIElement? {
        val dist = shortestDistance(source_coord.x, source_coord.y, destination_coord.x, destination_coord.y, c.x, c.y)
        if (operation == Operation.Menu && dist < 5) {
            return this;
        }
        return null
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

private fun shortestDistance(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double): Double {
    val px = x2 - x1
    val py = y2 - y1
    val temp = px * px + py * py
    var u = ((x3 - x1) * px + (y3 - y1) * py) / temp
    if (u > 1) {
        u = 1.0
    } else if (u < 0) {
        u = 0.0
    }
    val x = x1 + u * px
    val y = y1 + u * py

    val dx = x - x3
    val dy = y - y3
    return Math.sqrt((dx * dx + dy * dy).toDouble())

}