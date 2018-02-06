package editor

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import intellij.GraphFileEditor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import javax.swing.JPanel

val M_BUTTON_NONE = 0
val M_BUTTON_LEFT = 1
val M_BUTTON_MIDDLE = 2
val M_BUTTON_RIGHT = 3

enum class Operation {Select, Move, Menu, None, DrawEdge, OpenRustFile }

/*
enum class EventType {Single, Start, FollowUp, End}
data class Interaction(val operation: Operation, val type: EventType){
    //constructor(i: Interaction, transform: Transform):this(i.operation, i.type, transform.applyInverse(i.position))
}
*/

class Viewport(private val editor: GraphFileEditor?) : JPanel(), MouseListener, MouseWheelListener, MouseMotionListener, ComponentListener, KeyListener {
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
    var operationsStack = Stack<UIOperation>()
    var reversedOperationsStack = Stack<UIOperation>()

    init {
        addMouseListener(this)
        addMouseMotionListener(this)
        addMouseWheelListener(this)
        addComponentListener(this)
        addKeyListener(this)
        isFocusable = true
        preferredSize = currentSize
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
        var op = Operation.None
        if ((e.clickCount == 2) && (!e.isConsumed)){
            e.consume()
            op = when (e.button) {
                M_BUTTON_LEFT -> Operation.OpenRustFile
                else -> Operation.None
            }
        } else {
            op = when (e.button) {
                M_BUTTON_LEFT -> Operation.Select
                M_BUTTON_RIGHT -> Operation.Menu
                else -> Operation.None
            }
        }
        if (op == Operation.None)
            return
        val c = getSceneCoordinate(e)
        val picked = root.pick(c, op, transform)
        if ((picked == root) || (picked is Port) || (picked is Edge))
            return
        picked as Node
        if (op == Operation.OpenRustFile){
            /*
            if (picked.relativeFileLocation == "") {
                println("No file linked to node ${picked.id}, creating one now.")
                val fs = LocalFileSystem.getInstance()
                val nodesDir = fs.findFileByIoFile(File(editor!!.project.basePath, "./src/nodes"))
                if (nodesDir == null)
                    throw IOException("Directory ./src/nodes can not be found in project directory")

                //val linkedFilePath = nodesDir.path + "/" + picked.name + ".rs"

                var realtivePath = picked.name + ".rs"


                picked.constructFilepathForNode()
                while (Files.exists(Paths.get(picked.linkedFilePath!!))){
                    picked.linkedFilePath = picked.incrementFilepath()
                }
                Files.createFile(Paths.get(picked.linkedFilePath!!))
                LocalFileSystem.getInstance().refresh(false)
                picked.parent!!.onChildChanged(picked)
            }
            if (!Files.exists(Paths.get(picked.linkedFilePath))){
                Files.createFile(Paths.get(picked.linkedFilePath))
                LocalFileSystem.getInstance().refresh(false)
            }
            val file = LocalFileSystem.getInstance().findFileByPath(picked.linkedFilePath)
            FileEditorManager.getInstance(editor!!.project).openFile(file!!, true)
            */
        }
        //TODO Selection?
    }


