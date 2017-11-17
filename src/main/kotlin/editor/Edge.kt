package editor

import java.awt.Color
import javax.swing.JPopupMenu

class Edge(transform: Transform, parent: Node, val source: Port, val target: Port, scene: Viewport) : UIElement(transform, parent, scene) {
    override val bounds: Bounds
        get() = Bounds.minimalBounds(source_coord, target_coord)

    val curve: CubicBezierCurve
    var drawArrowTips = false

    init {
        assert(isValidEdge(source, target))

        // check if this is a forwarding edge from a child outport to a parent outport
        // for these we don't want to draw the arrow tip
        val srcDir = source.direction
        val dstDir = target.direction
        val srcNode = source.parent!!
        val dstNode = target.parent!!
        if (srcNode.parent == dstNode && srcDir == Direction.OUT && dstDir == Direction.OUT) {
            drawArrowTips = false
        }

        curve = CubicBezierCurve(this, source_coord, target_coord)
    }

    val source_coord: Coordinate get() {
        if (source.parent == parent)
            return source.getExternalCoordinate()
        else {
            assert(source.parent!!.parent == parent)
            return source.parent.transform * source.getExternalCoordinate()
        }
    }

    val target_coord: Coordinate get() {
        if (target.parent == parent)
            return target.getExternalCoordinate()
        else {
            assert(target.parent!!.parent == parent)
            return target.parent.transform * target.getExternalCoordinate()
        }
    }

    override fun render(g: GraphicsProxy) {
        curve.paint(g)
        //g.line(source_coord, target_coord)

        val dir = (target_coord - Coordinate(target_coord.x - 0.5*UNIT, target_coord.y)).normalize()
        val normal = dir.normal()

        if (drawArrowTips) {
            val arrowShape = transform * listOf(
                    target_coord,
                    target_coord - 0.5 * UNIT * (dir + 0.4 * normal),
                    target_coord - 0.5 * UNIT * (dir - 0.4 * normal)
            )
            g.polygon(Color.BLACK, arrowShape, true)
        }
    }

    override fun getContextMenu(at: Coordinate): JPopupMenu {
        return EdgeContextMenu(this, at)
    }

    override fun pick(c: Coordinate, operation: Operation, screenTransform: Transform): UIElement? {
        val dist = shortestDistance(source_coord.x, source_coord.y, target_coord.x, target_coord.y, c.x, c.y)
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