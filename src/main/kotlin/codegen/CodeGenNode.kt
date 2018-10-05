package codegen

import editor.Node

const val ERROR_TYPE = "EveError"
fun streamType(itemType: String) = "impl Stream<Item=$itemType, Error=${ERROR_TYPE}>"

const val TAB = "    "
val tabs = {n:Int -> "".padStart(n* TAB.length)}

class CodeGenNode(val node: Node) {
    val parent get() = node.parent
    val childNodes get() = node.childNodes
    val structName get() = node.name

    val moduleName: String get() = pascalToSnakeCase(structName)
    val nodeHandle = moduleName

    val sinkHandle: String get() {
        require(node.isSink)
        return "${nodeHandle}_sink"
    }

    fun generate(code: CodeBlock) {
        val inner_block = CodeBlock()
        val prefixHandle = when {
            node.isFanIn -> {
                val inputs = node.predecessors.map { i -> i.codeGen.getOutputHandle(this) }
                val mergeHandle = "${nodeHandle}_merge"
                inner_block.define(mergeHandle, inputs.joinToString(")\n$TAB.select(", "(", ")"))
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
                val sourceHandle = "${nodeHandle}_source"
                inner_block.define(sourceHandle,"$moduleName::$structName::new()")
                sourceHandle
            }
            node.isSink -> {
                //TODO("replace drop sink")
                val sinkDropHandle = "${nodeHandle}_sink_drop"
                inner_block.define(sinkDropHandle,
                        "$prefixHandle.map(|_| ())")
                sinkDropHandle
            }
            else -> {
                val stageHandle = "${nodeHandle}_stage"
                inner_block.define(stageHandle,
                        "$prefixHandle\n$TAB.map(|event| { $moduleName::tick(event) })")
                stageHandle
            }
        }

        when {
            node.isFanOut -> {
                val copyHandle = "${nodeHandle}_copy"

                inner_block.result("StreamCopyMutex::new(${stageHandle})")
                code.defineFromBlock(copyHandle, inner_block)
                node.successors.forEach { s ->
                    val outHandle = "${nodeHandle}_out_${s.codeGen.nodeHandle}"
                    code.define(outHandle, "$copyHandle.create_output_locked()")
                }
            }
            node.isSink -> {
                inner_block.result(stageHandle)
                //code.define(sinkHandle, "$stageHandle.map(|_| ())")

                TODO("build a proper sink")
            }
            else -> {
                inner_block.result(stageHandle)
                code.defineFromBlock(nodeHandle, inner_block)
            }
        }
    }

    fun getOutputHandle(target: CodeGenNode) : String {
        require(node.successors.contains(target.node))
        return when {
            node.isFanOut -> {
                "${nodeHandle}_out_${target.nodeHandle}"
            }
            else -> nodeHandle
        }
    }

}