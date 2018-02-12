package editor

import java.awt.Point
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D

data class Coordinate(val x: Double, val y: Double) {
    constructor(x: Int, y: Int) : this(x.toDouble(), y.toDouble())
    constructor(p: Point2D) : this(p.x, p.y)

    operator fun minus(c: Coordinate) = Vector(x - c.x, y - c.y)
    operator fun plus(c: Coordinate) = Vector(x + c.x, y + c.y)
    operator fun minus(v: Vector) = Coordinate(x - v.x, y - v.y)
    operator fun plus(v: Vector) = Coordinate(x + v.x, y + v.y)

    fun toIntPoint(): Point {
        return Point(x.toInt(), y.toInt())
    }
    fun toString(n: Int): String{
        val res = StringBuffer()
        res.append("Coordinate(x=")
        res.append("%.${n}f".format(x))
        res.append(", y=")
        res.append("%.${n}f".format(y))
        res.append(")")
        return res.toString()
    }
}

data class Vector(val x: Double, val y: Double) {
    operator fun times(s: Double) = Vector(s * x, s * y)
    operator fun times(v: Vector): Double {
        return v.x * x + v.y * y
    }

    operator fun plus(v: Vector) = Vector(x + v.x, y + v.y)
    operator fun minus(v: Vector) = Vector(x - v.x, y - v.y)
    operator fun unaryMinus() = Vector(-x, -y)

    fun length(): Double = Math.sqrt(x * x + y * y)
    fun normalize(): Vector {
        val len = length()
        return Vector(x / len, y / len)
    }

    fun normal(): Vector {
        val len = length()
        return Vector(y / len, -x / len)
    }

}

operator fun Double.times(v: Vector) = Vector(this * v.x, this * v.y)

data class Bounds(val x_min: Double, val y_min: Double, val x_max: Double, val y_max: Double) {
    init {
        assert(x_min == Double.POSITIVE_INFINITY || (x_min <= x_max && y_min <= y_max))
    }

    constructor() : this(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY)
    constructor(r: Rectangle2D) : this(r.minX, r.minY, r.maxX, r.maxY)

