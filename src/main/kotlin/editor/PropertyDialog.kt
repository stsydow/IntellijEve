package editor

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.*
import javax.swing.*
import java.awt.GridLayout


class PropertyDialog(val node: Node) : DialogWrapper(node.scene.editor?.project) {

    private val contextField = JBTextField(node.context.asExpression(), 25)
    private val contextStructField = JBTextField(node.context.structName, 25)
    val context: Context get() = Context.parse(contextField.text, contextStructField.text)
    private val filterField = JBTextField(node.filterExpression,25)
    val filter: String get() = filterField.text
    private val orderField = JBTextField(node.orderExpression)
    val order: String get() = orderField.text

    init {
        super.init()
        title = "Properties for ${node.name}"
    }

    override fun createCenterPanel(): JComponent? {

        // TODO use GridBagLayout
        val panel = JPanel(GridLayout(4, 2))

        val contextLabel = JLabel("Context:") //TODO JBLabel
        contextLabel.labelFor = contextField

        val contextStructLabel = JLabel("ContextStruct:") //TODO JBLabel
        contextStructLabel.labelFor = contextStructField

        val filterLabel = JLabel("Filter:")
        filterLabel.labelFor = filterField

        val orderLabel = JLabel("Order:")
        orderLabel.labelFor = orderField

        with(panel) {
            add(contextLabel)
            add(contextField)
            add(contextStructLabel)
            add(contextStructField)
            add(filterLabel)
            add(filterField)
            add(orderLabel)
            add(orderField)
        }

        return panel
    }
}