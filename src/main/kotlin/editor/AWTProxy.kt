package editor

import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints

enum class FontStyle {REGULAR, BOLD, ITALIC }
data class Font(val style: FontStyle, val size: Double) {

    //constructor(style: FontStyle, size: Int):this(style, size.toDouble())
    //constructor(size: Int):this(FontStyle.REGULAR, size.toDouble())

    fun getAWTFont(graphics: Graphics2D, scale: Double): java.awt.Font {
        var awtStyle = 0
        when (style) {
            FontStyle.REGULAR ->
                awtStyle = java.awt.Font.PLAIN
            FontStyle.BOLD ->
                awtStyle = java.awt.Font.BOLD
            FontStyle.ITALIC ->
                awtStyle = java.awt.Font.ITALIC
        }
        return graphics.font.deriveFont(awtStyle, (size * scale).toFloat())
    }
}

fun Graphics2D.drawLine(start: Coordinate, end: Coordinate) =
        this.drawLine(start.x.toInt(), start.y.toInt(), end.x.toInt(), end.y.toInt())

class GraphicsProxy(graphics: Graphics2D, val transform: Transform) {

    internal val awt_graphics = graphics

    fun reset() {
        awt_graphics.color = Color.BLACK
        awt_graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)

        //ANTIALIASING in with awt is rather expensive

        awt_graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        //awt_graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        //awt_graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

        // enable if ANTIALIASING for everything is to slow
        //awt_graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    }

    constructor(parent: GraphicsProxy, localTransform: Transform) :
            this(parent.awt_graphics, parent.transform * localTransform)

    fun stack(transform: Transform): GraphicsProxy {
        return GraphicsProxy(this, transform)
    }

    fun rect(c: Color, r: Bounds) {
        val oldColor = awt_graphics.color
        val global_bounds = transform * r

        val x_min = global_bounds.x_min.toInt()
        val y_min = global_bounds.y_min.toInt()
        val x_max = global_bounds.x_max.toInt()
        val y_max = global_bounds.y_max.toInt()

        awt_graphics.color = c
        awt_graphics.fillPolygon(
                intArrayOf(x_min, x_max, x_max, x_min),
                intArrayOf(y_min, y_min, y_max, y_max), 4)
        awt_graphics.color = oldColor

        awt_graphics.drawPolygon(
                intArrayOf(x_min, x_max, x_max, x_min),
                intArrayOf(y_min, y_min, y_max, y_max), 4)
    }

    fun line(begin: Coordinate, end: Coordinate) {
        val global_begin = transform * begin
        val global_end = transform * end

        awt_graphics.drawLine(global_begin, global_end)
    }

    fun lines(c: Color, vararg coords: Coordinate) = lines(c, coords.asIterable())

    fun lines(c: Color, coords: Iterable<Coordinate>) {
        val old = awt_graphics.color
        awt_graphics.color = c
        val iter = coords.iterator()
        var last_coord = transform * iter.next()
        while (iter.hasNext()) {
            val cur_coord = transform * iter.next()
            awt_graphics.drawLine(last_coord, cur_coord)
            last_coord = cur_coord
        }
        awt_graphics.color = old
    }


    fun polygon(c: Color, points: List<Coordinate>, filled: Boolean) {
        val old = awt_graphics.color
        awt_graphics.color = c
        val x = IntArray(points.size)
        val y = IntArray(points.size)

        val global_points = points.map { p -> transform * p }

        global_points.forEachIndexed({ index, coordinate ->
            x[index] = (coordinate.x + 0.5).toInt()
            y[index] = (coordinate.y + 0.5).toInt()
        })

        if (filled)
            awt_graphics.fillPolygon(x, y, points.size)
        else
            awt_graphics.drawPolygon(x, y, points.size)
        awt_graphics.color = old
    }

    fun text(text: String, position: Coordinate, font: Font) {
        val global_position = transform * position

        val awtFont = font.getAWTFont(awt_graphics, transform.scale)
        awt_graphics.font = awtFont

        awt_graphics.drawString(text, global_position.x.toInt(), global_position.y.toInt())
    }

    fun textBounds(text: String, font: Font): Bounds {
        val awtFont = font.getAWTFont(awt_graphics, transform.scale)
        val r = awtFont.getStringBounds(text, awt_graphics.fontRenderContext)
        return !transform * Bounds(r)
    }
}

fun hex2Rgb(colorStr: String): Color {
    return Color(
            Integer.valueOf(colorStr.substring(1, 3), 16)!!,
            Integer.valueOf(colorStr.substring(3, 5), 16)!!,
            Integer.valueOf(colorStr.substring(5, 7), 16)!!)
}