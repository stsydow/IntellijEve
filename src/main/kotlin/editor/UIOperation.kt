package editor

import codegen.isRustKeyword
import codegen.isValidRustIdentifier
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import javax.swing.JOptionPane
import javax.swing.JPopupMenu

sealed class Operation(val root: RootNode?, val coord: Coordinate?, val element: UIElement?) {
    abstract fun perform()

    class AreaSelectOperation(root: RootNode, element: Node, val startPos: Coordinate, mousePos: Coordinate): Operation(root, null, element) {
        var selectRect: Bounds
        init {
            val topLeft = Coordinate(Math.min(startPos.x, mousePos.x), Math.min(startPos.y, mousePos.y))
            val bottomRight = Coordinate(Math.max(startPos.x, mousePos.x), Math.max(startPos.y, mousePos.y))
            this.selectRect = Bounds(topLeft, bottomRight)
        }

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
            val topLeft = Coordinate(Math.min(startPos.x, pos.x), Math.min(startPos.y, pos.y))
            val bottomRight = Coordinate(Math.max(startPos.x, pos.x), Math.max(startPos.y, pos.y))
            this.selectRect = Bounds(topLeft, bottomRight)
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

    class OpenRustFileOperation(root: RootNode, node: Node): Operation(root, null, node){
        override fun perform() {
            if (element != null && root != null){
                element as Node
                if (element.name == Node.DEFAULT_NAME) {
                    JOptionPane.showMessageDialog(root.viewport, "Please name the node before opening its file", "Error", JOptionPane.ERROR_MESSAGE)
                } else if (element.parentsUnnamed()) {
                    JOptionPane.showMessageDialog(root.viewport, "Node in higher level of node is not named, can not open its file", "Error", JOptionPane.ERROR_MESSAGE)
                } else {
                    val nodeFile = element.rustFileOfNode()
                    if (nodeFile != null) {
                            FileEditorManager.getInstance(root.viewport.editor!!.project).openFile(nodeFile, true)
                        }
                }
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

class SetNodeNameOperation(val node: Node, val oldName: String, val newName: String): UIOperation(){
    override fun reverse() {
        node.name = oldName
        node.repaint()
        node.scene.save()
    }

    override fun apply() {
        // check whether new name is valid rust identifier
        if (!isValidRustIdentifier(newName) || isRustKeyword(newName))
            JOptionPane.showMessageDialog(node.scene, "Name must be a valid Rust identifier and can not be a Rust keyword.", "Error", JOptionPane.ERROR_MESSAGE)
        // check whether new name is already taken on this level of hierarchy
        if (node.parent!!.getChildNodeByName(newName) != null)
            JOptionPane.showMessageDialog(node.scene, "Name must be unique, at least on this hierarchy level.", "Error", JOptionPane.ERROR_MESSAGE)
        else {
            // everything is fine, we can change the name
            // backup old path
            var oldPath = node.filePath
            node.name = newName
            node.repaint()
            node.scene.save()
            // we also need to rename (move) the corresponding rust file
            // (if it yet exists)
            if (oldPath != null && Files.exists(oldPath)) {
                val newFile = node.rustFileOfNode()
                if (newFile != null){
                    val newPath = Paths.get(newFile.path)
                    Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }
}