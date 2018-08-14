package editor

import com.intellij.openapi.vfs.LocalFileSystem
import intellij.GraphFileEditor
import java.awt.*
import java.awt.event.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.swing.*

const val M_BUTTON_NONE = 0
const val M_BUTTON_LEFT = 1
const val M_BUTTON_MIDDLE = 2
const val M_BUTTON_RIGHT = 3

val CTRL_Z = KeyStroke.getKeyStroke("control Z")
val CTRL_Y = KeyStroke.getKeyStroke("control Y")
val SPACE_PRESS = KeyStroke.getKeyStroke("pressed SPACE")
val SPACE_RELEASE = KeyStroke.getKeyStroke("released SPACE")

/*
enum class EventType {Single, Start, FollowUp, End}
data class Interaction(val operation: Operation, val type: EventType){
    //constructor(i: Interaction, transform: Transform):this(i.operation, i.type, transform.applyInverse(i.position))
}
*/

class Viewport(val editor: GraphFileEditor?) : JPanel(), MouseListener, MouseWheelListener, MouseMotionListener, ComponentListener {
    companion object {
        const val NODES_RELATIVE_PATH = "/src/nodes"
        const val TRASH_RELATIVE_PATH = "/src/.trash"
    }

    var idx: Int = 0
    var root = RootNode(this)

    var currentSize = Dimension(1200, 800)
    var transform = Transform(currentSize.width.toDouble() / 2, currentSize.height.toDouble() / 2, 2.0)
    var focusedElement: UIElement? = null
    var focusedElementOriginalTransform: Transform? = null
    var focusedElementOriginalParentBounds: LinkedList<Bounds>? = null
    var lastMovementPosition: Coordinate? = null
    var lastMousePosition: Coordinate? = null
    var currentOperation: Operation = Operation.NoOperation()
    var operationsStack = Stack<UIOperation>()
    var reversedOperationsStack = Stack<UIOperation>()
    var selectedNodes = mutableListOf<Node>()
    var spaceBarPressed : Boolean = false
    var knownProperties = mutableSetOf<Property>()

    val trashDir: Path get() {
        val trashPath = Paths.get(editor!!.project.basePath + TRASH_RELATIVE_PATH)
        if (!Files.isDirectory(trashPath))
            Files.createDirectories(trashPath)
        LocalFileSystem.getInstance().refresh(false)
        return trashPath
    }

    init {
        addMouseListener(this)
        addMouseMotionListener(this)
        addMouseWheelListener(this)
        addComponentListener(this)
        isFocusable = true
        preferredSize = currentSize

        // add our key bindings to the input map
        val map = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        map.put(CTRL_Z, "undoAction")
        map.put(CTRL_Y, "redoAction")
        map.put(SPACE_PRESS, "spacePressed")
        map.put(SPACE_RELEASE, "spaceReleased")
        actionMap.put("undoAction", UndoAction(this))
        actionMap.put("redoAction", RedoAction(this))
        actionMap.put("spacePressed", PressSpaceAction(this))
        actionMap.put("spaceReleased", ReleaseSpaceAction(this))
    }

    override fun paintComponent(graphics: Graphics?) {
        super.paintComponent(graphics)
        val g = graphics!!.create() as Graphics2D
        /*
        if (isOpaque) {
            g.color = background
            g.fillRect(0, 0, width, height)
        }
        */
        val globalGraphics = GraphicsProxy(g, transform)

        globalGraphics.reset()
        root.render(globalGraphics)

        // draw the line when dragging for creating an Edge
        if (currentOperation is Operation.DrawEdgeOperation) {
            val op = currentOperation as Operation.DrawEdgeOperation
            val srcPort = op.element as Port
            globalGraphics.line(srcPort.getGlobalTransform() * srcPort.connectionPointRight, lastMovementPosition!!)
        }

        // display the mouse position
        if (root.showTransforms && lastMousePosition != null){
            val textPos = lastMousePosition!!
            globalGraphics.text("" + textPos.x.toInt() + " : " + textPos.y.toInt(), textPos, Font(FontStyle.REGULAR, 4.0))
        }

        // paint a selection rectangle
        if (currentOperation is Operation.AreaSelectOperation) {
            val op = currentOperation as Operation.AreaSelectOperation
            globalGraphics.polygon(Color.MAGENTA, op.selectRect.toCoordinates(), false)
        }

        // paint dashed rectangle enclosing all selected nodes (if more than one)
        if (selectedNodes.size > 1){
            val groupCoords = mutableListOf<Coordinate>()
            selectedNodes.forEach {
                groupCoords.addAll((it.getGlobalTransform()*it.bounds).toCoordinates())
            }
            val groupBounds = Bounds.minimalBounds(groupCoords)
            val stroke = globalGraphics.awt_graphics.stroke as BasicStroke
            val dashedStroke = BasicStroke(stroke.lineWidth, stroke.endCap, stroke.lineJoin, stroke.miterLimit, floatArrayOf(9.0f), 0.0f)
            globalGraphics.polygon(Color.MAGENTA,
                    groupBounds.toCoordinates(),
                    false,
                    dashedStroke)
        }
    }

