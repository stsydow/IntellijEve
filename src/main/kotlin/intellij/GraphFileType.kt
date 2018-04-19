package intellij

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * Created by Benni on 13.11.2016.
 */
class GraphFileType private constructor() : FileType {
    override fun getName(): String {
        return "EVEaMCP Graph"
    }

    override fun getDescription(): String {
        return "a EVEaMCP Graph File"
    }

    override fun getDefaultExtension(): String {
        return "eve"
    }

    override fun getIcon(): Icon? {
        return null
    }

    override fun isBinary(): Boolean {
        return false
    }

    override fun isReadOnly(): Boolean {
        return false
    }

    override fun getCharset(virtualFile: VirtualFile, bytes: ByteArray): String? {
        return null
    }

    companion object {
        val instance = GraphFileType()
    }
}
