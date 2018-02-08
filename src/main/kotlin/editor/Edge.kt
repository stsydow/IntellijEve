package editor

import java.awt.Color
import javax.swing.JPopupMenu

class Edge(transform: Transform, parent: Node, val source: Port, val target: Port, scene: Viewport) : UIElement(transform, parent, scene) {
    companion object {
        val PICK_DISTANCE = 5
    }

    val curve: CubicBezierCurve

    init {
        assert(isValidEdge(source, target))
        curve = CubicBezierCurve(this)}

    override val bounds: Bounds
        get() = Bounds.minimalBounds(source_coord, target_coord)

    val source_coord: Coordinate get() {
        if (source.parent == parent)    // edge from in port to inner node
            return source.transform * source.connectionPointRight
        else {  // edge between two nodes on same level
            assert(source.parent!!.parent == parent)
            return source.parent.transform * source.transform * source.connectionPointRight
        }
    }

    val target_coord: Coordinate get() {
        if (target.parent == parent)    // edge from inner node to out port
            return target.transform * target.connectionPointLeft
        else {  // edge between two nodes on same level
            assert(target.parent!!.parent == parent)
            return target.parent.transform * target.transform * target.connectionPointLeft
        }
    }

    override fun render(g: GraphicsProxy) {
        curve.paint(g)
    }

    override fun getContextMenu(at: Coordinate): JPopupMenu {
        return EdgeContextMenu(this, this.scene, at)
    }

    override fun pick(c: Coordinate, operation: Operation, screenTransform: Transform, filter: UIElementKind): UIElement? {
        val dist = curve.shortestDistancePointToCurve(c.x, c.y)
        return when (filter) {
            UIElementKind.NotEdge -> null
            UIElementKind.Port -> null
            UIElementKind.All -> this
            UIElementKind.Node -> null
            UIElementKind.Edge -> this
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