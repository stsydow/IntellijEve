package intellij

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import editor.Viewport
//import editor.Viewport
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel


class GraphFileEditor(val project: Project, val virtualFile: VirtualFile): UserDataHolderBase(), FileEditor {
    //val panel: JPanel = JPanel()
    val panel: Viewport = Viewport()

    override fun isModified(): Boolean {
        return false
    }

    override fun addPropertyChangeListener(p0: PropertyChangeListener) {

    }

    override fun getName(): String {
        return "intellij.GraphFileEditor"
    }

    override fun setState(p0: FileEditorState) {

    }

    override fun getComponent(): JComponent {
        return panel;
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return panel;
    }

    override fun selectNotify() {

    }

    override fun getCurrentLocation(): FileEditorLocation? {
        return null;
    }

    override fun deselectNotify() {

    }

    override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? {
        return null;
    }

    override fun isValid(): Boolean {
        return true
    }

    override fun removePropertyChangeListener(p0: PropertyChangeListener) {

    }

    override fun dispose() {

    }
}