    fun getSceneCoordinate(e: MouseEvent) = !transform * Coordinate(e.x, e.y)

    fun popOperation() {
        if(operationsStack.count() > 0) {
            val op = operationsStack.pop()
            op.reverse()
            save()
            reversedOperationsStack.push(op)
        }
    }

    fun pushOperation(opChain: UIOperation) {
        reversedOperationsStack.clear()
        opChain.apply()
        save()
        operationsStack.push(opChain)
    }

    fun popReverseOperation() {
        if(reversedOperationsStack.count() > 0) {
            val op = reversedOperationsStack.pop()
            op.apply()
            save()
            operationsStack.push(op)
        }
    }

    override fun mouseClicked(e: MouseEvent) {
        val op: Operation
        val sceneCoord = getSceneCoordinate(e)
        val picked: UIElement?
        val onlyCtrlModifier =  !spaceBarPressed &&
                                e.isControlDown &&
                                !e.isShiftDown &&
                                !e.isAltDown &&
                                !e.isAltGraphDown &&
                                !e.isMetaDown
        when (e.button) {
            M_BUTTON_LEFT   -> {
                picked = root.pick(sceneCoord, transform, UIElementKind.Node)
                if (e.clickCount == 2){
                    op = when (picked) {
                        is Node -> Operation.OpenRustFileOperation(root, picked)
                        else    -> Operation.NoOperation()
                    }
                }
                else if (onlyCtrlModifier){
                    op = when (picked) {
                        is Node -> Operation.SelectOperation(root, picked)
                        else    -> Operation.NoOperation()
                    }
                } else {
                    op = when (picked) {
                        is RootNode -> Operation.UnselectAllOperation(root)
                        is Node -> Operation.SelectOperation(root, picked)
                        else -> Operation.NoOperation()
                    }
                }
            }
            M_BUTTON_MIDDLE -> {
                picked = root.pick(sceneCoord, transform, UIElementKind.All)
                op = when (picked) {
                    null    -> Operation.NoOperation()
                    else    -> Operation.PrintDebugOperation(picked)
                }
            }
            M_BUTTON_RIGHT  -> {
                op = Operation.NoOperation()
            }
            else -> {
                op = Operation.NoOperation()
            }
        }
        op.perform()
    }

    override fun mousePressed(e: MouseEvent) {
        val op: Operation
        val sceneCoord = getSceneCoordinate(e)
        val picked: UIElement?
        val noModifierOrSpace =     !e.isControlDown &&
                                    !e.isShiftDown &&
                                    !e.isAltDown &&
                                    !e.isAltGraphDown &&
                                    !e.isMetaDown

        when (e.button) {
            M_BUTTON_LEFT   -> {
                if (spaceBarPressed) {
                    picked = root
                } else {
                    picked = root.pick(sceneCoord, transform, UIElementKind.NotEdge)
                }
                if (noModifierOrSpace) {
                    focusedElement = picked
                    when (picked) {
                        is Node -> {
                            if (!picked.isSelected && picked != root)
                                Operation.UnselectAllOperation(root).perform()
                            focusedElementOriginalTransform = picked.transform
                            focusedElementOriginalParentBounds = picked.getParentBoundsList()
                            op = Operation.MoveOperation(focusedElement!! as Node,
                                                            focusedElementOriginalParentBounds!!,
                                                            focusedElementOriginalTransform!!,
                                                            focusedElementOriginalParentBounds!!,
                                                            focusedElementOriginalTransform!!)
                        }
                        is Port -> {
                            if (!picked.parent!!.isSelected)
                                Operation.UnselectAllOperation(root).perform()
                            op = Operation.DrawEdgeOperation(root, picked)
                        }
                        else -> op = Operation.NoOperation()
                    }
                } else {
                    op = Operation.NoOperation()
                }
            }
            M_BUTTON_MIDDLE -> {
                op = Operation.NoOperation()
            }
            M_BUTTON_RIGHT   -> {
                picked = root.pick(sceneCoord, transform, UIElementKind.All)
                op = Operation.ShowMenuOperation(picked, sceneCoord, this, e.component)
            }
            else            -> {
                op = Operation.NoOperation()
            }
        }
        currentOperation = op
        op.perform()
        lastMovementPosition = sceneCoord
    }

