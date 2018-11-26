package editor

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.*
import java.awt.GridBagConstraints
import javax.swing.*
import java.awt.GridBagLayout


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

        val panel = JPanel(GridBagLayout())

        var rowCount = 0;
        val add = {labelText:String, text: JBTextField ->
            val constraints = GridBagConstraints()
            constraints.gridy = rowCount

            constraints.gridx = 0
            constraints.anchor = GridBagConstraints.LINE_START

            val label = JBLabel(labelText)
            label.labelFor = contextField
            panel.add(label, constraints)

            constraints.gridx = 1
            panel.add(text, constraints)

            rowCount += 1
        }

        add("Context:", contextField)
        add("ContextStruct:", contextStructField)
        add("Filter:",filterField)
        add("Order:", orderField)

        return panel
    }
}