package editor

import javax.swing.JColorChooser
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPopupMenu
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

open class NodeContextMenu(val node: Node, val scene: Viewport, val interaction_point: Coordinate) : JPopupMenu() {
    init {
        val createNodeItem = JMenuItem("create node")
        val deleteNodeItem = JMenuItem("delete node")
        val addPortItem = JMenuItem("add port")
        val setColorItem = JMenuItem("set color")
        val setProperties = JMenuItem("set properties")
        val setName = JMenuItem("set structName")
        val openRustFile = JMenuItem("open rust file")
        val shrinkItem = JMenuItem("shrink to minimal size")
        val showGeometryItem = JMenuItem("show node geometry")
        val hideGeometryItem = JMenuItem("hide node geometry")
        val generateItem = JMenuItem("generate code")

        createNodeItem.addActionListener {
            val local_to_global = node.getGlobalTransform()
            val local_c = !local_to_global * interaction_point
            val oldBounds = node.getParentBoundsList()
            oldBounds.addFirst(node.innerBounds)
            val newNode = Node(local_c, node, scene)
            val newBounds = node.getParentBoundsList()
            newBounds.addFirst(node.innerBounds)
            scene.pushOperation(AddNodeOperation(node, newNode, oldBounds, newBounds))
        }

        deleteNodeItem.addActionListener {
            val crossingEdges = mutableListOf<Edge>()
            node.parent!!.childEdges.retainAll { edge ->
                val retain = !(edge.targetNode == node || edge.sourceNode == node)
                if (!retain) {
                    crossingEdges.add(edge)
                }
                retain
            }
            scene.pushOperation(RemoveNodeOperation(node.parent, node, crossingEdges))
        }

        addPortItem.addActionListener {
            val port = Port(Direction.OUT, Type.STREAM, Port.DEFAULT_MESSAGE_TYPE, node, scene)
            scene.pushOperation(AddPortOperation(node, port))
        }

        setColorItem.addActionListener {
            val new = JColorChooser.showDialog(scene, "Please select new color", node.color)
            try {
                scene.pushOperation(ChangeColorOperation(node, node.color, new))
            } catch(e: Exception) {
                println(e)
            }
        }

        setProperties.addActionListener {
            val oldOrder = node.orderExpression
            val oldContext = node.context
            val oldFilter = node.filterExpression

            val dialog = PropertyDialog(node)

            val successful = dialog.showAndGet()
            if (successful) {
                if (oldContext != dialog.context) {
                    val op = ChangePropertyOperation(node, dialog.context)
                    scene.pushOperation(op)
                }

                if(oldFilter != dialog.filter) {
                    val op = ChangePropertyOperation(node, Filter(dialog.filter))
                    scene.pushOperation(op)
                }
                if(oldOrder != dialog.order) {
                    val op = ChangePropertyOperation(node, Order(dialog.order))
                    scene.pushOperation(op)
                }
            }
        }

        setName.addActionListener {
            val old = node.name
            val new = JOptionPane.showInputDialog(scene, "set Name", old)
            if (new != null) {
                scene.pushOperation(SetNodeNameOperation(node, old, new))
            }
        }

        openRustFile.addActionListener {
            val op = Operation.OpenRustFileOperation(node)
            op.perform()
        }

        shrinkItem.addActionListener {
            val inBounds = node.innerBounds
            val minBounds = node.minimalBounds()
            val newBounds = Bounds(inBounds.x_min, inBounds.y_min, minBounds.x_max, minBounds.y_max)
            val op = ResizeNodeOperation(node, node.innerBounds, newBounds)
            op.apply()
            scene.pushOperation(op)
        }

        showGeometryItem.addActionListener {
            node.showGeometry()
        }

        hideGeometryItem.addActionListener {
            node.hideGeometry()
        }

        generateItem.addActionListener {
            scene.generateCode()
        }

        add(createNodeItem)
        if (node.parent != null) {
            add(deleteNodeItem)
            add(addPortItem)
            add(setColorItem)
            add(setProperties)
            add(setName)
            add(openRustFile)
            add(shrinkItem)
            add(showGeometryItem)
            add(hideGeometryItem)
        } else {
            add(generateItem)
        }

        addPopupMenuListener(MenuListener(scene))

    }
}

class MenuListener(val scene: Viewport):PopupMenuListener {
    override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {
        // don't care
    }

    override fun popupMenuCanceled(e: PopupMenuEvent?) {
        scene.currentOperation = Operation.NoOperation()
    }

    override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
//        assert(scene.currentOperation == Operation.Menu)
    }
}

class RootNodeContextMenu(node: RootNode, scene: Viewport, interaction_point: Coordinate) : NodeContextMenu(node, scene, interaction_point){
    init {
        val showTransformsItem = JMenuItem("visualize transforms")
        val hideTransformsItem = JMenuItem("hide transforms")
        val showGeometryItem = JMenuItem("show node geometry")
        val hideGeometryItem = JMenuItem("hide node geometry")
        showTransformsItem.addActionListener {
            node.visualizeTransforms()
        }
        hideTransformsItem.addActionListener {
            node.hideTransforms()
        }
        showGeometryItem.addActionListener {
            node.showGeometry()
        }
        hideGeometryItem.addActionListener {
            node.hideGeometry()
        }
        add(showTransformsItem)
        add(hideTransformsItem)
        add(showGeometryItem)
        add(hideGeometryItem)
    }
}

class PortContextMenu(private val port: Port, private val scene: Viewport, private val interaction_point: Coordinate) : JPopupMenu() {
    init {
        val deletePortItem = JMenuItem("delete port")
        val setPayloadItem = JMenuItem("set message type")

        deletePortItem.addActionListener {
            scene.pushOperation(RemovePortOperation(port.parent!!, port))
        }

        setPayloadItem.addActionListener {
            val newPayload = JOptionPane.showInputDialog(scene, "new message type:", port.message_type)
            scene.pushOperation(ChangePayloadOperation(port, port.message_type, newPayload))
        }

        add(deletePortItem)
        add(setPayloadItem)
    }
}

class EdgeContextMenu(private val edge: Edge, private val scene: Viewport, private val interaction_point: Coordinate) : JPopupMenu() {
    init {
        val deleteEdgeItem = JMenuItem("delete edge")

        deleteEdgeItem.addActionListener {
            scene.pushOperation(RemoveEdgeOperation(edge.parent!!, edge))
        }

        add(deleteEdgeItem)
    }
}