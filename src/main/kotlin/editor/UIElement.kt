package editor

import java.util.*
import javax.swing.JPopupMenu

const val FONT_SIZE = 22.0
const val UNIT = FONT_SIZE
val DEFAULT_FONT = Font(FontStyle.REGULAR, FONT_SIZE)

enum class UIElementKind {
    Node,
    Port,
    Edge,
    All,
    NotEdge
}

abstract class UIElement(var transform: Transform, val parent: Node?, protected var scene: Viewport) {
    var id: String = "uielement"+scene.idx++

    abstract fun render(g: GraphicsProxy)
    abstract fun pick(c: Coordinate, operation: Operation, screenTransform: Transform, filter: UIElementKind): UIElement?

    open fun repaint() {
        parent?.repaint()
    }

    abstract val bounds: Bounds
    fun externalBounds() = transform * bounds

    fun getGlobalTransform(): Transform {
        val p = parent
        if (p == null)
            return transform
        else
            return p.getGlobalTransform() * transform
    }

    fun getParentBoundsList(): LinkedList<Bounds> {
        val bounds = LinkedList<Bounds>()
        var n: Node? = parent
        while(n != null) {
            bounds.addLast(n.innerBounds)
            n = n.parent
        }
        return bounds
    }
}

fun getCommonAncestorForEdge(src: Port, dst: Port): Node? {
    if (isValidEdge(src, dst)) {
        if (src.parent!!.parent == dst.parent)
            return dst.parent
        if (dst.parent!!.parent == src.parent)
            return src.parent
        if (src.parent.parent == dst.parent.parent) {
            return src.parent.parent
        }
    }
    return null
}



fun get2NSpaces(n: Int) : String {
    val res = StringBuffer()
    for (i in 0..n){
        res.append(' ')
        res.append(' ')
    }
    return res.toString()
}

// extract integer number from UIElement default names, like 23 out of "uielement23"
fun extractIndexFromString(str: String): Int?{
    val defaultNamePattern = Regex("^uielement[0-9][0-9]*$")
    if (defaultNamePattern.matches(str)){
        val digitPattern = Regex("[0-9][0-9]*")
        val digitMatch = digitPattern.find(str, 0)
        if (digitMatch != null){
            return digitMatch.value.toIntOrNull()
        }
    }
    return null
}

