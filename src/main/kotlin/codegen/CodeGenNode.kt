package codegen

import editor.ContextType
import editor.Node

class CodeGenNode(val node: Node) {
    val parent get() = node.parent
    val childNodes get() = node.childNodes
    val structName get() = node.name

    val moduleName: String get() = pascalToSnakeCase(structName)
    val nodeHandle: String get() = moduleName

    fun generate(code: Scope) {
        check(nodeHandle != "<anonymous>" && ! nodeHandle.isEmpty()) {"Can't generate an anonymous node."}
        val innerBlock = CodeBlock()
        var prefixHandle = when {
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

        if (node.hasFilter) {
            val filterHandle = "${nodeHandle}_filter"
            innerBlock.define(filterHandle,
                        "$prefixHandle\n$TAB.map(|event| { ${node.filterExpression} })")
            prefixHandle = filterHandle
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
                if (node.hasContext) {
                    val contextStruct = node.context.structName
                    innerBlock.define("context", "$moduleName::$contextStruct::new()")
                }
                when(node.context.type) {
                    /*TODO new syntax:
                           //let aircraft_contexts = global_context(adsb_stream, || {AircraftCollection::new(receiver_db.clone())});
        let aircraft_contexts = selective_context(adsb_stream,
                                                  |icao_address: &u32| { AircraftState::new(*icao_address) },
                                                  |event| {
                                                      let (_receiver, icao) = event.source;
                                                      icao.into()
                                                  },
                                                  |state, event| { state.process(event) }
        );
                     */
                    ContextType.Selection -> TODO("selective context")
                    ContextType.Global -> innerBlock.define(stageHandle,"GlobalContext::new($prefixHandle, context)")
                    ContextType.None -> innerBlock.define(stageHandle,
                        "$prefixHandle\n$TAB.map(|event| { $moduleName::tick(event) })")
                }
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