package editor

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.*
import javax.swing.JComponent


//TODO use an com.intellij.ui.EditorTextField for Context Struct, selector and init

class PropertyDialog(val node: Node) : DialogWrapper(node.scene.editor?.project) {

    private val contextField = JBTextField(node.context.asExpression(), 25)
    private val contextStructField = JBTextField(node.context.structName, 25)
    val context: Context get() = Context.parse(contextField.text, contextStructField.text)
    private val filterField = JBTextField(node.filterExpression,25)
    val filter: String get() = filterField.text
    private val orderField = JBTextField(node.orderExpression, 15)
    val order: String get() = orderField.text

    init {
        super.init()
        title = "Properties for ${node.name}"
    }

    override fun createCenterPanel(): JComponent? {
        return panel {
            row("Context:") { contextField(grow) }
            row("ContextStruct:") { contextStructField(grow) }
            row("Filter:") { filterField(grow) }
            row("Order:") { orderField(grow) }
        }
    }
}