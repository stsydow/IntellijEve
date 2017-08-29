package editor

import java.awt.Color
import javax.swing.JPopupMenu

enum class Direction {IN, OUT }

class Port(val direction: Direction, val message_type: String, parent: Node, scene: Viewport) : UIElement(Transform(0.0, 0.0, 1.0), parent, scene) {
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
    }

    val connectionPoint: Coordinate get() {
        if (direction == Direction.IN)
            return Coordinate(IN_SIZE.x_min, IN_SIZE.height / 2)
        else
            return Coordinate(OUT_SIZE.x_max, OUT_SIZE.height / 2)
    }

    fun getExternalCoordinate() = transform * connectionPoint

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

        val poly = shape

        if (g.transform.scale > 0.5) {
            localGraphics.text(message_type, text_pos, DEFAULT_FONT)
        }
        localGraphics.polygon(Color.BLACK, poly, true)
    }

    override fun getContextMenu(at: Coordinate): JPopupMenu {
        return PortContextMenu(this, at)
    }

    override fun pick(c: Coordinate, operation: Operation, screenTransform: Transform): UIElement? {
        val local_c = !transform * c
        if (local_c in bounds) return this
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
}