package editor

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import intellij.GraphFileEditor
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.KeyStroke

val M_BUTTON_NONE = 0
val M_BUTTON_LEFT = 1
val M_BUTTON_MIDDLE = 2
val M_BUTTON_RIGHT = 3

val CTRL_Z = KeyStroke.getKeyStroke("control Z")
val CTRL_Y = KeyStroke.getKeyStroke("ctrl y")
val SPACE_PRESS = KeyStroke.getKeyStroke("pressed SPACE")
val SPACE_RELEASE = KeyStroke.getKeyStroke("released SPACE")

enum class Operation {
    AreaSelect,
    DrawEdge,
    Menu,
    Move,
    None,
    OpenRustFile,
    PrintDebug,
    Select,
    UnselectAll
}

/*
enum class EventType {Single, Start, FollowUp, End}
data class Interaction(val operation: Operation, val type: EventType){
    //constructor(i: Interaction, transform: Transform):this(i.operation, i.type, transform.applyInverse(i.position))
}
*/

class Viewport(private val editor: GraphFileEditor?) : JPanel(), MouseListener, MouseWheelListener, MouseMotionListener, ComponentListener {
    var idx: Int = 0
    var root = RootNode(this)

    var currentSize = Dimension(1200, 800)
    var transform = Transform(currentSize.width.toDouble() / 2, currentSize.height.toDouble() / 2, 2.0)
    var focusedElement: UIElement? = null
    var focusedElementOriginalTransform: Transform? = null
    var focusedElementOriginalParentBounds: LinkedList<Bounds>? = null
    var lastMovementPosition: Coordinate? = null
    var lastMousePosition: Coordinate? = null
    var currentOperation = Operation.None
    var ongoingOperation: MyOperation = MyOperation.NoOperation()
    var operationsStack = Stack<UIOperation>()
    var reversedOperationsStack = Stack<UIOperation>()
    var selectedNodes = mutableListOf<Node>()
    var selectionRectangle : Bounds? = null
    var rectSelectStartPos : Coordinate? = null
    var spaceBarPressed : Boolean = false

    init {
        addMouseListener(this)
        addMouseMotionListener(this)
        addMouseWheelListener(this)
        addComponentListener(this)
        isFocusable = true
        preferredSize = currentSize

        // add our key bindings to the input map
        val map = getInputMap()
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

        val srcPort = focusedElement
        if (srcPort != null && srcPort is Port) {
            globalGraphics.line(srcPort.getGlobalTransform() * srcPort.connectionPointRight, lastMovementPosition!!)
        }

        // display the mouse position
        if (lastMousePosition != null){
            val textPos = lastMousePosition!!
            globalGraphics.text("" + textPos.x.toInt() + " : " + textPos.y.toInt(), textPos, Font(FontStyle.REGULAR, 4.0))
        }

        // paint a selection rectangle
        if (ongoingOperation is MyOperation.AreaSelectOperation) {
            val op = ongoingOperation as MyOperation.AreaSelectOperation
            globalGraphics.polygon(Color.MAGENTA, op.selectRect.toCoordinates(), false)
        }
    }

    fun getSceneCoordinate(e: MouseEvent) = !transform * Coordinate(e.x, e.y)

    fun popOperation() {
        if(operationsStack.count() > 0) {
            val op = operationsStack.pop()
            op.reverse()
            reversedOperationsStack.push(op)
        }
    }

    fun pushOperation(opChain: UIOperation) {
        reversedOperationsStack.clear()
        operationsStack.push(opChain)
    }

    fun popReverseOperation() {
        if(reversedOperationsStack.count() > 0) {
            val op = reversedOperationsStack.pop()
            op.apply()
            operationsStack.push(op)
        }
    }

    override fun mouseClicked(e: MouseEvent) {
        val op: MyOperation
        val sceneCoord = getSceneCoordinate(e)
        val picked: UIElement?
        when (e.button) {
            M_BUTTON_LEFT   -> {
                picked = root.pick(sceneCoord, Operation.None, transform, UIElementKind.Node)
                op = when (picked) {
                    is RootNode -> MyOperation.UnselectAllOperation(root)
                    is Node     -> MyOperation.SelectOperation(root, picked)
                    else        -> MyOperation.NoOperation()
                }
            }
            M_BUTTON_MIDDLE -> {
                picked = root.pick(sceneCoord, Operation.None, transform, UIElementKind.All)
                op = when (picked) {
                    null    -> MyOperation.NoOperation()
                    else    -> MyOperation.PrintDebugOperation(picked)
                }
            }
            M_BUTTON_RIGHT  -> {
                op = MyOperation.NoOperation()
            }
            else -> {
                op = MyOperation.NoOperation()
            }
        }
        op.perform()
    }

