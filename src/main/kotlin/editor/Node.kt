package editor

import java.awt.Color


enum class PropertyType {Filter, Order, ContextId }

class Property(val type: PropertyType, var expression: String) {
    override fun toString(): String {
        return "$type: $expression"
    }

    override fun equals(other: Any?): Boolean {
        return when(other) {
            null -> false
            is Property -> ((other.type == type) && other.expression == expression)
            else -> false
        }
    }

    override fun hashCode(): Int {
        return type.hashCode() * expression.hashCode()
    }
}


// assert Nodes may not overlap
open class Node(transform: Transform, name: String, parent: Node?, scene: Viewport) : UIElement(transform, parent, scene) {
    companion object {
        const val TITLE_HEIGHT = 2 * UNIT
        val INNER_PADDING = Padding(0.2 * UNIT)
        val DEFAULT_PADDING = Padding(TITLE_HEIGHT, Port.OUT_SIZE.width, 0.0, Port.IN_SIZE.width) + INNER_PADDING
        const val DEFAULT_NAME = "<anonymous>"
        val DEFAULT_COLOR = hex2Rgb("#b8d8d8")
        val DEFAULT_BOUNDS = Bounds(0.0, 0.0, 20 * UNIT, 15 * UNIT)
        var SCALE_FACTOR = 0.5
        val DEFAULT_TRANSFORM = Transform(0.0, 0.0, SCALE_FACTOR)

        val CHILD_NODE_SYMBOL = Transform(0.0, 0.0, UNIT) * listOf(
                Coordinate(0.0, 0.0),
                Coordinate(5.0, 0.0),
                Coordinate(5.0, 3.0),
                Coordinate(0.0, 3.0)
        )
    }

    var name: String = name
        get() = field
        set(value) {
            impl.rename(value)
            field = value
            repaint()
        }


    val impl = ImplementationNode(this)
    val codeGen = CodeGenNode(this)
    // Data Fields

    var showGeometry = false
    var childrenPickable = true
    var isSelected = false

    val in_port = Port(Direction.IN, Port.ANY_MESSAGE_TYPE, this, scene)
    val out_ports = mutableListOf<Port>()
    val childEdges = mutableListOf<Edge>()
    val childNodes = mutableSetOf<Node>()

    val padding = DEFAULT_PADDING
    var innerBounds = DEFAULT_BOUNDS
    var color = DEFAULT_COLOR

    // Properties

    val childNodeCount: Int get() = childNodes.size
    val outgoingEdges :  Iterable<Edge> get() = out_ports.flatMap { p -> p.outgoingEdges }
    val incommingEdges :  Iterable<Edge> get() = in_port?.incommingEdges
    val successors : Iterable<Node> get() = outgoingEdges.map { e -> e.targetNode }
    val predecessors : Iterable<Node> get() = incommingEdges.map { e -> e.sourceNode }
    val isSink: Boolean get() = out_ports.isEmpty()
    val isSource: Boolean get() = (in_port == null || incommingEdges.none()) // && childNodes.isEmpty()
    val isFanOut: Boolean get() = outgoingEdges.count() > 1
    val isFanIn: Boolean get() = in_port.incommingEdges.count() > 1

    val properties = mutableListOf<Property>()
    val propertiesPadding: Padding get() = Padding((1.2 * properties.size) * UNIT, 0.0, 0.0, 0.0)
    override val bounds: Bounds get() = innerBounds + padding + propertiesPadding
    val titleBottom: Coordinate get() = (bounds - propertiesPadding).min() + Vector(0.0, TITLE_HEIGHT)


    constructor(parent: Node, scene: Viewport) : this(DEFAULT_TRANSFORM, parent, scene)
    constructor(t: Transform, parent: Node, scene: Viewport) : this(t, DEFAULT_NAME, parent, scene) {
        println("Created Node for parent ${parent.id} with structName $name and id $id")
        parent.addNode(this)
    }

    init {
        require(this is RootNode || parent != null)
        positionChildren()
    }

    open fun positionChildren() {
        in_port.transform = Transform(titleBottom + Vector(0.0, UNIT / 2), 1.0)
        out_ports.forEachIndexed { i, port ->
            port.transform = Transform(titleBottom + Vector(bounds.width, UNIT / 2 + i * (UNIT / 2 + Port.OUT_SIZE.height)), 1.0)
        }
    }

