package editor

import java.awt.event.ActionEvent
import javax.swing.AbstractAction

class UndoAction(val scene: Viewport) : AbstractAction() {
    override fun actionPerformed(p0: ActionEvent?) {
        println("UndoAction")
        scene.popOperation()
    }
}

class RedoAction(val scene: Viewport) : AbstractAction() {
    override fun actionPerformed(p0: ActionEvent?) {
        println("RedoAction")
        scene.popReverseOperation()
    }
}