    override fun mousePressed(e: MouseEvent) {
        val view_pos: Coordinate = getSceneCoordinate(e)
        val op: MyOperation
        val sceneCoord = getSceneCoordinate(e)
        val picked: UIElement?
        val onlyCtrlModifier = !spaceBarPressed && e.isControlDown && !e.isShiftDown && !e.isAltDown && !e.isAltGraphDown && !e.isMetaDown

        when (e.button) {
            M_BUTTON_LEFT   -> {
                if (onlyCtrlModifier) {
                    currentOperation = Operation.AreaSelect
                    picked = root.pick(sceneCoord, Operation.None, transform, UIElementKind.Node)
                    if (picked != null)
                        op = MyOperation.AreaSelectOperation(root, picked!! as Node, sceneCoord, Bounds(sceneCoord.x, sceneCoord.y, sceneCoord.x, sceneCoord.y))
                    else
                        op = MyOperation.NoOperation()
                } else {
                    if (spaceBarPressed) {
                        picked = root
                    } else {
                        picked = root.pick(sceneCoord, Operation.None, transform, UIElementKind.NotEdge)
                    }
                    focusedElement = picked
                    when (picked) {
                        is Node -> {
                            focusedElementOriginalTransform = picked.transform
                            focusedElementOriginalParentBounds = picked.getParentBoundsList()
                            currentOperation = Operation.Move
                            op = MyOperation.MoveOperation(focusedElement!! as Node, focusedElementOriginalParentBounds!!, focusedElementOriginalTransform!!, focusedElementOriginalParentBounds!!, focusedElementOriginalTransform!!)
                        }
                        is Port -> {
                            currentOperation = Operation.DrawEdge
                            op = MyOperation.DrawEdgeOperation(root, picked)
                        }
                        else    -> op = MyOperation.NoOperation()
                    }
                }
            }
            M_BUTTON_MIDDLE -> {
                op = MyOperation.NoOperation()
            }
            M_BUTTON_RIGHT   -> {
                picked = root.pick(sceneCoord, Operation.None, transform, UIElementKind.All)
                op = MyOperation.ShowMenuOperation(picked, sceneCoord, this, e.component)
            }
            else            -> {
                op = MyOperation.NoOperation()
            }
        }
        ongoingOperation = op
        op.perform()
        lastMovementPosition = view_pos
    }

    override fun mouseReleased(e: MouseEvent) {
        val view_pos = getSceneCoordinate(e)
        val oldFocus = focusedElement
        focusedElement = null
        lastMovementPosition = null

        when (currentOperation) {
            Operation.AreaSelect -> {
                if (ongoingOperation is MyOperation.AreaSelectOperation){
                    val op = ongoingOperation as MyOperation.AreaSelectOperation
                    op.update(sceneCoord)
                }
            }
            Operation.DrawEdge -> {
                val picked = root.pick(view_pos, currentOperation, transform,  UIElementKind.Port)
                if (picked is Port && ongoingOperation is MyOperation.DrawEdgeOperation) {
                    val op = ongoingOperation as MyOperation.DrawEdgeOperation
                    op.target = picked
                    ongoingOperation = op
                }
            }
            Operation.Move -> {
                val parent: Node? = oldFocus!!.parent
                if (parent != null) {
                    val bounds = oldFocus.getParentBoundsList()
                    pushOperation(MoveOperation(oldFocus, focusedElementOriginalParentBounds!!, focusedElementOriginalTransform!!, bounds, oldFocus.transform))
                }
            }
            Operation.Menu -> {
                //TODO menu is still active
            }
            else -> { /*don't care*/ }
        }
        ongoingOperation.perform()
        ongoingOperation = MyOperation.NoOperation()
        currentOperation = Operation.None
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
        val view_pos = transform.applyInverse(Coordinate(e.x, e.y))
        lastMousePosition = getSceneCoordinate(e)

        when (currentOperation) {
            Operation.AreaSelect -> {
                if (ongoingOperation is MyOperation.AreaSelectOperation)
                    (ongoingOperation as MyOperation.AreaSelectOperation).update(lastMousePosition!!)
            }
            Operation.DrawEdge -> {
                assert(focusedElement is Port)
                repaint()
            }
            Operation.Move -> {
                if (ongoingOperation is MyOperation.MoveOperation) {
                    val p = focusedElement!!.parent
                    val delta_pos = view_pos - lastMovementPosition!!
                    val newTransform: Transform
                    if (p != null) {
                        val v_parent = p.getGlobalTransform().applyInverse(delta_pos)
                        newTransform = focusedElement!!.transform + v_parent
                    } else {
                        newTransform = focusedElement!!.transform + delta_pos
                    }
                    val op = ongoingOperation as MyOperation.MoveOperation
                    op.update(focusedElement!!.getParentBoundsList(), newTransform)
                    ongoingOperation = op
                }
            }
            else -> { /*don't care*/ }
        }
        lastMovementPosition = view_pos
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
        if (editor != null) {
            editor.save()
        }
    }

    fun generateCode(){
        if (editor != null) {
            editor.generate()
        }
    }
}
