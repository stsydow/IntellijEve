package editor

import java.awt.Color

enum class Direction {IN, OUT }

class Port(val direction: Direction, var message_type: String, parent: Node, scene: Viewport) : UIElement(Transform(0.0, 0.0, 1.0), parent, scene) {
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

    val connectionPointLeft: Coordinate get() {
        if (direction == Direction.IN)
            return Transform(0.0, 0.0, UNIT) * Coordinate(0.0, 0.5)
        else // Direction.OUT
            return Transform(0.0, 0.0, UNIT) * Coordinate(-0.2, 0.5)
    }

    val connectionPointRight: Coordinate get() {
        if (direction == Direction.IN)
            return Transform(0.0, 0.0, UNIT) * Coordinate(0.8, 0.5)
        else // Direction.OUT
            return Transform(0.0, 0.0, UNIT) * Coordinate(0.0, 0.5)
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
        localGraphics.polygon(Color.BLACK, shape, true)
    }

    override fun pick(c: Coordinate, screenTransform: Transform, filter: UIElementKind): UIElement? {
        val local_c = !transform * c
        if (local_c in bounds)
            return when (filter) {
                UIElementKind.NotEdge -> this
                UIElementKind.Port -> this
                UIElementKind.All -> this
                UIElementKind.Node -> null
                UIElementKind.Edge -> null

            }
        return null
    }

    override val bounds: Bounds get() {
        when (direction) {
            Direction.IN -> {
                return IN_SIZE
            }
            Direction.OUT -> {
                return OUT_SIZE
            }
        }
    }

    fun toString(n: Int): String {
        val prefix = get2NSpaces(n)
        return prefix + "Port[id: $id; direction: $direction]\n"
    }
}