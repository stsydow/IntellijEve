package editor

import codegen.isRustKeyword
import codegen.isValidRustPascalCase
import com.intellij.openapi.fileEditor.FileEditorManager
import java.awt.Color
import java.awt.Component
import java.util.*
import javax.swing.JOptionPane
import javax.swing.JPopupMenu

/*
TODO:
interface Operation {
    fun perform()
}
*/

sealed class Operation() {
    abstract fun perform()

    class AreaSelectOperation(val root: RootNode, val element: Node, val startPos: Coordinate, mousePos: Coordinate): Operation() {
        var selectRect: Bounds
        init {
            val topLeft = Coordinate(Math.min(startPos.x, mousePos.x), Math.min(startPos.y, mousePos.y))
            val bottomRight = Coordinate(Math.max(startPos.x, mousePos.x), Math.max(startPos.y, mousePos.y))
            this.selectRect = Bounds(topLeft, bottomRight)
        }

        override fun perform() {
            val nodesContained = mutableListOf<Node>()
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

        fun update(pos: Coordinate){
            val topLeft = Coordinate(Math.min(startPos.x, pos.x), Math.min(startPos.y, pos.y))
            val bottomRight = Coordinate(Math.max(startPos.x, pos.x), Math.max(startPos.y, pos.y))
            this.selectRect = Bounds(topLeft, bottomRight)
        }
    }

    class DrawEdgeOperation(val root: RootNode, val src: Port) : Operation() {
        var target: Port? = null

        override fun perform() {
            if (target != null) {
                val ancestor = getCommonAncestorForEdge(src , target!!)
                if (ancestor != null) {
                    val edge = Edge(Transform(0.0, 0.0, 1.0), ancestor, src, target!!, root.viewport)
                    src.scene.pushOperation(AddEdgeOperation(ancestor, edge))
                }
            }
        }
    }

    class MoveOperation(val element: Node, val oldParentBounds: LinkedList<Bounds>, val oldTransform: Transform, var newParentBounds: List<Bounds>, var newTransform: Transform): Operation() {


        constructor(element: Node):this(element,
                element.getParentBoundsList(), element.transform,
                element.getParentBoundsList(), element.transform)

        val hasMoved: Boolean get() = (oldTransform != newTransform)
        override fun perform() {
            element.transform = newTransform
            element.repaint()   // TODO: needed here?
            val p: Node? = element.parent
            p?.onChildChanged(element)
        }

        fun update(newParentBounds: List<Bounds>, newTransform: Transform){
            this.newParentBounds = newParentBounds
            this.newTransform = newTransform
            this.perform()
        }
    }

    class NoOperation: Operation() {
        override fun perform() {
            // do nothing obviously
        }
    }

    class PrintDebugOperation(val element: Pickable): Operation(){
        val printElem = { typeStr:String, e:UIElement -> println("$typeStr ${e.id}: bounds ${e.bounds} \n\t external bounds ${e.externalBounds()} ")}
        override fun perform() {
            when(element) {
                is Edge -> printElem("Edge", element)
                is Node -> printElem("Node", element)
                is Port -> printElem("Port", element)
                else    -> check(false)
            }
        }
    }

