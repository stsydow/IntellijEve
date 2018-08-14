package editor

import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import java.awt.Dimension
import java.awt.event.*
import javax.swing.*
import javax.swing.JDialog
import javax.swing.JButton

class ListDialog(title: String, options: List<Property>) : JDialog() {
    val textField = JBTextField()

    init {
        modalityType = ModalityType.APPLICATION_MODAL
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        this.title = title
        this.minimumSize = Dimension(300, 300)

        //Ensure the text field always gets the first focus.
        addComponentListener(object : ComponentAdapter() {
            override fun componentShown(ce: ComponentEvent?) {
                textField.requestFocusInWindow()
            }
        })

        val clrButton = JButton("Clear")
        clrButton.addActionListener {
            selection = null
            textField.text = ""
        }

        val listModel = DefaultListModel<String>()
        options.forEach { it -> listModel.addElement(it.expression) }
        val optionListView = JBList(listModel)
        optionListView.selectionMode = ListSelectionModel.SINGLE_SELECTION
        optionListView.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val selected = optionListView.selectedValue
                textField.text = selected
            }
        }
        val listScrollPane = JBScrollPane(optionListView)

        val components = arrayOf(textField, clrButton, listScrollPane)
        val buttons = arrayOf("Select", "Cancel")
        val optionPane = JOptionPane(components,
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION,
                null,
                buttons, buttons[0])
        optionPane.addPropertyChangeListener { e ->
            val prop = e.propertyName
            val value = e.newValue
            if (this.isVisible
                    && e.source === optionPane
                    && prop == JOptionPane.VALUE_PROPERTY) {
                if (value == buttons[0]) {
                    selection = textField.text
                } else {
                    selection = null
                }
                this.isVisible = false
            }
        }
        contentPane = optionPane
    }

    var selection: String? = null

    fun setInitialSelection(text: String){
        selection = text
        textField.text = text
    }
}