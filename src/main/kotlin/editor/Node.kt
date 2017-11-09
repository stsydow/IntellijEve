package editor

import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color
import javax.swing.JPopupMenu

enum class PropertyType {Filter, Order, ContextId }

class Property(val type: PropertyType, var expression: String) {
    override fun toString(): String {
        return "${type.toString()}: $expression"
    }
}

// assert Nodes may not overlap
open class Node(transform: Transform, var name: String, parent: Node?, scene: Viewport) : UIElement(transform, parent, scene) {
    companion object {
        val TITLE_HEIGHT = 2 * UNIT
        val INNER_PADDING = Padding(0.2 * UNIT)
        val DEFAULT_PADDING = Padding(TITLE_HEIGHT, Port.OUT_SIZE.width, 0.0, Port.IN_SIZE.width) + INNER_PADDING
        val DEFAULT_NAME = "<anonymous>"
        val DEFAULT_BOUNDS = Bounds(0.0, 0.0, 20 * UNIT, 15 * UNIT)
        var SCALE_FACTOR = 0.5
        val DEFAULT_TRANSFORM = Transform(0.0, 0.0, SCALE_FACTOR)
    }

    val in_port = Port(Direction.IN, "Any", this, scene)
    val out_ports = mutableListOf<Port>()
    val childEdges = mutableListOf<Edge>()
    val childNodes = mutableListOf<Node>()

    val properties = mutableListOf<Property>()

    val propertiesPadding: Padding get() = Padding((1.2 * properties.size + 0.5) * UNIT, 0.0, 0.0, 0.0)

    override val bounds: Bounds get() = innerBounds + padding + propertiesPadding
    val titleBottom: Coordinate get() = (bounds - propertiesPadding).min() + Vector(0.0, TITLE_HEIGHT)

    var padding = DEFAULT_PADDING
    var innerBounds = DEFAULT_BOUNDS
    var color = Color.GREEN

    val childNodeCount: Int get() = childNodes.size

    constructor(parent: Node, scene: Viewport) : this(DEFAULT_TRANSFORM, parent, scene)
    constructor(t: Transform, parent: Node, scene: Viewport) : this(t, DEFAULT_NAME, parent, scene) {
        parent.addNode(this)
    }

    init {
        assert(this is RootNode || parent != null)
        positionChildren()
    }

    open fun positionChildren() {
        in_port.transform = Transform(titleBottom + Vector(0.0, UNIT / 2), 1.0)
        out_ports.forEachIndexed { i, port ->
            port.transform = Transform(titleBottom + Vector(bounds.width, UNIT / 2 + i * (UNIT / 2 + Port.OUT_SIZE.height)), 1.0)
        }
    }

    fun addNode(child: Node) {
        assert(child.parent == this)
        assert(childNodes.add(child))
        onChildChanged(child)
        repaint()
    }

    fun addEdge(child: Edge) {
        assert(child.parent == this)
        assert(childEdges.add(child))
        repaint()
    }

    fun addPort(child: Port) {
        out_ports.add(child)
        positionChildren()
        repaint();
    }

    override fun pick(c: Coordinate, operation: Operation, screenTransform: Transform): UIElement? {
        var picked: UIElement?
        val local_c = !transform * c
        if (local_c !in bounds) return null

        if (screenTransform.scale < DEFAULT_TRANSFORM.scale) {
            return this
        }

        childEdges.forEach {
            picked = it.pick(local_c, operation, screenTransform * transform)
            if (picked != null) {
                return picked
            }
        }

        picked = in_port.pick(local_c, operation, screenTransform * transform)
        if (picked != null) return picked

        for (out in out_ports) {
            picked = out.pick(local_c, operation, screenTransform * transform)
            if (picked != null) {
                return picked
            }
        }

        val iter = childNodes.iterator()
        while (iter.hasNext()) {
            val child = iter.next()
            val picked_child = child.pick(local_c, operation, screenTransform * transform)
            if (picked_child != null)
                return picked_child
        }
        return this
    }

    fun remove(child: UIElement) {
        assert(child.parent == this)
        if (child is Node) {
            assert(childNodes.remove(child))
        } else if (child is Port) {
            assert(child.direction == Direction.OUT)
            assert(out_ports.remove(child))
            positionChildren()
        } else if (child is Edge) {
            assert(childEdges.remove(child))
        }
        repaint()
    }

    fun getPortById(id: String): Port? {
        if (in_port.id == id) {
            return in_port
        } else {
            return out_ports.find { it.id == id }
        }
    }

    fun getChildNodeById(id: String): Node? {
        return childNodes.find { it.id == id }
    }