    class SelectOperation(val root: RootNode, val element: UIElement?): Operation() {
        override fun perform() {
            if (element != null && element is Node){
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

    class ShowMenuOperation(val element: Pickable?, val coord: Coordinate, val view: Viewport, val comp: Component): Operation() {
        override fun perform() {
            if (element != null){
                val menu:JPopupMenu? = when (element) {
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

    class UnselectAllOperation(val root: RootNode): Operation(){
        override fun perform() {
                root.viewport.selectedNodes.forEach{ node -> node.isSelected = false }
                root.viewport.selectedNodes.clear()
        }
    }

    class OpenRustFileOperation(val node: Node): Operation(){
        override fun perform() {
            when {
                !node.hasName ->
                    JOptionPane.showMessageDialog(
                            node.scene,
                            "Please structName the node before opening its file",
                            "Error", JOptionPane.ERROR_MESSAGE)

                node.parentsUnnamed() ->
                    JOptionPane.showMessageDialog(
                            node.scene,
                            "Node in higher level of node is not named, can not open its file",
                            "Error", JOptionPane.ERROR_MESSAGE)
                else -> {
                    val nodeFile = node.impl.getOrCreateFile()
                    val project = node.scene.editor?.project

                    if(project != null) {
                        FileEditorManager.getInstance(project).openFile(nodeFile, true)
                    } else {
                        JOptionPane.showMessageDialog(
                                node.scene,
                                "No IntelliJ project instance found. Running in standalone mode?",
                                "Error", JOptionPane.ERROR_MESSAGE)
                    }
                }
            }
        }
    }
}

abstract class UIOperation {
    abstract fun reverse()
    abstract fun apply()
}

class MoveOperation(val op: Operation.MoveOperation): UIOperation() {
    override fun reverse() {
        with(op) {
            element.transform = oldTransform
            var p: Node? = element.parent
            for (i in oldParentBounds) {
                println("setting bounds to $i for $p")
                p!!.innerBounds = i
                p.positionChildren()
                p = p.parent
            }
            element.repaint()
        }
    }


    override  fun apply() {
        with(op) {
            element.transform = newTransform
            element.repaint()
            var p: Node? = element.parent
            for (i in newParentBounds) {
                p!!.innerBounds = i
                p.positionChildren()
                p = p.parent
            }
            element.repaint()
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

class RemovePortOperation(val parent: Node, val element: Port): UIOperation() {
    override fun reverse() {
        parent.addPort(element)
    }

    override fun apply() {
        parent.remove(element)
    }
}

class ChangePayloadOperation(val port: Port, val oldPayload: String, val newPayload: String): UIOperation() {
    override fun reverse() {
        port.message_type = oldPayload
        port.repaint()
    }

    override fun apply() {
        port.message_type = newPayload
        port.repaint()
    }
}

class AddNodeOperation(val parent: Node, val element: Node, val oldParentBounds: LinkedList<Bounds>, val newParentBounds: LinkedList<Bounds>): UIOperation() {
    init {
        require(oldParentBounds.size == newParentBounds.size)
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

        element.impl.retreiveFromTrash()
    }

    override  fun apply() {
        element.impl.moveToTrash()

        parent.remove(element)
        crossingEdges.forEach {
            it.parent!!.childEdges.remove(it)
        }
    }
}

class ChangeColorOperation(val node: Node, val oldColor: Color, val newColor: Color): UIOperation() {
    override fun reverse() {
        node.color = oldColor
        node.repaint()
    }

    override fun apply() {
        node.color = newColor
        node.repaint()
    }
}

class ChangePropertyOperation(val node: Node, val value: Property): UIOperation() {
    lateinit var old: Property
    override fun reverse() {
        val cur_value = old.exchange(node)
        check(cur_value == value)
        node.repaint()
    }

    override fun apply() {
        old = value.exchange(node)
        node.repaint()
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

class SetNodeNameOperation(val node: Node, val oldName: String, val newName: String): UIOperation() {
    override fun reverse() {
        node.name = oldName
    }

    override fun apply() {
        // check whether new structName is valid rust identifier
        if (!isValidRustPascalCase(newName) || isRustKeyword(newName)) {
            JOptionPane.showMessageDialog(node.scene,
                    "Name must be a valid Rust identifier in pascal case and can not be a Rust keyword.",
                    "Error", JOptionPane.ERROR_MESSAGE)
            return
        }
        // check whether new structName is already taken on this level of hierarchy
        if (node.parent!!.getChildNodeByName(newName) != null) {
            JOptionPane.showMessageDialog(node.scene,
                    "Name must be unique, at least on this hierarchy level.",
                    "Error", JOptionPane.ERROR_MESSAGE)
            return
        }

        node.name = newName
    }
}