    fun addNode(child: Node) {
        require(child.parent == this)
        childNodes.add(child)
        onChildChanged(child)
        repaint()
    }

    // NOTE: this function is overloaded in the RootNode class!
    open fun addEdge(child: Edge) {
        require(child.parent == this)
        childEdges.forEach {
            if ((child.targetPort == it.targetPort)
                && (child.sourcePort == it.sourcePort))
                return
        }
        check(childEdges.add(child))
        parent?.onChildChanged(this)
        repaint()
    }

    fun addPort(child: Port) {
        out_ports.add(child)
        positionChildren()
        parent?.onChildChanged(this)
        repaint()
    }

    override fun pick(c: Coordinate, screenTransform: Transform, filter: UIElementKind): UIElement? {
        var picked: UIElement?
        val local_c = !transform * c
        if (local_c !in bounds) return null

        if (screenTransform.scale < DEFAULT_TRANSFORM.scale) {
            return this
        }

        // edges are not pickable when the children nodes are not rendered
        if (childrenPickable){
            childEdges.forEach {
                picked = it.pick(local_c, screenTransform * transform, filter)
                if (picked != null) {
                    return picked
                }
            }
        }

        picked = in_port.pick(local_c, screenTransform * transform, filter)
        if (picked != null) return picked

        for (out in out_ports) {
            picked = out.pick(local_c, screenTransform * transform, filter)
            if (picked != null) {
                return picked
            }
        }

        if (childrenPickable) {
            val iter = childNodes.iterator()
            while (iter.hasNext()) {
                val child = iter.next()
                val picked_child = child.pick(local_c, screenTransform * transform, filter)
                if (picked_child != null)
                    return picked_child
            }
        }
        return when (filter) {
            UIElementKind.NotEdge -> this
            UIElementKind.Port -> null
            UIElementKind.All -> this
            UIElementKind.Node -> this
            UIElementKind.Edge -> null
        }
    }

    // NOTE: this function is overloaded in the RootNode class
    open fun remove(child: UIElement) {
        require(child.parent == this)
        when (child) {
            is Node -> {
                check(childNodes.remove(child))
                onChildChanged(child)
            }
            is Port -> {
                require(child.direction == Direction.OUT)
                parent?.removeEdgesConnectedToPort(child)
                removeEdgesConnectedToPort(child)
                check(out_ports.remove(child))
                positionChildren()
                parent?.onChildChanged(this)
            }
            is Edge -> {
                check(childEdges.remove(child))
                parent?.onChildChanged(this)
            }
        }
        repaint()
    }

    fun getPortById(id: String): Port? {
        return if (in_port.id == id) {
            in_port
        } else {
            out_ports.find { it.id == id }
        }
    }

    fun getChildNodeById(id: String): Node? {
        return childNodes.find { it.id == id }
    }

    fun getChildNodeByName(name: String): Node? {
        return childNodes.find { it.name == name }
    }

    fun getChildEdgeByPortIds(source: String, target: String): Edge? {
        return childEdges.find { it.sourcePort.id == source && it.targetPort.id == target }
    }

    fun removeEdgesConnectedToPort(port: Port) {
        val iter = childEdges.listIterator()
        while (iter.hasNext()){
            val edge = iter.next()
            if (edge.sourcePort == port || edge.targetPort == port)
                iter.remove()
        }
    }

    fun parentsUnnamed(): Boolean {
        var p = parent
        while (p != null){
            if (p.name == Node.DEFAULT_NAME)
                return true
            p = p.parent
        }
        return false
    }

    open fun onChildChanged(child: Node) {
        // expand this node if necessary to fit children
        val c_bounds = child.externalBounds()
        if (c_bounds !in innerBounds) {
            innerBounds += c_bounds
            positionChildren()
        }
        // update parent of this node
        parent?.onChildChanged(this)

        // push other nodes aside
        resolveCollisions(child)
    }

