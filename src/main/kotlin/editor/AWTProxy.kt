package editor

import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Stroke

enum class FontStyle {REGULAR, BOLD, ITALIC }
data class Font(val style: FontStyle, val size: Double) {

    //constructor(style: FontStyle, size: Int):this(style, size.toDouble())
    //constructor(size: Int):this(FontStyle.REGULAR, size.toDouble())

    fun getAWTFont(graphics: Graphics2D, scale: Double): java.awt.Font {
        val awtStyle = when (style) {
            FontStyle.REGULAR -> java.awt.Font.PLAIN
            FontStyle.BOLD -> java.awt.Font.BOLD
            FontStyle.ITALIC -> java.awt.Font.ITALIC
        }
        return graphics.font.deriveFont(awtStyle, (size * scale).toFloat())
    }
}

fun Graphics2D.drawLine(start: Coordinate, end: Coordinate) =
        this.drawLine(start.x.toInt(), start.y.toInt(), end.x.toInt(), end.y.toInt())

class GraphicsProxy(graphics: Graphics2D, val transform: Transform) {

    internal val awtGraphics = graphics

    fun reset() {
        awtGraphics.color = Color.BLACK
        awtGraphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)

        //ANTIALIASING in with awt is rather expensive

        awtGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        //awtGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        //awtGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

        // enable if ANTIALIASING for everything is to slow
        //awtGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    }

    constructor(parent: GraphicsProxy, localTransform: Transform) :
            this(parent.awtGraphics, parent.transform * localTransform)

    fun stack(transform: Transform): GraphicsProxy {
        return GraphicsProxy(this, transform)
    }

    fun rect(c: Color, r: Bounds) {
        val oldColor = awtGraphics.color
        val globalBounds = transform * r

        val x_min = globalBounds.x_min.toInt()
        val y_min = globalBounds.y_min.toInt()
        val x_max = globalBounds.x_max.toInt()
        val y_max = globalBounds.y_max.toInt()

        awtGraphics.color = c
        awtGraphics.fillPolygon(
                intArrayOf(x_min, x_max, x_max, x_min),
                intArrayOf(y_min, y_min, y_max, y_max), 4)
        awtGraphics.color = oldColor

        awtGraphics.drawPolygon(
                intArrayOf(x_min, x_max, x_max, x_min),
                intArrayOf(y_min, y_min, y_max, y_max), 4)
    }

    fun circle(col: Color, center: Coordinate, radius: Double){
        val oldColor = awtGraphics.color
        val globalBounds = transform*Bounds(center.x-radius, center.y-radius, center.x+radius, center.y+radius)

        val x = globalBounds.x_min.toInt()
        val y = globalBounds.y_min.toInt()
        val width = (globalBounds.x_max - x).toInt()
        val height = (globalBounds.y_max - y).toInt()

        awtGraphics.color = col
        awtGraphics.fillOval(x, y, width, height)
        awtGraphics.color = oldColor

        awtGraphics.drawArc(x, y, width, height, 0, 360)
    }

    fun line(begin: Coordinate, end: Coordinate) {
        awtGraphics.drawLine(transform * begin, transform * end)
    }

    fun lines(c: Color, vararg coords: Coordinate) = lines(c, coords.asIterable())

    fun lines(c: Color, coords: Iterable<Coordinate>) {
        val oldColor = awtGraphics.color
        awtGraphics.color = c

        coords.map { c -> transform * c }.reduce { last, cur ->
            awtGraphics.drawLine(last, cur)
            cur
        }
        awtGraphics.color = oldColor
    }
    
    fun polygon(c: Color, points: Collection<Coordinate>, filled: Boolean, stroke: Stroke = awtGraphics.stroke){
        val oldColor = awtGraphics.color
        awtGraphics.color = c
        val oldStroke = awtGraphics.stroke
        awtGraphics.stroke = stroke
        val x = IntArray(points.size)
        val y = IntArray(points.size)

        val globalPoints = points.map { p -> transform * p }

        globalPoints.forEachIndexed { index, coordinate ->
            x[index] = (coordinate.x + 0.5).toInt()
            y[index] = (coordinate.y + 0.5).toInt()
        }

        if (filled)
            awtGraphics.fillPolygon(x, y, points.size)
        else
            awtGraphics.drawPolygon(x, y, points.size)
        awtGraphics.color = oldColor
        awtGraphics.stroke = oldStroke
    }

    fun text(text: String, position: Coordinate, font: Font) {
        val globalPosition = transform * position

        val awtFont = font.getAWTFont(awtGraphics, transform.scale)
        awtGraphics.font = awtFont

        awtGraphics.drawString(text, globalPosition.x.toInt(), globalPosition.y.toInt())
    }

    fun textBounds(text: String, font: Font): Bounds {
        val awtFont = font.getAWTFont(awtGraphics, transform.scale)
        val r = awtFont.getStringBounds(text, awtGraphics.fontRenderContext)
        return !transform * Bounds(r)
    }
}

fun hex2Rgb(colorStr: String): Color {
    return Color(
            Integer.valueOf(colorStr.substring(1, 3), 16)!!,
            Integer.valueOf(colorStr.substring(3, 5), 16)!!,
            Integer.valueOf(colorStr.substring(5, 7), 16)!!)
}

fun colorToHexstring(color: Color): String {
    return "#" + Integer.toHexString(color.rgb).substring(2)
}