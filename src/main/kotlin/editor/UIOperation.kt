package editor

import java.awt.Component
import java.util.*
import javax.swing.JPopupMenu

sealed class Operation(val root: RootNode?, val coord: Coordinate?, val element: UIElement?) {
    abstract fun perform()

    class AreaSelectOperation(root: RootNode, element: Node, val startPos: Coordinate, var selectRect: Bounds): Operation(root, null, element) {
        override fun perform() {
            val nodesContained = mutableListOf<Node>()
            if (root!= null && element != null && element is Node){
                if (element.childrenPickable){
                    element.childNodes.forEach {
                        val globalBounds = it.getGlobalTransform() * it.bounds
                        if (selectRect.contains(globalBounds))
                            nodesContained.add(it)
                    }
                }
                nodesContained.forEach {
                    it.isSelected = true
                    root.viewport.selectedNodes.add(it)
                }
            }
        }

        fun update(pos: Coordinate){
            this.selectRect = Bounds(startPos, pos)
        }
    }

    class DrawEdgeOperation(root: RootNode, src: Port): Operation(root, null, src){
        var target: Port? = null

        override fun perform() {
            if (target != null) {
                val ancestor = getCommonAncestorForEdge(element as Port, target!!)
                if (ancestor != null) {
                    val edge = Edge(Transform(0.0, 0.0, 1.0), ancestor, element, target!!, root!!.viewport)
                    ancestor.addEdge(edge)
//                    pushOperation(AddEdgeOperation(ancestor, edge))
                }
            }
        }
    }

    class MoveOperation(element: Node, val oldParentBounds: LinkedList<Bounds>, val oldTransform: Transform, var newParentBounds: List<Bounds>, var newTransform: Transform): Operation(null, null, element) {
        override fun perform() {
            if (element != null) {
                element.transform = newTransform
                element.repaint()   // TODO: needed here?
                var p: Node? = element.parent
                if (p != null)
                    p.onChildChanged(element as Node)
            }
        }

        fun update(newParentBounds: List<Bounds>, newTransform: Transform){
            this.newParentBounds = newParentBounds
            this.newTransform = newTransform
            this.perform()
        }
    }

    class NoOperation(): Operation(null, null, null) {
        override fun perform() {
            // do nothing obviously
        }
    }

    class PrintDebugOperation(element: UIElement): Operation(null, null, element){
        override fun perform() {
            if (element != null){
                val elemStr = when(element) {
                    is Edge -> "Edge"
                    is Node -> "Node"
                    is Port -> "Port"
                    else    -> "<Unknown>"
                }
                println("$elemStr ${element.id}: bounds ${element.bounds} \n\t external bounds ${element.externalBounds()} ")
            } else {
                println("no UIElement picked")
            }
        }
    }

    class SelectOperation(root: RootNode, element: UIElement?): Operation(root, null, element) {
        override fun perform() {
            if (root != null && element != null && element is Node){
                if (element.isSelected){
                    element.isSelected = false
                    root.viewport.selectedNodes.remove(element)
                } else {
                    element.isSelected = true
                    root.viewport.selectedNodes.add(element)
                }
            } else {
                println("Nothing to select")
            }
        }
    }

    class ShowMenuOperation(element: UIElement?, coord: Coordinate, val view: Viewport, val comp: Component): Operation(null, coord, element) {
        override fun perform() {
            if (element != null && coord != null){
                val menu:JPopupMenu?
                menu = when (element) {
                    is RootNode -> RootNodeContextMenu(element, view, coord)
                    is Edge     -> EdgeContextMenu(element, view, coord)
                    is Node     -> NodeContextMenu(element, view, coord)
                    is Port     -> PortContextMenu(element, view, coord)
                    else        -> null
                }
                if (menu != null) {
                    val menuCoords = view.transform * coord
                    menu.show(comp, menuCoords.x.toInt(), menuCoords.y.toInt())
                }
            }
        }
    }

    class UnselectAllOperation(root: RootNode): Operation(root, null, null){
        override fun perform() {
            if (root != null) {
                root.viewport.selectedNodes.forEach{ node -> node.isSelected = false }
                root.viewport.selectedNodes.clear()
            }
        }
    }
}

abstract class UIOperation {
    abstract fun reverse();
    abstract fun apply();
}

class MoveOperation(val element: UIElement, val oldParentBounds: LinkedList<Bounds>, val old: Transform, val newParentBounds: List<Bounds>, val new: Transform): UIOperation() {
    override fun reverse() {
        element.transform = old;
        var p: Node? = element.parent
        for (i in oldParentBounds) {
            println("setting bounds to $i for $p")
            p!!.innerBounds = i
            p.positionChildren()
            p = p.parent
        }
        element.repaint()
    }

    override  fun apply() {
        element.transform = new
        element.repaint()
        var p: Node? = element.parent
        for (i in newParentBounds) {
            p!!.innerBounds = i
            p.positionChildren()
            p = p.parent
        }
    }
}

class ResizeNodeOperation(val node: Node, val oldBounds: Bounds, val newBounds: Bounds): UIOperation() {
    override fun apply() {
        node.innerBounds = newBounds
        node.positionChildren()
        node.repaint()
    }

    override fun reverse() {
        node.innerBounds = oldBounds
        node.repaint()
    }
}

class AddPortOperation(val parent: Node, val element: Port): UIOperation() {
    override fun reverse() {
        parent.remove(element)
    }

    override  fun apply() {
        parent.addPort(element)
    }
}

class AddNodeOperation(val parent: Node, val element: Node, val oldParentBounds: LinkedList<Bounds>, val newParentBounds: LinkedList<Bounds>): UIOperation() {
    init {
        assert(oldParentBounds.size == newParentBounds.size)
    }
    override fun reverse() {
        parent.remove(element)
        var p: Node? = parent
        for (i in oldParentBounds) {
            println("setting bounds to $i for $p")
            p!!.innerBounds = i
            p.positionChildren()
            p = p.parent
        }
        element.repaint()
    }

    override  fun apply() {
        parent.addNode(element)
        var p: Node? = element.parent
        for (i in newParentBounds) {
            p!!.innerBounds = i
            p.positionChildren()
            p = p.parent
        }
    }
}

class RemoveNodeOperation(val parent: Node, val element: Node, val crossingEdges: List<Edge>): UIOperation() {
    override fun reverse() {
        parent.addNode(element)
        crossingEdges.forEach {
            it.parent!!.addEdge(it)
        }
    }

    override  fun apply() {
        parent.remove(element)
        crossingEdges.forEach {
            it.parent!!.childEdges.remove(it)
        }
    }
}

class AddEdgeOperation(val parent: Node, val element: Edge): UIOperation() {
    override fun reverse() {
        parent.remove(element)
    }

    override fun apply() {
        parent.addEdge(element)
    }
}

class RemoveEdgeOperation( val parent: Node, val element: Edge): UIOperation() {
    override fun reverse() {
        parent.addEdge(element)
    }

    override fun apply() {
        parent.remove(element)
    }
}