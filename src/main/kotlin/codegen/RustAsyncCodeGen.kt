package codegen

import editor.Node
import editor.RootNode
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths

const val ERROR_TYPE = "EveError"
val streamType = {itemType: String -> "impl Stream<Item=$itemType, Error=$ERROR_TYPE>"}

abstract class ReducedGraphNode(val name: String) {
    // TODO get rid of the recursion here (handledElements Set too) - see traverseGraph
    abstract fun generateInstantiationCode(builder: StringBuilder, handledElements: MutableSet<String>)
}

class Pipeline(name: String,
               private val firstNode: Node, private val lastNode: Node,
               private val predecessor: ReducedGraphNode?, var successor: ReducedGraphNode?) :
        ReducedGraphNode(name), Iterable<Node> {

    private class PipelineIterator(start:Node, end:Node): Iterator<Node> {
        private var currentNode:Node = start
        private var endNode:Node = end

        override fun hasNext(): Boolean = currentNode.successors.any()

        override fun next(): Node {
            assert(currentNode == endNode || currentNode.successors.count() == 1)
            val result = currentNode.successors.first()
            currentNode = result
            return result
        }
    }

    override operator fun iterator(): Iterator<Node> {
        return (Pipeline::PipelineIterator)(firstNode, lastNode)
    }

    override fun generateInstantiationCode(builder: StringBuilder, handledElements: MutableSet<String>) {
        if (name in handledElements) {
            return
        }

        predecessor?.generateInstantiationCode(builder, handledElements)
        if (name in handledElements) {
            return
        }

        val inputConnector = when (predecessor) {
            null -> ""
            is Pipeline -> name
            is Merge -> name
            is Copy -> "$name.create_output_locked()"
            else -> error("unknown element type")
        }

        builder.appendln("let $name = $name($inputConnector);")
        handledElements.add(name)

        successor?.generateInstantiationCode(builder, handledElements)
    }

    val isSource: Boolean get() = firstNode.isSource
    val isSink: Boolean get() = lastNode.isSink

    fun generatePipeline(): String {
        val builder = StringBuilder()

        val resultType = if (isSink) "()" else streamType(lastNode.out_ports.first().message_type)

        val pipelineName = "pipeline_${firstNode.id}_${lastNode.id}"


        if (isSource) {
            builder.appendln("pub fn $pipelineName() -> $resultType {")
        } else {
            val inputType = streamType(firstNode.in_port.message_type)
            builder.append("""
            pub fn $pipelineName(stream: $inputType) -> $resultType} {
                stream"""
            )
        }

        for (node in this){
            val nodeName = node.name
            val moduleName = pascalToSnakeCase(nodeName)
            if (node.isSource) {
                builder.appendln("$moduleName::$nodeName::new()")
            } else {
                builder.appendln("  .map(|event| { $moduleName::tick(event) })")
            }
        }
        builder.appendln("}")
        return builder.toString()
    }
}

class Merge(name: String, val inputNodes: MutableList<ReducedGraphNode>, var outputNode: ReducedGraphNode?) : ReducedGraphNode(name) {
    override fun generateInstantiationCode(builder: StringBuilder, handledElements: MutableSet<String>) {
        if (name in handledElements) {
            return
        }

        inputNodes.forEach {
            it.generateInstantiationCode(builder, handledElements)
        }
        if (name in handledElements) {
            return
        }
        builder.appendln("let $name = ${inputNodes[0].name}")
        for (i in 1 until inputNodes.count() - 1) {
            builder.appendln(".select(${inputNodes[i].name})")
        }
        builder.append(";")
        handledElements.add(name)
        outputNode?.generateInstantiationCode(builder, handledElements)
    }
}

