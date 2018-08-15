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
        val setOrderItem = JMenuItem("set order")
        val setContextId = JMenuItem("set context")
        val setFilter = JMenuItem("set filter")
        val setName = JMenuItem("set name")
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
            val newnode = Node(Transform(local_c, Node.SCALE_FACTOR), node, scene)
            val newBounds = node.getParentBoundsList()
            newBounds.addFirst(node.innerBounds)
            scene.pushOperation(AddNodeOperation(node, newnode, oldBounds, newBounds))
        }

        deleteNodeItem.addActionListener {
            val crossingEdges = mutableListOf<Edge>()
            node.parent!!.childEdges.retainAll {
                val retain = !(it.target.parent == node || it.source.parent == node)
                if (!retain) {
                    crossingEdges.add(it)
                }
                retain
            }
            scene.pushOperation(RemoveNodeOperation(node.parent, node, crossingEdges))
        }

        addPortItem.addActionListener {
            val port = Port(Direction.OUT, Port.DEFAULT_MESSAGE_TYPE, node, scene)
            scene.pushOperation(AddPortOperation(node, port))
        }

        setColorItem.addActionListener {
            val new = JColorChooser.showDialog(scene, "Please select new color", Node.DEFAULT_COLOR)
            try {
                scene.pushOperation(ChangeColorOperation(node, node.color, new))
            } catch(e: Exception) {
                println(e)
            }
        }

        setOrderItem.addActionListener {
            var oldOrder = node.getProperty(PropertyType.Order)
            if (oldOrder == null)
                oldOrder = ""
            // get a list of all existing orders in the graph
            val existingOrders = mutableSetOf<Property>()
            scene.knownProperties.forEach {
                if (it.type == PropertyType.Order)
                    existingOrders.add(it)
            }
            // display dialog to choose or enter order
            val orderDialog = ListDialog("Please choose or enter order",
                                    existingOrders.distinctBy {
                                    Pair(it.type, it.expression)
                                 })
            orderDialog.pack()
            orderDialog.setLocationRelativeTo(scene)
            orderDialog.setInitialSelection(oldOrder)
            orderDialog.isVisible = true
            val order = orderDialog.selection
            // set, change or remove order
            if (order != null){
                if (order != "")
                    scene.pushOperation(ChangePropertyOperation(node, PropertyType.Order, oldOrder, order))
                else
                    scene.pushOperation(RemovePropertyOperation(node, PropertyType.Order, oldOrder))
            }
        }

        setContextId.addActionListener {
            var oldContext = node.getProperty(PropertyType.ContextId)
            if (oldContext == null)
                oldContext = ""
            val context = JOptionPane.showInputDialog(scene, "Set ContextID", oldContext)
            if (context != null) {
                if (context != "")
                    scene.pushOperation(ChangePropertyOperation(node, PropertyType.ContextId, oldContext, context))
                else
                    scene.pushOperation(RemovePropertyOperation(node, PropertyType.ContextId, oldContext))
            }
        }

        setFilter.addActionListener {
            var oldFilter = node.getProperty(PropertyType.Filter)
            if (oldFilter == null)
                oldFilter = ""
            val filter = JOptionPane.showInputDialog(scene, "Set filter", oldFilter)
            if (filter != null) {
                if (filter != "")
                    scene.pushOperation(ChangePropertyOperation(node, PropertyType.Filter, oldFilter, filter))
                else
                    scene.pushOperation(RemovePropertyOperation(node, PropertyType.Filter, oldFilter))
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
            val op = Operation.OpenRustFileOperation(scene.root, node)
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
            add(setOrderItem)
            add(setContextId)
            add(setFilter)
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

class PortContextMenu(val port: Port, val scene: Viewport, val interaction_point: Coordinate) : JPopupMenu() {
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

class EdgeContextMenu(val edge: Edge, val scene: Viewport, val interaction_point: Coordinate) : JPopupMenu() {
    init {
        val deleteEdgeItem = JMenuItem("delete edge")

        deleteEdgeItem.addActionListener {
            scene.pushOperation(RemoveEdgeOperation(edge.parent!!, edge))
        }

        add(deleteEdgeItem)
    }
}