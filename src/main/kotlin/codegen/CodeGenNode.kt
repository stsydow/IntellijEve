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
    val nodeHandle: String get() = moduleName

    val sinkHandle: String get() {
        require(node.isSink)
        return "${nodeHandle}_sink"
    }

    fun generate(code: Scope) {
        check(nodeHandle != "<anonymous>" && ! nodeHandle.isEmpty()) {"Can't generate an anonymous node."}
        val innerBlock = CodeBlock()
        val prefixHandle = when {
            node.isFanIn -> {
                val inputs = node.predecessors.map { i -> i.codeGen.getOutputHandle(this) }
                val mergeHandle = "${nodeHandle}_merge"
                innerBlock.define(mergeHandle, inputs.joinToString(")\n$TAB.select(", "(", ")"))
                mergeHandle
            }
            node.isSource -> {"(invalid)"}
            else ->{
                node.predecessors.first().codeGen.getOutputHandle(this)
            }
        }

        val stageHandle = when {
            node.childNodes.any() -> {
                TODO("Hierarchic node $nodeHandle")
            }
            node.isSource -> {
                check(!node.isSink)
                val sourceHandle = "${nodeHandle}_source"
                innerBlock.define(sourceHandle,"$moduleName::$structName::new()")
                sourceHandle
            }
            node.isSink -> {
                //TODO("replace drop sink")
                val sinkDropHandle = "${nodeHandle}_sink_drop"
                innerBlock.define(sinkDropHandle,
                        "$prefixHandle.for_each(|_| ok(()))")
                sinkDropHandle
            }
            else -> {
                val stageHandle = "${nodeHandle}_stage"
                innerBlock.define(stageHandle,
                        "$prefixHandle\n$TAB.map(|event| { $moduleName::tick(event) })")
                stageHandle
            }
        }

        when {
            node.isFanOut -> {
                val copyHandle = "${nodeHandle}_copy"

                innerBlock.result("StreamCopyMutex::new(${stageHandle})")
                code.defineFromBlock(copyHandle, innerBlock)
                node.successors.forEach { s ->
                    val outHandle = "${nodeHandle}_out_${s.codeGen.nodeHandle}"
                    code.define(outHandle, "$copyHandle.create_output_locked()")
                }
            }
            else -> {
                innerBlock.result(stageHandle)
                code.defineFromBlock(nodeHandle, innerBlock)
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