    override fun mouseReleased(e: MouseEvent) {
        val sceneCoord = getSceneCoordinate(e)
        val oldFocus = focusedElement
        focusedElement = null
        lastMovementPosition = null

        when (currentOperation) {
            is Operation.AreaSelectOperation -> {
                if (currentOperation is Operation.AreaSelectOperation){
                    Operation.UnselectAllOperation(root).perform()
                    (currentOperation as Operation.AreaSelectOperation).update(sceneCoord)
                }
            }
            is Operation.DrawEdgeOperation -> {
                val picked = root.pick(sceneCoord, transform,  UIElementKind.Port)
                if (picked is Port && currentOperation is Operation.DrawEdgeOperation)
                    (currentOperation as Operation.DrawEdgeOperation).target = picked
            }
            is Operation.MoveOperation -> {
                val parent: Node? = oldFocus!!.parent
                if (parent != null) {
                    val bounds = oldFocus.getParentBoundsList()
                    pushOperation(MoveOperation(oldFocus, focusedElementOriginalParentBounds!!, focusedElementOriginalTransform!!, bounds, oldFocus.transform))
                }
            }
            is Operation.ShowMenuOperation -> {
                //TODO menu is still active
            }
            else -> { /*don't care*/ }
        }
        currentOperation.perform()
        currentOperation = Operation.NoOperation()
        focusedElementOriginalTransform = null
        repaint()
    }

    override fun mouseEntered(e: MouseEvent) { /*don't care*/ }

    override fun mouseExited(e: MouseEvent) { /*don't care*/ }

    override fun mouseWheelMoved(e: MouseWheelEvent) {
        val c = !transform * Coordinate(e.x.toDouble(), e.y.toDouble())
        val scale_factor = Math.pow(1.2, -e.preciseWheelRotation)
        if (transform.scale * scale_factor > 1e8) {
            error("root zoom factor to large: ${transform.scale * scale_factor} - AWT bugs expected")
        } else {
            transform = transform.zoom(scale_factor, c)
            repaint()
        }
    }

    override fun mouseDragged(e: MouseEvent) {
        val sceneCoord = getSceneCoordinate(e)
        lastMousePosition = sceneCoord
        val onlyCtrlModifier =  !spaceBarPressed &&
                                e.isControlDown &&
                                !e.isShiftDown &&
                                !e.isAltDown &&
                                !e.isAltGraphDown &&
                                !e.isMetaDown

        when (currentOperation) {
            is Operation.AreaSelectOperation -> {
                (currentOperation as Operation.AreaSelectOperation).update(lastMousePosition!!)
            }
            is Operation.MoveOperation -> {
                val p = focusedElement!!.parent
                val delta_pos = sceneCoord - lastMovementPosition!!
                val newTransform: Transform
                if (p != null) {
                    val v_parent = p.getGlobalTransform().applyInverse(delta_pos)
                    newTransform = focusedElement!!.transform + v_parent
                } else {
                    newTransform = focusedElement!!.transform + delta_pos
                }
                val op = currentOperation as Operation.MoveOperation
                op.update(focusedElement!!.getParentBoundsList(), newTransform)
                currentOperation = op
            }
            else -> {
                if (SwingUtilities.isLeftMouseButton(e)){
                    if (onlyCtrlModifier) {
                        val picked = root.pick(sceneCoord, transform, UIElementKind.Node)
                        if (picked != null)
                            currentOperation = Operation.AreaSelectOperation(root,
                                                                                picked as Node,
                                                                                lastMovementPosition!!,
                                                                                sceneCoord)
                    }
                }
            }
        }
        lastMovementPosition = sceneCoord
        repaint()
    }

    override fun mouseMoved(e: MouseEvent) {
        lastMousePosition = getSceneCoordinate(e)
        repaint()
    }

    override fun componentHidden(e: ComponentEvent?) { /*don't care*/ }

    override fun componentMoved(e: ComponentEvent?) {
        println(e)
    }

    override fun componentShown(e: ComponentEvent?) { /*don't care*/ }

    override fun componentResized(e: ComponentEvent?) {
        if (width > 0 && height > 0) {
            println(e)

            val scale = Math.sqrt(
                    (width * width + height * height).toDouble() /
                            (currentSize.width * currentSize.width + currentSize.height * currentSize.height).toDouble()
            )

            val center = transform.applyInverse(
                    Coordinate((width - currentSize.width).toDouble(),
                            (height - currentSize.height).toDouble()))

            transform = transform.zoom(scale, center)
            currentSize = Dimension(width, height)
        }
    }

    fun save() {
        if (editor != null)
            editor.save()
    }

    fun generateCode(){
        if (editor != null)
            editor.generate()
    }
}
