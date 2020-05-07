package intellij

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.CharsetToolkit;
import javax.swing.Icon

/**
 * Created by Benni on 13.11.2016.
 */


class GraphFileType private constructor() : FileType {

    companion object {
        const val FILE_EXENSION:String = "eve"
        val instance = GraphFileType()
    }

    override fun getName(): String {
        return "EVEaMCP Graph"
    }

    override fun getDescription(): String {
        return "a EVEaMCP Graph File"
    }

    override fun getDefaultExtension(): String {
        return GraphFileType.FILE_EXENSION
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
        return CharsetToolkit.UTF8;
    }

}