    override fun mousePressed(e: MouseEvent) {
        val view_pos:Coordinate = getSceneCoordinate(e)

        if (currentOperation != Operation.None) {
            println("An operation is already active: ${currentOperation}")
            return
        }

        if (e.button == M_BUTTON_LEFT) {
            val picked = root.pick(view_pos, Operation.Select, transform)
            focusedElement = picked
            currentOperation = when (picked) {
                is Port -> Operation.DrawEdge
                is Node -> Operation.Move
                else -> Operation.None
            }
            if(currentOperation == Operation.Move) {
                focusedElementOriginalTransform = picked!!.transform
                val bounds = picked.getParentBoundsList()
                focusedElementOriginalParentBounds = bounds
            }
        }

        if (e.button == M_BUTTON_RIGHT) {
            val picked = root.pick(view_pos, currentOperation, transform)
            if (picked != null) {
                picked.getContextMenu(view_pos).show(e.component, e.x, e.y)
                // TODO move context menu to scene
            } else {
                error("root did not catch our pick!")
            }
            currentOperation = when (picked) {
                is Node -> Operation.Menu
                is Port -> Operation.Menu
                is Edge -> Operation.Menu
                else -> Operation.None
            }
        }

        if (e.button == M_BUTTON_MIDDLE) {
            val picked = root.pick(view_pos, currentOperation, transform)
            if (picked != null) {
                var str = ""
                if (picked is Port)
                    str += "Port: "
                else if (picked is Edge)
                    str += "Edge: "
                else if (picked is Node)
                    str += "Node: "
                else
                    str += "<Unknown>: "
                str += "bounds " + picked.bounds + "\n\t"
                str += "external bounds " + picked.externalBounds()
                println(str)
            }
        }
        lastMovementPosition = view_pos
    }

    override fun mouseReleased(e: MouseEvent) {
        val view_pos = getSceneCoordinate(e)
        val picked = root.pick(view_pos, currentOperation, transform)
        println("mouseReleased picked $picked")

        val oldFocus = focusedElement
        focusedElement = null
        lastMovementPosition = null

        when (currentOperation) {
            Operation.DrawEdge -> {
                if (picked is Port && oldFocus is Port) {
                    val ancestor = getCommonAncestorForEdge(oldFocus, picked)
                    if (ancestor != null) {
                        val edge = Edge(Transform(0.0, 0.0, 1.0), ancestor, oldFocus, picked, this)
                        println("adding edge to ancestor $ancestor")
                        ancestor.addEdge(edge)
                        pushOperation(AddEdgeOperation(ancestor, edge))
                    }
                }
                currentOperation = Operation.None
            }
            Operation.Move -> {
                currentOperation = Operation.None
                val parent: Node? = oldFocus!!.parent
                if (parent != null) {
                    val bounds = oldFocus.getParentBoundsList()
                    pushOperation(MoveOperation(oldFocus, focusedElementOriginalParentBounds!!, focusedElementOriginalTransform!!, bounds, oldFocus.transform))
                }
            }
            Operation.Menu -> {
                //TODO menu is still active
                currentOperation = Operation.None
            }
            Operation.None -> {
            } //don't care
            Operation.Select -> {
            } //don't care
            Operation.OpenRustFile -> TODO()
        }
        focusedElementOriginalTransform = null
        repaint()
    }

    override fun mouseEntered(e: MouseEvent) {}

    override fun mouseExited(e: MouseEvent) {}

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
        val delta_pos = view_pos - lastMovementPosition!!
        lastMousePosition = getSceneCoordinate(e)
        lastMovementPosition = view_pos
        when (currentOperation) {
            Operation.Move -> {
                val target = focusedElement as Node
                target.moveGlobal(delta_pos)
            }
            Operation.DrawEdge -> {
                assert(focusedElement is Port)
                repaint()
            }
            Operation.Menu -> {
            } //don't care
            Operation.Select -> {
            } //don't care
            Operation.None -> error("drag without active operation")
        }
    }

    override fun mouseMoved(e: MouseEvent) {
        lastMousePosition = getSceneCoordinate(e)
        repaint()
    }

    override fun componentHidden(e: ComponentEvent?) { /*don't care*/
    }

    override fun componentMoved(e: ComponentEvent?) {
        println(e)
    }

    override fun componentShown(e: ComponentEvent?) { /*don't care*/
    }

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

    override fun keyTyped(e: KeyEvent?) {
    }

    override fun keyPressed(e: KeyEvent?) {
        if ((e!!.keyCode == KeyEvent.VK_Z) && e.modifiers and KeyEvent.CTRL_MASK != 0) {
            popOperation()
        }
        if ((e!!.keyCode == KeyEvent.VK_Y) && e.modifiers and KeyEvent.CTRL_MASK != 0) {
            popReverseOperation()
        }
    }

    override fun keyReleased(e: KeyEvent?) {
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
