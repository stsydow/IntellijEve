package editor

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import intellij.GraphFileEditor
import java.awt.*
import java.awt.event.*
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

const val M_BUTTON_NONE = 0
const val M_BUTTON_LEFT = 1
const val M_BUTTON_MIDDLE = 2
const val M_BUTTON_RIGHT = 3

val CTRL_Z = KeyStroke.getKeyStroke("control Z")!!
val CTRL_Y = KeyStroke.getKeyStroke("control Y")!!

/*
enum class EventType {Single, Start, FollowUp, End}
data class Interaction(val operation: Operation, val type: EventType){
    //constructor(i: Interaction, transform: Transform):this(i.operation, i.type, transform.applyInverse(i.position))
}
*/

class Viewport(val editor: GraphFileEditor?) : JPanel(), MouseListener, MouseWheelListener, MouseMotionListener, ComponentListener {
    var idx: Int = 0
    var root = RootNode(this)

    val module:Module? get() {
        val graphFile = editor?.file
        val module:Module?
        if(editor != null && graphFile != null) {
            module = ModuleUtil.findModuleForFile(graphFile, editor.project)
        } else {
            module = null
        }
        return module
    }

    var currentSize = Dimension(1200, 800)
    var transform = Transform(currentSize.width.toDouble() / 2, currentSize.height.toDouble() / 2, 2.0)
    var lastMovementPosition: Coordinate? = null
    var lastMousePosition: Coordinate? = null
    var currentOperation: Operation = Operation.NoOperation()
    val operationsStack = Stack<UIOperation>()
    val reversedOperationsStack = Stack<UIOperation>()
    val selectedNodes = mutableListOf<Node>()


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
        actionMap.put("undoAction", UndoAction(this))
        actionMap.put("redoAction", RedoAction(this))
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
            globalGraphics.line(op.src.getGlobalTransform() * op.src.connectionPointRight, lastMovementPosition!!)
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
            val stroke = globalGraphics.awtGraphics.stroke as BasicStroke
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
        val sceneCoord = getSceneCoordinate(e)
        val picked: Pickable?
        val onlyCtrlModifier =  e.isControlDown &&
                                !e.isShiftDown &&
                                !e.isAltDown &&
                                !e.isAltGraphDown &&
                                !e.isMetaDown
        val op = when (e.button) {
            M_BUTTON_LEFT   -> {
                picked = root.pick(sceneCoord, transform, PickAction.Select)
                when {
                    e.clickCount == 2 -> when (picked) {
                        is Node -> Operation.OpenRustFileOperation(picked)
                        else    -> Operation.NoOperation()
                    }
                    onlyCtrlModifier -> when (picked) {
                        is Node -> Operation.SelectOperation(root, picked)
                        else    -> Operation.NoOperation()
                    }
                    else -> when (picked) {
                        is RootNode -> Operation.UnselectAllOperation(root)
                        is Node -> Operation.SelectOperation(root, picked)
                        else -> Operation.NoOperation()
                    }
                }
            }
            M_BUTTON_MIDDLE -> {
                picked = root.pick(sceneCoord, transform, PickAction.Debug)
                when (picked) {
                    null    -> Operation.NoOperation()
                    else    -> Operation.PrintDebugOperation(picked)
                }
            }
            M_BUTTON_RIGHT -> Operation.NoOperation()
            else -> Operation.NoOperation()
        }

        if(! (op is Operation.NoOperation)) {
            e.consume()
            op.perform()
            currentOperation = Operation.NoOperation()
        }
    }

    override fun mousePressed(e: MouseEvent) {
        val op: Operation
        val sceneCoord = getSceneCoordinate(e)
        val picked: Pickable?
        val noModifier =    !e.isControlDown &&
                            !e.isShiftDown &&
                            !e.isAltDown &&
                            !e.isAltGraphDown &&
                            !e.isMetaDown

        when (e.button) {
            M_BUTTON_LEFT   -> {
                picked = root.pick(sceneCoord, transform, PickAction.Drag)
                if (noModifier) {
                    when (picked) {
                        is Node -> {
                            if (!picked.isSelected && picked != root)
                                Operation.UnselectAllOperation(root).perform()
                            op = Operation.MoveOperation(picked)
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
                op = Operation.MoveOperation(root)
            }
            M_BUTTON_RIGHT   -> {
                picked = root.pick(sceneCoord, transform, PickAction.Menu)
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
        lastMovementPosition = null
        val op = currentOperation

        when (op) {
            is Operation.AreaSelectOperation -> {
                Operation.UnselectAllOperation(root).perform()
                op.update(sceneCoord)
            }
            is Operation.DrawEdgeOperation -> {
                val picked = root.pick(sceneCoord, transform,  PickAction.Connect)
                if (picked is Port)
                    op.target = picked
            }
            is Operation.MoveOperation -> {
                val parent: Node? = op.element.parent
                if (parent != null && op.hasMoved ) {
                    pushOperation(MoveOperation(op))
                }
            }
            is Operation.ShowMenuOperation -> {
                //TODO menu is still active
            }
            else -> { /*don't care*/ }
        }
        op.perform()
        currentOperation = Operation.NoOperation()
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
        val onlyCtrlModifier =  e.isControlDown &&
                                !e.isShiftDown &&
                                !e.isAltDown &&
                                !e.isAltGraphDown &&
                                !e.isMetaDown

        val op = currentOperation

        when (op) {
            is Operation.AreaSelectOperation -> {
                op.update(lastMousePosition!!)
            }
            is Operation.MoveOperation -> {
                val p = op.element.parent
                val delta_pos = sceneCoord - lastMovementPosition!!
                val newTransform: Transform
                if (p != null) {
                    val v_parent = p.getGlobalTransform().applyInverse(delta_pos)
                    newTransform = op.element.transform + v_parent
                } else {
                    newTransform = op.element.transform + delta_pos
                }
                op.update(op.element.getParentBoundsList(), newTransform)
                currentOperation = op
            }
            else -> {
                if (SwingUtilities.isLeftMouseButton(e)){
                    if (onlyCtrlModifier) {
                        val picked = root.pick(sceneCoord, transform, PickAction.Select)
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
