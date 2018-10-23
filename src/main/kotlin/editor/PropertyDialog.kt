package editor

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.*
import javax.swing.*
import java.awt.GridLayout


class PropertyDialog(val node: Node) : DialogWrapper(node.scene.editor?.project) {

    private val contextField = JBTextField(node.context.asExpression())
    val context: Context get() = Context.parse(contextField.text)
    private val filterField = JBTextField(node.filterExpression)
    val filter: String get() = filterField.text
    private val orderField = JBTextField(node.orderExpression)
    val order: String get() = orderField.text

    init {
        super.init()
        title = "Properties for ${node.name}"
    }

    override fun createCenterPanel(): JComponent? {

        val panel = JPanel(GridLayout(3, 2))

        val contextLabel = JLabel("Context:") //TODO JBLabel
        contextLabel.labelFor = contextField

        val filterLabel = JLabel("Filter:")
        filterLabel.labelFor = filterField

        val orderLabel = JLabel("Order:")
        orderLabel.labelFor = orderField

        with(panel) {
            add(contextLabel)
            add(contextField)
            add(filterLabel)
            add(filterField)
            add(orderLabel)
            add(orderField)
        }

        return panel
    }
}