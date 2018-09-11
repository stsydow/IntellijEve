package intellij

import codegen.RustAsyncCodeGen
import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import editor.Viewport
import graphmlio.read
import graphmlio.validate
import graphmlio.write
import org.apache.commons.io.input.BOMInputStream
import java.beans.PropertyChangeListener
import java.io.File
import java.io.FileInputStream
import javax.swing.JComponent

private fun fileIsEmpty(file: File): Boolean {
    val inStream = FileInputStream(file)
    val bomInStream = BOMInputStream(inStream, false)
    // file is empty if first read attempt after opening the stream returns -1
    return (bomInStream.read() < 0)
}

class GraphFileEditor(val project: Project, private val virtualFile: VirtualFile): UserDataHolderBase(), FileEditor {

    private val panel: Viewport = Viewport(this)

    init {
        val file = File(virtualFile.path)
        if (!fileIsEmpty(file)) {
            if (!validate(file))
                println("Warning, given file could not be validated as graphml!")
            panel.idx = 0
            val newRoot = read(file, panel)
            if (newRoot != null) {
                panel.root = newRoot
                panel.repaint()
                panel.save()
            }
        }
    }

    override fun isModified(): Boolean {
        return false
    }

    override fun addPropertyChangeListener(p0: PropertyChangeListener) { }

    override fun getName(): String = javaClass.name //"intellij.GraphFileEditor"

    override fun setState(p0: FileEditorState) { }

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent? = panel

    override fun selectNotify() { }

    override fun getCurrentLocation(): FileEditorLocation? = null


    override fun deselectNotify() { }

    override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = null

    override fun isValid(): Boolean {
        return true
    }

    override fun removePropertyChangeListener(p0: PropertyChangeListener) { }

    override fun dispose() { }

    fun generate() {
        try {
            val path = project.baseDir.path
            RustAsyncCodeGen.generateSkeleton(path)
            RustAsyncCodeGen.generateCode(panel.root, path)
        } catch(e: Exception) {
            e.printStackTrace()
        }
    }

    fun save() {
        write(virtualFile.path, panel.root)
    }

}