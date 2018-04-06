package editor

import java.awt.event.ActionEvent
import javax.swing.AbstractAction

class UndoAction(val scene: Viewport) : AbstractAction() {
    override fun actionPerformed(p0: ActionEvent?) {
        scene.popOperation()
    }
}

class RedoAction(val scene: Viewport) : AbstractAction() {
    override fun actionPerformed(p0: ActionEvent?) {
        scene.popReverseOperation()
    }
}

class PressSpaceAction(val scene: Viewport) : AbstractAction() {
    override fun actionPerformed(p0: ActionEvent?) {
        scene.spaceBarPressed = true
    }
}

class ReleaseSpaceAction(val scene: Viewport) : AbstractAction() {
    override fun actionPerformed(p0: ActionEvent?) {
        scene.spaceBarPressed = false
    }
}