    companion object {
        fun minimalBounds(col: Iterable<Coordinate>): Bounds {
            var x_min: Double = Double.POSITIVE_INFINITY
            var y_min: Double = Double.POSITIVE_INFINITY
            var x_max: Double = Double.NEGATIVE_INFINITY
            var y_max: Double = Double.NEGATIVE_INFINITY

            col.forEach { (x, y) ->
                x_min = minOf(x_min, x)
                y_min = minOf(y_min, y)
                x_max = maxOf(x_max, x)
                y_max = maxOf(y_max, y)
            }

            return Bounds(x_min, y_min, x_max, y_max)
        }

        fun minimalBounds(vararg coordinate: Coordinate) = minimalBounds(coordinate.asIterable())

        fun infinite(): Bounds {
            return Bounds(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
        }
    }

    operator fun contains(c: Coordinate): Boolean = c.x in x_min..x_max && c.y in y_min..y_max

    operator fun contains(c: Bounds): Boolean {
        return c.x_min >= x_min && c.x_max <= x_max &&
                c.y_min >= y_min && c.y_max <= y_max
    }

    operator fun plus(c: Coordinate) = Bounds(
            minOf(x_min, c.x), minOf(y_min, c.y),
            maxOf(x_max, c.x), maxOf(y_max, c.y)
    )

    operator fun plus(b: Bounds) = Bounds(
            minOf(x_min, b.x_min), minOf(y_min, b.y_min),
            maxOf(x_max, b.x_max), maxOf(y_max, b.y_max)
    )

    operator fun plus(p: Padding) = Bounds(
            x_min - p.left, y_min - p.top,
            x_max + p.right, y_max + p.bottom
    )

    operator fun minus(p: Padding) = Bounds(
            x_min + p.left, y_min + p.top,
            x_max - p.right, y_max - p.bottom
    )

    fun min() = topLeft
    fun max() = topRight

    val topLeft: Coordinate get() = Coordinate(x_min, y_min)
    val bottomRight: Coordinate get() = Coordinate(x_max, y_max)
    val topRight: Coordinate get() = Coordinate(x_max, y_min)
    val bottomLeft: Coordinate get() = Coordinate(x_min, y_max)


    val width: Double get() = x_max - x_min
    val height: Double get() = y_max - y_min

    override fun toString(): String {
        var str = ""

        str += "topLeft (" + Math.round(x_min) + "," + Math.round(y_min) + ") "
        str += "topRight (" + Math.round(x_max) + "," + Math.round(y_min) + ") "
        str += "bottomRight (" + Math.round(x_max) + "," + Math.round(y_max) + ") "
        str += "bottomLeft (" + Math.round(x_min) + "," + Math.round(y_max) + ")"

        return str
    }

    fun toCoordinates(): List<Coordinate>{
        val res = MutableList<Coordinate>(0, {it -> Coordinate(0, 0)})

        res.add(topLeft)
        res.add(topRight)
        res.add(bottomRight)
        res.add(bottomLeft)

        return res.toList()
    }
}

data class Padding(val top: Double = 0.0, val right: Double = 0.0,
                   val bottom: Double = 0.0, val left: Double = 0.0) {
    constructor(value: Double) : this(value, value, value, value)

    operator fun plus(p: Padding) = Padding(
            top + p.top, right + p.right,
            bottom + p.bottom, left + p.left
    )
}

data class Transform(val x_offset: Double, val y_offset: Double, val scale: Double) {
    init {
        assert(x_offset.isFinite() && y_offset.isFinite() && scale.isFinite())
    }
    constructor() : this(0.0, 0.0, 1.0)
    constructor(c: Coordinate, scale: Double) : this(c.x, c.y, scale)

    operator fun times(t: Transform) = Transform(
            x_offset + scale * t.x_offset,
            y_offset + scale * t.y_offset,
            scale * t.scale
    )

    operator fun times(c: Coordinate) = Coordinate(
            c.x * scale + x_offset,
            c.y * scale + y_offset
    )

    operator fun times(b: Bounds) = Bounds(
            b.x_min * scale + x_offset,
            b.y_min * scale + y_offset,
            b.x_max * scale + x_offset,
            b.y_max * scale + y_offset
    )

    operator fun times(v: Vector) = Vector(v.x * scale, v.y * scale)

    operator fun times(col: Iterable<Coordinate>) = col.map { c -> this * c }

    operator fun plus(v: Vector) = Transform(x_offset + v.x, y_offset + v.y, scale)
    operator fun minus(v: Vector) = Transform(x_offset - v.x, y_offset - v.y, scale)

    operator fun not() = Transform(-x_offset / scale, -y_offset / scale, 1 / scale)

    fun applyInverse(c: Coordinate) = Coordinate(
            (c.x - x_offset) / scale,
            (c.y - y_offset) / scale
    )

    fun applyInverse(v: Vector) = Vector(v.x / scale, v.y / scale)

    fun zoom(factor: Double, c: Coordinate) =
            Transform(x_offset + scale * (1 - factor) * c.x,
                    y_offset + scale * (1 - factor) * c.y,
                    scale * factor)

    fun toString(n: Int): String {
        val res = StringBuffer()
        res.append("Transform(x_offset=")
        res.append("%.${n}f".format(x_offset))
        res.append(", y_offset=")
        res.append("%.${n}f".format(y_offset))
        res.append(", scale=")
        res.append("%.${n}f".format(scale))
        res.append(")")
        return res.toString()
    }
}
/*
    Calculate the shortest between the point (x3, y3) and the line (x1, y1) -- (x2, y2)
 */
fun shortestDistancePointToLine(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double): Double {
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
    return Math.sqrt(dx * dx + dy * dy)
}

