package editor

import codegen.pascalToSnakeCase
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.*
import java.nio.file.Path
import java.nio.file.Paths

const val DEFAULT_FILE_ENDING = "rs"
const val NODES_RELATIVE_PATH = "/src"
const val TRASH_RELATIVE_PATH = "/src/.trash"

class ImplementationNode(private val node:Node) {

    private val parent get() = node.parent
    private val childNodes get() = node.childNodes
    private val name get() = node.name

    private val project get() = node.scene.editor!!.project

    private val projectDirectory: String get() = project.basePath!!

    private val trashDirectory: String get() = with(parent?.impl) {
        when (this) {
            null -> TRASH_RELATIVE_PATH
            else -> "$trashDirectory/$moduleName"
        }
    }

    private val sourceDirectory: String get()  = with(parent?.impl) {
        when (this) {
            null -> NODES_RELATIVE_PATH
            else -> "$sourceDirectory/$moduleName"
        }
    }

    private val moduleName: String get() = when(node) {
        is RootNode -> "nodes"
        else -> pascalToSnakeCase(name)
    }

    private val sourceFile: Path get() = Paths.get("$projectDirectory/$sourceDirectory/$moduleName.$DEFAULT_FILE_ENDING")
    private val moduleDirectory: Path get() = Paths.get("$projectDirectory/$sourceDirectory/$moduleName")

    private val trashFile: Path get() = Paths.get("$projectDirectory/$trashDirectory/$moduleName.$DEFAULT_FILE_ENDING")

    fun getOrCreateFile(): VirtualFile {
        val file = with(sourceFile) {
            if (!exists()) {
                createFile()
            }
            toFile()
        }
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)!!
    }

    fun rename(newName:String) {
        val newModuleName = pascalToSnakeCase(newName)
        when {
            sourceFile.exists() -> {
                val newFile = Paths.get("$projectDirectory/$sourceDirectory/$newModuleName.$DEFAULT_FILE_ENDING")
                sourceFile.move(newFile)
            }
            moduleDirectory.isDirectory() -> {
                val newDirectory = Paths.get("$projectDirectory/$sourceDirectory/$newModuleName")
                moduleDirectory.move(newDirectory)
            }
            else -> {}
        }
        LocalFileSystem.getInstance().refresh(false)
    }

    fun moveToTrash() {
        // do it for all the child nodes first
        childNodes.forEach {n ->
            n.impl.moveToTrash()
        }
        // if a rust file for the node exists, move it to the trash dir
        val nodePath = sourceFile
        if (nodePath.exists()) {
            trashFile.parent.createDirectories()
            nodePath.move(trashFile)
            LocalFileSystem.getInstance().refresh(false)
        }
    }

    fun retreiveFromTrash() {
        // if a rust file exists in the trash we are going to retreive it
        if (trashFile.exists()){
            trashFile.move(sourceFile)
            LocalFileSystem.getInstance().refresh(false)
        }
        // now retreive all children
        childNodes.forEach {n ->
            n.impl.retreiveFromTrash()
        }
    }
}