    fun resolveCollisions(child: Node) {
        val c_bounds = child.externalBounds()
        childNodes.forEach {
            if (it != child){
                if (it.externalBounds().intersectsWith(c_bounds)) {
//                    println("Bounds of node ${child.id} and node ${it.id} are intersecting.")
                    val intersection = it.externalBounds().intersect(c_bounds)
                    if (intersection != null) {
//                        println("intersection: $intersection")
                        if (intersection.height > intersection.width) {
                            // push along X axis
                            if ((intersection.x_max >= c_bounds.x_min) && (intersection.x_min <= c_bounds.x_min))
                            // push towards lower Xes
                                it.moveGlobal(Vector(-intersection.width, 0.0))
                            else if ((intersection.x_max >= c_bounds.x_max) && (intersection.x_min <= c_bounds.x_max))
                            // push towards higher Xes
                                it.moveGlobal(Vector(intersection.width, 0.0))
                        } else {
                            // push along Y axis
                            if ((intersection.y_max >= c_bounds.y_min) && (intersection.y_min <= c_bounds.y_min))
                            // push towards lower Yes
                                it.moveGlobal(Vector(0.0, -intersection.height))
                            else if ((intersection.y_max >= c_bounds.y_max) && (intersection.y_min <= c_bounds.y_max))
                            // push towards higher Yes
                                it.moveGlobal(Vector(0.0, intersection.height))
                        }
                    }
                }
            }
        }
    }