class Copy(name: String, val inputNode: ReducedGraphNode, val outputNodes: MutableList<ReducedGraphNode>) : ReducedGraphNode(name) {
    override fun generateInstantiationCode(builder: StringBuilder, handledElements: MutableSet<String>) {
        if (name in handledElements) {
            return
        }

        builder.append(
                """
                    let $name = StreamCopyMutex {
                        inner: Arc::new(Mutex::new(StreamCopy {
                            input: ${inputNode.name},
                            buffers: vec!(),
                            idx: 0
                        }
                    ))};
                    """
        )
        handledElements.add(name)
        outputNodes.forEach {
            it.generateInstantiationCode(builder, handledElements)
        }
    }
}

class ReducedGraph(val pipelines: List<Pipeline>, val merges: List<Merge>)

class GraphBuilder(nodes: Collection<Node>) {
    private val pipelines =  HashMap<String, Pipeline>()
    private val merges = HashMap<String, Merge>()
    private val copies = HashMap<String, Copy>()

    init {
        val sourceNodes = nodes.filter { n -> n.isSource }
        sourceNodes.forEach {
            traverseGraph(it, it, null)
        }
    }

    fun asReducesGraph():ReducedGraph = ReducedGraph(ArrayList(this.pipelines.values), ArrayList(this.merges.values))

    /*
     TODO: brake this up
      */
    private fun traverseGraph(sourceNode: Node, currentNode: Node, predecessor: ReducedGraphNode?) {
        if (currentNode.isSink) { // sink
            val pipelineId = "pipeline_${sourceNode.id.toLowerCase()}_${currentNode.id.toLowerCase()}"
            if (!pipelines.containsKey(pipelineId)) {
                val pipeline = Pipeline(pipelineId, sourceNode, currentNode, predecessor, null)
                pipelines[pipeline.name] = pipeline
                if (predecessor is Merge) {
                    predecessor.outputNode = pipeline
                } else if (predecessor is Copy) {
                    if (pipeline !in predecessor.outputNodes) {
                        predecessor.outputNodes.add(pipeline)
                    }
                }
            }
        } else if (currentNode.outgoingEdges.count() == 1) { // pipeline...
            val next = currentNode.successors.first()
            if (next.in_port.incommingEdges.count() == 1) { // ... continues with a normal successor
                traverseGraph(sourceNode, next, predecessor)
            } else { // ... stops with a merge
                val pipelineId = "pipeline_${sourceNode.id.toLowerCase()}_${currentNode.id.toLowerCase()}"
                val mergeId = "merge_${next.id.toLowerCase()}"

                val pipeline = when (pipelines.containsKey(pipelineId)) {
                    true -> pipelines[pipelineId]!!
                    false -> Pipeline(pipelineId, sourceNode, currentNode, predecessor, null)
                }
                val merge = when (merges.containsKey(mergeId)) {
                    true -> merges[mergeId]!!
                    false -> Merge(mergeId, mutableListOf(), null)
                }

                if (pipeline !in merge.inputNodes) {
                    merge.inputNodes.add(pipeline)
                }
                pipeline.successor = merge

                merges[mergeId] = merge
                pipelines[pipelineId] = pipeline

                if (predecessor is Merge) {
                    predecessor.outputNode = pipeline
                } else if (predecessor is Copy) {
                    if (pipeline !in predecessor.outputNodes) {
                        predecessor.outputNodes.add(pipeline)
                    }
                }

                traverseGraph(next, next, merge)
            }
        } else { // Copy
            val copyid = "copy_${currentNode.id.toLowerCase()}"
            when (predecessor) {
                is Pipeline -> {
                    val pipelineId = "pipeline_${sourceNode.id.toLowerCase()}_${currentNode.id.toLowerCase()}"
                    val pipeline = when (pipelines.containsKey(pipelineId)) {
                        true -> pipelines[pipelineId]!!
                        false -> Pipeline(pipelineId, sourceNode, currentNode, predecessor, null)
                    }

                    val copy = when (copies.containsKey(copyid)) {
                        true -> copies[copyid]!!
                        false -> Copy(copyid, pipeline, mutableListOf())
                    }

                    copies[copyid] = copy
                    pipelines[pipelineId] = pipeline

                    currentNode.successors.forEach {
                        traverseGraph(it, it, copy)
                    }
                }
                is Merge -> {
                    val pipelineId = "pipeline_${currentNode.id.toLowerCase()}_${currentNode.id.toLowerCase()}"
                    val mergeId = predecessor.name

                    val merge = merges[mergeId]!!

                    val pipeline = when (pipelines.containsKey(pipelineId)) {
                        true -> pipelines[pipelineId]!!
                        false -> Pipeline(pipelineId, currentNode, currentNode, predecessor, null)
                    }

                    val copy = when (copies.containsKey(copyid)) {
                        true -> copies[copyid]!!
                        false -> Copy(copyid, pipeline, mutableListOf())
                    }

                    copies[copyid] = copy
                    merge.outputNode = pipeline
                    pipeline.successor = copy
                    pipelines[pipelineId] = pipeline

                    currentNode.successors.forEach {
                        traverseGraph(it, it, copy)
                    }
                }
                else -> error("copy invalid predecessor")
            }
        }
    }
}

