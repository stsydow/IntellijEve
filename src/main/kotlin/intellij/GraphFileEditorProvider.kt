package intellij

import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jdom.Element


class GraphFileEditorProvider: FileEditorProvider, DumbAware {
    override fun accept(project: Project, virtualFile: VirtualFile): Boolean {
        val ext = virtualFile.extension
        return if (ext != null && ext == "eve") true else false
    }

    override fun createEditor(project: Project, virtualFile: VirtualFile): FileEditor {
        return GraphFileEditor(project, virtualFile)
    }

    override fun getEditorTypeId(): String {
        return "EventGraphEditor"
    }

    override fun getPolicy(): FileEditorPolicy {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR
    }

    override fun readState(sourceElement: Element, project: Project, file: VirtualFile): FileEditorState {
        return object: FileEditorState {
            override fun canBeMergedWith(var1: FileEditorState, var2: FileEditorStateLevel): Boolean {
                return false;
            }
        }
    }
}