    fun minimalBounds(): Bounds {
        return if (childNodes.isNotEmpty()) {
            childNodes.map { c ->
                c.externalBounds()
            }.reduce { groupBounds, bounds ->
                groupBounds + bounds
            }
        } else {
            DEFAULT_BOUNDS
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

    fun drawTransformLines(g: GraphicsProxy, parentOrigin: Coordinate, parentTransform: Transform){
        val childOrigin = parentTransform * transform * Coordinate(0.0, 0.0)
        g.line(parentOrigin, childOrigin)
        g.circle(Color.BLACK, childOrigin, 1.0 * transform.scale)
        val childDirection = (childOrigin - parentOrigin).normalize()
        val length = (childOrigin - parentOrigin).length()
        val textPos = parentOrigin + childDirection * (length / 2)
        g.text(transform.toString(2), textPos, Font(FontStyle.REGULAR, 0.3 * transform.scale * UNIT))
        childNodes.forEach { child ->
            child.drawTransformLines(g, childOrigin, parentTransform * transform)
        }
    }

    override fun render(g: GraphicsProxy) {
        val localGraphics = g.stack(transform)

        localGraphics.rect(color, bounds - propertiesPadding)

        localGraphics.text(name, titleBottom + Vector(0.5 * UNIT, -0.5 * UNIT), DEFAULT_FONT)
        localGraphics.line(titleBottom, titleBottom + Vector(bounds.width, 0.0))

        if (properties.size > 0) {
            localGraphics.polygon(color, listOf(
                    bounds.topLeft + Vector(0.0, propertiesPadding.top),
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

        // reset the flag for the children not being pickable
        childrenPickable = true

        if ((g.transform.scale < 1) && (childNodeCount > 0)){
            // set a flag that this node's children are not pickable
            childrenPickable = false
            // draw a symbolization of child nodes
            val lineA = listOf<Coordinate>().toMutableList()
            val lineB = listOf<Coordinate>().toMutableList()
            // first node shape
            val topLeft = bounds.topLeft + Vector(80.0, 160.0)
            var nodeOffset = topLeft
            var offsetTransform = Transform(nodeOffset.x, nodeOffset.y, 1.0)
            var nodePoly = offsetTransform * CHILD_NODE_SYMBOL
            localGraphics.polygon(Color.DARK_GRAY, nodePoly, true)

            // starting point for first line
            val pointA1 = Coordinate(nodePoly[2].x, nodePoly[1].y + (nodePoly[2].y-nodePoly[1].y)/2.0)

            // second node shape
            nodeOffset += Vector(20.0, 100.0)
            offsetTransform = Transform(nodeOffset.x, nodeOffset.y, 1.0)
            nodePoly = offsetTransform * CHILD_NODE_SYMBOL
            localGraphics.polygon(Color.DARK_GRAY, nodePoly, true)

            // starting point for second line
            val pointB1 = Coordinate(nodePoly[2].x, nodePoly[1].y + (nodePoly[2].y-nodePoly[1].y)/2.0)

            // third node shape
            nodeOffset += Vector(170.0, -60.0)
            offsetTransform = Transform(nodeOffset.x, nodeOffset.y, 1.0)
            nodePoly = offsetTransform * CHILD_NODE_SYMBOL
            localGraphics.polygon(Color.DARK_GRAY, nodePoly, true)

            // other points for first line
            val pointA4 = Coordinate(nodePoly[3].x, nodePoly[1].y + (nodePoly[3].y-nodePoly[0].y)/2.5)
            val pointA3 = Coordinate(pointA4.x-pointA4.x/15.0, pointA4.y)
            val pointA2 = Coordinate(pointA3.x, pointA1.y)
            lineA.addAll(listOf(pointA1, pointA2, pointA3, pointA4))
            localGraphics.lines(Color.DARK_GRAY, lineA)

            // other points for second line
            val pointB4 = Coordinate(nodePoly[3].x, nodePoly[1].y + (nodePoly[3].y-nodePoly[0].y)/1.5)
            val pointB3 = Coordinate(pointB4.x-pointB4.x/10.0, pointB4.y)
            val pointB2 = Coordinate(pointB3.x, pointB1.y)
            lineB.addAll(listOf(pointB1, pointB2, pointB3, pointB4))
            localGraphics.lines(Color.DARK_GRAY, lineB)
        } else {
            childNodes.forEach { child ->
                child.render(localGraphics)
            }
            childEdges.forEach {
                it.render(localGraphics)
            }
            // draw debug information about nodes geometry
            if (showGeometry){
                val fontSize = 1.0*transform.scale* UNIT
                val nodeOrigin = Coordinate(0.0, 0.0)
                localGraphics.rect(Color.RED, Bounds(-2.0, -2.0, 2.0, 2.0))
                localGraphics.text(nodeOrigin.toString(0), nodeOrigin+Vector(2.0, -2.0), Font(FontStyle.REGULAR, fontSize))

                var textPos = innerBounds.topLeft + Vector(2.0, fontSize)
                localGraphics.polygon(Color.RED, innerBounds.toCoordinates(), false)
                localGraphics.text("innerbounds", textPos, Font(FontStyle.REGULAR, fontSize))

                textPos = bounds.topLeft + Vector(2.0, fontSize)
                localGraphics.polygon(Color.RED, bounds.toCoordinates(), false)
                localGraphics.text("bounds = innerBounds + padding + propertiesPadding", textPos, Font(FontStyle.REGULAR, fontSize))

                textPos = (innerBounds + padding).topLeft + Vector(2.0, fontSize)
                localGraphics.polygon(Color.RED, (innerBounds + padding).toCoordinates(), false)
                localGraphics.text("innerBounds + padding", textPos, Font(FontStyle.REGULAR, fontSize))

                val minBounds = minimalBounds()
                textPos = minBounds.topLeft + Vector(2.0, fontSize)
                localGraphics.polygon(Color.RED, minBounds.toCoordinates(), false)
                localGraphics.text("minimalBounds()", textPos, Font(FontStyle.REGULAR, fontSize))
            }
        }

        // highlight a selected node
        if (isSelected){
            if (properties.size <= 0){
                val boundsToDraw = bounds - propertiesPadding
                localGraphics.polygon(Color.MAGENTA, boundsToDraw.toCoordinates(), false)
            } else {
                localGraphics.polygon(Color.MAGENTA, listOf(
                        bounds.topLeft,
                        bounds.bottomLeft,
                        bounds.bottomRight,
                        bounds.topRight + Vector(0.0, propertiesPadding.top),
                        bounds.topRight - Vector(propertiesPadding.top, 0.0)),
                        false)
            }
        }
    }

    fun getProperty(type: PropertyType): String? {
        properties.forEach {
            if (it.type == type)
                return it.expression
        }
        return null
    }

    fun setProperty(type: PropertyType, value: String) {
        if (value == "")
            removeProperty(type)
        else {
            properties.forEach {
                if (it.type == type) {
                    it.expression = value
                    scene.knownProperties.add(Property(type, value))
                    return
                }
            }
            properties.add(Property(type, value))
            scene.knownProperties.add(Property(type, value))
            scene.save()
        }
    }

    fun removeProperty(type: PropertyType) {
        properties.retainAll { it.type != type }
        scene.save()
    }

    open fun showGeometry(){
        showGeometry = true
        childNodes.forEach{it.showGeometry()}
    }

    open fun hideGeometry() {
        showGeometry = false
        childNodes.forEach { it.hideGeometry() }
    }

    override fun toString(): String {
        return toString(0)
    }



    fun toString(n: Int): String {
        val prefix = get2NSpaces(n)

        var str = "${prefix}Node[id: $id; structName: $name]{\n"
        str += in_port.toString(n+1)
        out_ports.forEach{
            str += it.toString(n+1)
        }
        childNodes.forEach{
            str += it.toString(n+1)
        }
        str += prefix + "}\n"

        return str
    }
}

class RootNode(val viewport: Viewport, t: Transform) : Node(t, "__root__", null, viewport) {
    var keepInSync = true
    var showTransforms = false

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

        // draw lines to visualize the transforms system
        if (showTransforms){
            // draw line from scene origin to origin of root node
            val sceneOrigin = !transform * Coordinate(0, 0)
            val rootOrigin = Coordinate(0, 0)
            localGraphics.circle(Color.BLACK, sceneOrigin, 1.0*transform.scale)
            localGraphics.circle(Color.BLACK, rootOrigin, 1.0*transform.scale)
            localGraphics.line(sceneOrigin, rootOrigin)
            val textPos = sceneOrigin + Vector((rootOrigin.x - sceneOrigin.x)/2.0, (rootOrigin.y - sceneOrigin.y)/2.0)
            localGraphics.text(transform.toString(2), textPos, Font(FontStyle.REGULAR, 0.3*transform.scale* UNIT))

            // draw line with transform caption for every child node
            childNodes.forEach { child ->
                child.drawTransformLines(localGraphics, rootOrigin, Transform(0.0, 0.0, 1.0))
            }
        }
    }

    override fun remove(child: UIElement) {
        require(child.parent == this)
        if (child is Node) {
            check(childNodes.remove(child))
            onChildChanged(child)
        } else if (child is Edge) {
            check(childEdges.remove(child))
            if (keepInSync)
                scene.save()
        }
        repaint()
    }

    override fun addEdge(child: Edge) {
        require(child.parent == this)
        childEdges.forEach {
            if ((child.targetPort == it.targetPort)
                    && (child.sourcePort == it.sourcePort))
                return
        }
        check(childEdges.add(child))
        if (keepInSync)
            scene.save()
        repaint()
    }

    /*
    override fun positionChildren() {
    }
    */

    override fun repaint() {
        viewport.repaint()
    }

    override fun pick(c: Coordinate, screenTransform: Transform, filter: UIElementKind): UIElement? {
        var picked: UIElement?
        val local_c = !transform * c

        require(screenTransform == viewport.transform)

        childEdges.forEach {
            picked = it.pick(local_c, screenTransform * transform, filter)
            if (picked != null) {
                return picked
            }
        }

        val iter = childNodes.iterator()
        while (iter.hasNext()) {
            val child = iter.next()
            val picked_child = child.pick(local_c, screenTransform * transform, filter)
            if (picked_child != null)
                return picked_child
        }
        return when (filter) {
            UIElementKind.NotEdge -> this
            UIElementKind.Port -> null
            UIElementKind.All -> this
            UIElementKind.Node -> this
            UIElementKind.Edge -> null

        }
    }

    override fun moveGlobal(v: Vector) {
        transform += v
        repaint()
    }

    override fun onChildChanged(child: Node) {
        val c_bounds = child.externalBounds()
        if (c_bounds !in innerBounds) {
            innerBounds += c_bounds
            positionChildren()
        }
        // push other nodes aside
        resolveCollisions(child)

        if (keepInSync) {
            scene.save()
        }
    }

    fun visualizeTransforms(){
        showTransforms = true
    }

    fun hideTransforms(){
        showTransforms = false
    }

    override fun showGeometry(){
        childNodes.forEach { it.showGeometry() }
    }

    override fun hideGeometry() {
        childNodes.forEach { it.hideGeometry() }
    }
}