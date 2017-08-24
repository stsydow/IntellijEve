package editor

import javax.swing.JMenuItem
import javax.swing.JPopupMenu

class NodeContextMenu(val node: Node, val interaction_point: Coordinate) : JPopupMenu() {
    init {
        val createNodeItem = JMenuItem("create node")
        val deleteNodeItem = JMenuItem("delete node")
        val addPortItem = JMenuItem("add port")

        createNodeItem.addActionListener {
            val local_to_global = node.getGlobalTransform()
            val local_c = !local_to_global * interaction_point
            val oldBounds = node.getParentBoundsList()
            oldBounds.addFirst(node.innerBounds)
            val newnode = Node(Transform(local_c, Node.SCALE_FACTOR), node, node.scene)
            val newBounds = node.getParentBoundsList()
            newBounds.addFirst(node.innerBounds)
            node.scene.pushOperation(AddNodeOperation(node, newnode, oldBounds, newBounds))
        }
        deleteNodeItem.addActionListener {
            node.parent!!.remove(node)
            val crossingEdges = mutableListOf<Edge>()
            node.parent.childEdges.retainAll {
                val retain = !(it.destination.parent == node || it.source.parent == node)
                if (!retain) {
                    crossingEdges.add(it)
                }
                retain
            }
            node.scene.pushOperation(RemoveNodeOperation(node.parent, node, crossingEdges))
        }
        addPortItem.addActionListener {
            val port = Port(Direction.OUT, "i32", node, node.scene)
            node.addPort(port)
            node.scene.pushOperation(AddPortOperation(node, port))
        }

        add(createNodeItem)
        add(deleteNodeItem)
        add(addPortItem)
    }
}

class PortContextMenu(val port: Port, val interaction_point: Coordinate) : JPopupMenu() {
    init {
        val deletePortItem = JMenuItem("delete port")

        deletePortItem.addActionListener {
            port.parent!!.remove(port)
        }

        add(deletePortItem)
    }
}

class EdgeContextMenu(val edge: Edge, val interaction_point: Coordinate) : JPopupMenu() {
    init {
        val deleteEdgeItem = JMenuItem("delete edge")

        deleteEdgeItem.addActionListener {
            edge.parent!!.remove(edge)
            edge.parent.scene.pushOperation(RemoveEdgeOperation(edge.parent, edge))
        }

        add(deleteEdgeItem)
    }
}