class RustAsyncCodeGen {
    companion object {
        private fun getTemplate(filePath:String): URL {
            val classloader = this::class.java.classLoader // the inteliJ ClassLoader is quit brittle
            return classloader.getResource("/templates/$filePath")
        }

        fun generateSkeleton(outputDirectory: String) {

        }

        fun generateCode(rootGraph: RootNode, outputDirectory: String) {
            println("[RustCodeGenerator] generating code...")
            val srcPath = Paths.get("$outputDirectory/src")
            val nodesPath =  Paths.get("$srcPath/nodes")
            Files.createDirectories(nodesPath)

            val streamCopyPath = Paths.get("$srcPath/stream_copy.rs")
            if (!Files.exists(streamCopyPath)) {
                val templateUrl = getTemplate("src/stream_copy.rs")
                val byteStream = templateUrl.openStream()
                Files.copy(byteStream, streamCopyPath)
            }
            val graphs: Collection<Node> = rootGraph.flattenChildGraphs()
            generateTaskGraph(graphs, outputDirectory)
        }
    }
}


    private fun generateTaskGraph(nodes: Collection<Node>, outputDirectory: String) {
        val graph = GraphBuilder(nodes).asReducesGraph()
        val builder = StringBuilder()
        builder.append("""
extern crate futures;
extern crate tokio_core;

mod nodes;
mod structs;

use tokio_core::reactor::Core;
use futures::Future;
use futures::future::ok;
use futures::Poll;
use futures::Stream;
use futures::Async;

use std::sync::Arc;
use std::sync::Mutex;
use std::clone::Clone;
use std::sync::MutexGuard;

use structs::*;
""")
        nodes.forEach {
            if (it.childNodes.count() == 0) {
                val moduleName = pascalToSnakeCase(it.name)
                builder.appendln("use nodes::$moduleName;")
            }
        }
        builder.append("""

pub fn main() {
    let mut core = Core::new().unwrap();
    core.run(build()).unwrap();
}

""")
        val sinks = mutableListOf<Pipeline>()
        val sources = mutableListOf<Pipeline>()
        val handledElements = mutableSetOf<String>()

        // generate basic pipeline creation functions
        graph.pipelines.forEach {
            if (it.isSource) {
                sources.add(it)
            }
            if (it.isSink) {
                sinks.add(it)
            }
            builder.append(it.generatePipeline())
        }

        builder.append("""
pub fn build() -> impl Future<Item=(), Error=$ERROR_TYPE)> {""")
        sources.forEach {
            it.generateInstantiationCode(builder, handledElements)
        }

        builder.appendln("${sinks[0].name}")
        for (i in 1 until sinks.count()) {
            builder.appendln(".join(${sinks[i].name}).map(|_| ())")
        }
        builder.append(
        """
        .for_each(|_| ok(()))
        }

        """
        )

        Files.write(Paths.get("$outputDirectory/src/task_graph.rs"), builder.toString().toByteArray())
    }