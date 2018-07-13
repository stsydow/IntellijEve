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
        val linkWithFile = JMenuItem("link with file")
        val shrinkItem = JMenuItem("shrink to minimal size")
        val showGeometryItem = JMenuItem("show node geometry")
        val hideGeometryItem = JMenuItem("hide node geometry")
        val generateItem = JMenuItem("generate")

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
            node.parent!!.remove(node)
            val crossingEdges = mutableListOf<Edge>()
            node.parent.childEdges.retainAll {
                val retain = !(it.target.parent == node || it.source.parent == node)
                if (!retain) {
                    crossingEdges.add(it)
                }
                retain
            }
            scene.pushOperation(RemoveNodeOperation(node.parent, node, crossingEdges))
        }

        addPortItem.addActionListener {
            val port = Port(Direction.OUT, "i32", node, scene)
            node.addPort(port)
            scene.pushOperation(AddPortOperation(node, port))
        }

        setColorItem.addActionListener {
            val new = JColorChooser.showDialog(scene, "Please select new color", Node.DEFAULT_COLOR)
            try {
                node.color = new
                node.repaint()
                scene.save()
            } catch(e: Exception) {
                println(e)
            }
        }

        setOrderItem.addActionListener {
            val existingOrders = mutableSetOf<Property>()
            if (scene != null)
                scene.knownProperties.forEach {
                    if (it.type == PropertyType.Order)
                        existingOrders.add(it)
                }
            val orderDialog = ListDialog("Please choose or enter order", existingOrders.toSet())
            orderDialog.pack()
            orderDialog.setLocationRelativeTo(scene)
            val existingProperty = node.getProperty(PropertyType.Order)
            if (existingProperty != null)
                orderDialog.setInitialSelection(existingProperty)
            orderDialog.isVisible = true
            val order = orderDialog.selection
            if (order != null){
                if (order != "")
                    node.setProperty(PropertyType.Order, order)
                else
                    node.removeProperty(PropertyType.Order);
            }
            node.repaint()
        }

        setContextId.addActionListener {
            val old = node.getProperty(PropertyType.ContextId);
            val order: String?;
            if (old != null) {
                order = JOptionPane.showInputDialog(scene, "construct id from:", old)
            } else {
                order = JOptionPane.showInputDialog(scene, "relevant fields:", old)
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
                order = JOptionPane.showInputDialog(scene, "set Filter", old)
            } else {
                order = JOptionPane.showInputDialog(scene, "filter by:", old)
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
            val new = JOptionPane.showInputDialog(scene, "set Name", old)
            if (new != null) {
                node.name = new
                node.repaint()
            }
        }

        /*
        linkWithFile.addActionListener{
            val old = node.linkedFilePath
            val chooser = JFileChooser()
            val filter = FileNameExtensionFilter("Rust code files", "rs")
            chooser.fileFilter = filter
            var nodesDir = File("")
            if (old != "") {
                nodesDir = File(old.substringBeforeLast('/'))
            } else {
                nodesDir = File(node.scene.editor!!.project.basePath, "./src/nodes")
                if (nodesDir == null) {
                    throw IOException("Directory ./src/nodes can not be found in project directory")
                }
            }
            chooser.currentDirectory = nodesDir
            val retVal = chooser.showOpenDialog(node.scene)
            if (retVal == JFileChooser.APPROVE_OPTION){
                node.linkedFilePath = chooser.selectedFile.absolutePath
                if (!Files.exists(Paths.get(node.linkedFilePath))){
                    Files.createFile(Paths.get(node.linkedFilePath))
                    LocalFileSystem.getInstance().refresh(true)
                }
                node.parent!!.onChildChanged(node)
            }
        }
        */

        shrinkItem.addActionListener(){
            val inBounds = node.innerBounds
            val minBounds = node.minimalBounds()
            val newBounds = Bounds(inBounds.x_min, inBounds.y_min, minBounds.x_max, minBounds.y_max)
            val op = ResizeNodeOperation(node, node.innerBounds, newBounds)
            op.apply()
            scene.pushOperation(op)
        }

        showGeometryItem.addActionListener{
            node.showGeometry()
        }

        hideGeometryItem.addActionListener{
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
            add(linkWithFile)
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
        showTransformsItem.addActionListener(){
            node.visualizeTransforms()
        }
        hideTransformsItem.addActionListener(){
            node.hideTransforms()
        }
        showGeometryItem.addActionListener{
            node.showGeometry()
        }
        hideGeometryItem.addActionListener{
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
            port.parent!!.remove(port)
        }
        setPayloadItem.addActionListener {
            port.message_type = JOptionPane.showInputDialog(scene, "new message type:", port.message_type)
            port.repaint()
        }

        add(deletePortItem)
        add(setPayloadItem)
    }
}

class EdgeContextMenu(val edge: Edge, val scene: Viewport, val interaction_point: Coordinate) : JPopupMenu() {
    init {
        val deleteEdgeItem = JMenuItem("delete edge")

        deleteEdgeItem.addActionListener {
            edge.parent!!.remove(edge)
            scene.pushOperation(RemoveEdgeOperation(edge.parent, edge))
        }

        add(deleteEdgeItem)
    }
}