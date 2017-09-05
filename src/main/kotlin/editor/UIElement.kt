package editor

import java.util.*
import javax.swing.JPopupMenu

const val FONT_SIZE = 22.0
const val UNIT = FONT_SIZE
val DEFAULT_FONT = Font(FontStyle.REGULAR, FONT_SIZE)

abstract class UIElement(var transform: Transform, val parent: Node?, var scene: Viewport) {

    val id: String
    init {
        id = "uielement"+scene.idx++
    }
    abstract fun render(g: GraphicsProxy)
    abstract fun getContextMenu(at: Coordinate): JPopupMenu
    abstract fun pick(c: Coordinate, operation: Operation, screenTransform: Transform): UIElement?

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

