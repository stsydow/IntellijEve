package editor

import codegen.pascalToSnakeCase
import java.util.*

const val ERROR_TYPE = "EveError"
fun streamType(itemType: String) = "impl Stream<Item=$itemType, Error=$ERROR_TYPE>"

const val TAB = "    "
val tabs = {n:Int -> "".padStart(n* TAB.length)}

class CodeFragment {

    class Definition(val identifier:String, val expression: String) {
        override fun toString() = "let $identifier = $expression;\n"
    }
    private val definitions = mutableListOf<Definition>()
    private val known_indentifiers = mutableSetOf<String>()

    fun define(identifier:String, expression:String) {
        require(known_indentifiers.add(identifier)){"identifier allready defined"}
        definitions.add(Definition(identifier, expression))
    }

    override fun toString() = definitions.joinToString("", "{", "}") { def -> def.toString() }
}

class CodeGenNode(val node:Node) {
    val parent get() = node.parent
    val childNodes get() = node.childNodes
    val structName get() = node.name

    val moduleName: String get() = pascalToSnakeCase(structName)
    val nodeHandle = moduleName

    val sourceHandle: String get() {
        require(node.isSource)
        return "${nodeHandle}_source"
    }

    val sinkHandle: String get() {
        require(node.isSink)
        return "${nodeHandle}_sink"
    }

    fun generate(code: CodeFragment) {
        val prefixHandle = when {
            node.isFanIn -> {
                val inputs = node.predecessors.map { i -> i.codeGen.getOutputHandle(this) }
                val mergeHandle = "${nodeHandle}_merge"
                code.define(mergeHandle, inputs.joinToString(")\n$TAB.select(", "(", ")"))
                mergeHandle
            }
            else ->{
                node.predecessors.first().codeGen.getOutputHandle(this)
            }
        }

        val stageHandle = when {
            node.childNodes.any() -> {
                TODO("Hierarchic")
            }
            node.isSource -> {
                check(!node.isSink)
                code.define(sourceHandle,"$moduleName::$structName::new()")
                code.define(nodeHandle, sourceHandle)
                sourceHandle
            }
            node.isSink -> {
                TODO("sink")
            }
            else -> {
                val stageHandle = "${nodeHandle}_stage"
                code.define(stageHandle,
                        "$prefixHandle\n$TAB.map(|event| { $moduleName::tick(event) })")
                stageHandle
            }
        }

        val postfixHandle = when {
            node.isFanOut -> {
                val copyHandle = "${nodeHandle}_copy"
                code.define(copyHandle, "StreamCopyMutex::new(${stageHandle})")
                node.successors.forEach { s ->
                    val outHandle = "${nodeHandle}_out_${s.codeGen.nodeHandle}"
                    code.define(outHandle, "$copyHandle.create_output_locked()")
                }
                copyHandle
            }
            node.isSink -> {
                code.define(sinkHandle, "$sinkHandle.map(|_| ())")
                sinkHandle
            }
            else -> {
                stageHandle
            }
        }

        code.define(nodeHandle, postfixHandle)
    }

    fun getOutputHandle(target:CodeGenNode) : String {
        require(node.successors.contains(target.node))
        return when {
            node.isFanOut -> {
                "${nodeHandle}_out_${target.nodeHandle}"
            }
            else -> nodeHandle
        }
    }

}