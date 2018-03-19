package intellij

import codegen.RustCodeGenerator
import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import org.apache.commons.io.input.BOMInputStream
import editor.Viewport
import graphmlio.read
import graphmlio.write
import graphmlio.validate
import java.beans.PropertyChangeListener
import java.io.File
import java.io.FileInputStream
import javax.swing.JComponent

class GraphFileEditor(val project: Project, val virtualFile: VirtualFile): UserDataHolderBase(), FileEditor {
    val panel: Viewport = Viewport(this)

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
            }
        }
    }

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

    fun generate() {
        try {
            val gen = RustCodeGenerator()
            val path = project.baseDir.path
            gen.generateSkeleton(path)
            gen.generateCode(panel.root, path)
        } catch(e: Exception) {
            e.printStackTrace()
        }
    }

    fun save() {
        write(virtualFile.path, panel.root)
    }

    /*
        Method that checks whether a file is empty while considering UTF-8 Byte Order Marks
        as suggested in
        https://stackoverflow.com/questions/1835430/byte-order-mark-screws-up-file-reading-in-java/1835529#1835529
     */
    fun fileIsEmpty(file: File): Boolean {
        val inStream = FileInputStream(file)
        val bomInStream = BOMInputStream(inStream, false)
        // file is empty if first read attempt after opening the stream returns -1
        return (bomInStream.read() < 0)
    }
}