import editor.Node
import editor.Viewport
import java.awt.BorderLayout
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * Created by Stefan Sydow on 26.03.17.
 */
object DemoLauncher {
    @JvmStatic fun main(args: Array<String>) {

        val plaf = UIManager.getSystemLookAndFeelClassName() //"-Dswing.defaultlaf=com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel"
        UIManager.setLookAndFeel(plaf)

        val frame = JFrame("Demo")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

        val viewport = Viewport(null)
        viewport.layout = BorderLayout()
        frame.contentPane = viewport

        Node(Node(viewport.root, viewport), viewport)

        JFrame.setDefaultLookAndFeelDecorated(true)
        SwingUtilities.updateComponentTreeUI(frame)

        frame.pack()
        frame.isVisible = true
    }
}
