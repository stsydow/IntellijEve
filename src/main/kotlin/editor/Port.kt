package editor

import java.awt.Color

enum class Direction {IN, OUT }
enum class Type {VALUE, ASYNC_VALUE, STREAM }

class Port(val direction: Direction, val type: Type, var message_type: String, parent: Node, scene: Viewport) : UIElement(Transform(0.0, 0.0, 1.0), parent, scene) {
    companion object {
        val IN_SHAPE = Transform(0.0, 0.0, UNIT) * listOf(
                Coordinate(0.0, 0.0),
                Coordinate(0.2, 0.0),
                Coordinate(0.8, 0.5),
                Coordinate(0.2, 1.0),
                Coordinate(0.0, 1.0)
        )
        val OUT_SHAPE = Transform(0.0, 0.0, UNIT) * listOf(
                Coordinate(0.0, 0.0),
                Coordinate(-0.8, 0.0),
                Coordinate(-0.2, 0.5),
                Coordinate(-0.8, 1.0),
                Coordinate(0.0, 1.0)
        )
        val IN_SIZE = Bounds.minimalBounds(IN_SHAPE)
        val OUT_SIZE = Bounds.minimalBounds(OUT_SHAPE)
        const val DEFAULT_MESSAGE_TYPE = "i32"
        const val ANY_MESSAGE_TYPE = "Any"
    }

    var name: String = id

    val node: Node get() = parent!!

    val connectionPointLeft: Coordinate get() {
        return if (direction == Direction.IN)
            Transform(0.0, 0.0, UNIT) * Coordinate(0.0, 0.5)
        else // Direction.OUT
            Transform(0.0, 0.0, UNIT) * Coordinate(-0.2, 0.5)
    }

    val connectionPointRight: Coordinate get() {
        return if (direction == Direction.IN)
            Transform(0.0, 0.0, UNIT) * Coordinate(0.8, 0.5)
        else // Direction.OUT
            Transform(0.0, 0.0, UNIT) * Coordinate(0.0, 0.5)
    }

    val incommingEdges: Collection<Edge> get(){
        val isTarget = { e:Edge -> e.targetPort == this }

        val fromChilds = node.childEdges.filter(isTarget)

        val graph = node.parent

        val fromSiblings = when (graph)
        {
            is Node -> {
                graph.childEdges.filter(isTarget)
            }
            else -> mutableListOf()
        }
        return fromChilds + fromSiblings
    }

    val outgoingEdges: Iterable<Edge> get(){
        val isSource = { e:Edge -> e.sourcePort == this }
        val toChilds = node.childEdges.filter(isSource)
        val graph = node.parent

        val toSiblings = when (graph)
        {
            is Node -> {
                graph.childEdges.filter(isSource)
            }
            else -> mutableListOf()
        }

        return toChilds + toSiblings
    }

    override fun render(g: GraphicsProxy) {
        val localGraphics = g.stack(transform)
        val shape: List<Coordinate>
        val text_pos: Coordinate
        val text_bounds = localGraphics.textBounds(message_type, DEFAULT_FONT)

        when (direction) {
            Direction.IN -> {
                shape = IN_SHAPE
                text_pos = Coordinate(
                        IN_SIZE.x_max + UNIT / 2,
                        IN_SIZE.y_min + text_bounds.height
                )

            }
            Direction.OUT -> {
                shape = OUT_SHAPE
                text_pos = Coordinate(
                        OUT_SIZE.x_min - UNIT / 2 - text_bounds.width,
                        OUT_SIZE.y_min + text_bounds.height
                )
            }
        }

        if (g.transform.scale > 0.5) {
            localGraphics.text(message_type, text_pos, DEFAULT_FONT)
        }
        when (type) {
            Type.VALUE -> localGraphics.polygon(Color.GRAY, shape, true)
            Type.ASYNC_VALUE -> localGraphics.polygon(Color.GRAY, shape, false)
            Type.STREAM -> localGraphics.polygon(Color.BLACK, shape, true)
        }

    }

    override fun pick(c: Coordinate, screenTransform: Transform, action: PickAction): Pickable? {
        val local_c = !transform * c
        if (local_c in bounds)
            return when (action) {
                PickAction.Select -> null
                PickAction.Connect -> this
                PickAction.Drag -> this
                PickAction.Debug -> this
                PickAction.Menu -> this
            }
        return null
    }

    override val bounds: Bounds get() {
        return when (direction) {
            Direction.IN -> IN_SIZE
            Direction.OUT -> OUT_SIZE
        }
    }

    fun toString(n: Int): String {
        val prefix = get2NSpaces(n)
        return prefix + "Port[id: $id; direction: $direction]\n"
    }
}