    fun getChildEdgeByPortIds(source: String, target: String): Edge? {
        return childEdges.find { it.source.id == source && it.target.id == target }
    }

    open fun onChildChanged(child: Node) {
        val c_bounds = child.externalBounds()
        //TODO grow parent and push other nodes aside
        if (c_bounds !in innerBounds) {
            innerBounds += c_bounds
            positionChildren()
            parent?.onChildChanged(this)
        }
    }

    fun minimalBounds(): Bounds {
        return childNodes.map {
            c ->
            c.externalBounds()
        }.reduce {
            groupBounds, bounds ->
            groupBounds + bounds
        }
    }

    open fun moveGlobal(v: Vector) {
        val p = parent
        if (p != null) {
            val v_parent = p.getGlobalTransform().applyInverse(v)
            transform += v_parent
            p.onChildChanged(this)
        } else {
            transform += v
        }
        repaint()
    }

    override fun render(g: GraphicsProxy) {
        val localGraphics = g.stack(transform)

        localGraphics.rect(color, bounds - propertiesPadding)

        localGraphics.text(name, titleBottom + Vector(0.5 * UNIT, -0.5 * UNIT), DEFAULT_FONT)
        localGraphics.line(titleBottom, titleBottom + Vector(bounds.width, 0.0))

        if (properties.size > 0) {
            localGraphics.polygon(color, listOf(bounds.topLeft + Vector(0.0, propertiesPadding.top),
                    bounds.topLeft,
                    bounds.topRight - Vector(propertiesPadding.top, 0.0),
                    bounds.topRight + Vector(0.0, propertiesPadding.top)),
                    true)
            localGraphics.lines(
                    Color.BLACK,
                    bounds.topLeft + Vector(0.0, propertiesPadding.top),
                    bounds.topLeft,
                    bounds.topRight - Vector(propertiesPadding.top, 0.0),
                    bounds.topRight + Vector(0.0, propertiesPadding.top)
            )
            properties.forEachIndexed { i, p ->
                localGraphics.text(p.toString(), bounds.topLeft + Vector(0.5 * UNIT, 1.2 * (i + 1) * UNIT), DEFAULT_FONT)
            }
        }

        in_port.render(localGraphics)

        out_ports.forEach { port ->
            port.render(localGraphics)
        }


        if (g.transform.scale < 1) return

        childNodes.forEach { child ->
            child.render(localGraphics)
        }

        childEdges.forEach {
            it.render(localGraphics)
        }
    }

    override fun getContextMenu(at: Coordinate): JPopupMenu {
        return NodeContextMenu(this, at)
    }

    fun getProperty(type: PropertyType): String? {
        properties.forEach {
            if (it.type == type)
                return it.expression;
        }
        return null;
    }

    fun setProperty(type: PropertyType, value: String) {
        properties.forEach {
            if (it.type == type) {
                it.expression = value;
                return;
            }
        }
        properties.add(Property(type, value))
    }

    fun removeProperty(type: PropertyType) {
        properties.retainAll { it.type != type }
    }
}

class RootNode(val viewport: Viewport, t: Transform) : Node(t, "__root__", null, viewport) {
    var keepInSync = true

    init {
        innerBounds = Bounds.infinite()
    }

    constructor(viewport: Viewport) : this(viewport, Transform())
    constructor(viewport: Viewport, sync: Boolean) : this(viewport, Transform()){
        keepInSync = sync
    }

    override fun render(g: GraphicsProxy) {
        val localGraphics = g.stack(transform)
        childNodes.forEach { child ->
            child.render(localGraphics)
        }

        childEdges.forEach {
            it.render(localGraphics)
        }
    }

    override fun positionChildren() {
    }

    override fun repaint() {
        viewport.repaint()
    }

    override fun pick(c: Coordinate, operation: Operation, screenTransform: Transform): UIElement? {
        val local_c = !transform * c

        val iter = childNodes.iterator()

        assert(screenTransform == viewport.transform)
        while (iter.hasNext()) {
            val child = iter.next()
            val picked_child = child.pick(local_c, operation, screenTransform * transform)
            if (picked_child != null)
                return picked_child
        }
        return this
    }

    override fun moveGlobal(v: Vector) {
        transform += v
        repaint()
    }

    override fun onChildChanged(child: Node) {
        val c_bounds = child.externalBounds()
        //TODO grow parent and push other nodes aside
        if (c_bounds !in innerBounds) {
            innerBounds += c_bounds
            positionChildren()
        }
        if (keepInSync)
            scene.editor!!.save()
    }

    override fun getContextMenu(at: Coordinate): JPopupMenu {
        return RootNodeContextMenu(this, at)
    }
}