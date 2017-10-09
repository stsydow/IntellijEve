package editor

import graphmlio.*
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.Messages
import intellij.GraphFileType
import java.io.File
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPopupMenu

open class NodeContextMenu(val node: Node, val interaction_point: Coordinate) : JPopupMenu() {
    init {
        val createNodeItem = JMenuItem("create node")
        val deleteNodeItem = JMenuItem("delete node")
        val addPortItem = JMenuItem("add port")
        val setColorItem = JMenuItem("set color")
        val setOrderItem = JMenuItem("set order")
        val setContextId = JMenuItem("set context")
        val setFilter = JMenuItem("set filter")
        val setName = JMenuItem("set name")
        val generateItem = JMenuItem("generate")

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
                val retain = !(it.target.parent == node || it.source.parent == node)
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
        setColorItem.addActionListener {
            val new = JOptionPane.showInputDialog(node.scene, "new color:", "#ff0000")
            try {
                node.color = hex2Rgb(new)
                node.repaint()
            } catch(e: Exception) {
                println(e)
            }
        }
        setOrderItem.addActionListener {
            val old = node.getProperty(PropertyType.Order);
            val order: String?;
            if (old != null) {
                order = JOptionPane.showInputDialog(node.scene, "order by:", old)
            } else {
                order = JOptionPane.showInputDialog(node.scene, "order by:", old)
            }

            if (order != null && order != "") {
                node.setProperty(PropertyType.Order, order)
            } else if (old != null) {
                node.removeProperty(PropertyType.Order);
            }
            node.repaint()
        }
        setContextId.addActionListener {
            val old = node.getProperty(PropertyType.ContextId);
            val order: String?;
            if (old != null) {
                order = JOptionPane.showInputDialog(node.scene, "construct id from:", old)
            } else {
                order = JOptionPane.showInputDialog(node.scene, "relevant fields:", old)
            }

            if (order != null && order != "") {
                node.setProperty(PropertyType.ContextId, order)
            } else if (old != null) {
                node.removeProperty(PropertyType.ContextId);
            }
            node.repaint()
        }
        setFilter.addActionListener {
            val old = node.getProperty(PropertyType.Filter);
            val order: String?;
            if (old != null) {
                order = JOptionPane.showInputDialog(node.scene, "set Filter", old)
            } else {
                order = JOptionPane.showInputDialog(node.scene, "filter by:", old)
            }

            if (order != null && order != "") {
                node.setProperty(PropertyType.Filter, order)
            } else if (old != null) {
                node.removeProperty(PropertyType.Filter);
            }
            node.repaint()
        }
        setName.addActionListener {
            val old = node.name
            val new = JOptionPane.showInputDialog(node.scene, "set Name", old)
            if (new != null) {
                node.name = new
                node.repaint()
            }
        }
        generateItem.addActionListener {
            node.scene.editor!!.generate()
        }

        add(createNodeItem)
        if (node.parent != null) {
            add(deleteNodeItem)
            add(addPortItem)
            add(setColorItem)
            add(setOrderItem)
            add(setContextId)
            add(setFilter)
            add(setName)
        } else {
            add(generateItem)
        }
    }
}

class RootNodeContextMenu(node: Node, interaction_point: Coordinate) : NodeContextMenu(node, interaction_point) {
    init {
        val loadGraphItem = JMenuItem("load graph")
        val saveGraphItem = JMenuItem("save graph")

        loadGraphItem.addActionListener{
            val fcDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor(GraphFileType.instance)
            fcDescriptor.title = "Select file to load"
            fcDescriptor.isChooseFiles
            FileChooser.chooseFile(fcDescriptor, null, null, {fileSelected ->
                val path = fileSelected.path
                val file = File(path)
                val oldRoot = node
                val scene = node.scene
                val newRoot = read(file, scene)
                if (newRoot != null) {
                    scene.root = newRoot
                    scene.repaint()
                }
            })
        }

        saveGraphItem.addActionListener{
            val path = Messages.showInputDialog("Please enter filepath relative to your \$HOME", "Save graph to file", null)
            if ((path != null) && (path.length > 0))
                write(System.getProperty("user.home") + "/" + path, node)
        }

        add(loadGraphItem)
        add(saveGraphItem)
    }
}

class PortContextMenu(val port: Port, val interaction_point: Coordinate) : JPopupMenu() {
    init {
        val deletePortItem = JMenuItem("delete port")
        val setPayloadItem = JMenuItem("set message type")

        deletePortItem.addActionListener {
            port.parent!!.remove(port)
        }
        setPayloadItem.addActionListener {
            port.message_type = JOptionPane.showInputDialog(port.parent!!.scene, "new message type:", port.message_type)
            port.repaint()
        }

        add(deletePortItem)
        add(setPayloadItem)
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