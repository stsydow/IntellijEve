package codegen

import editor.Node
import editor.RootNode
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths

const val ERROR_TYPE = "EveError"
const val TAB = "    "
val tabs = {n:Int -> "".padStart(n* TAB.length)}
val streamType = {itemType: String -> "impl Stream<Item=$itemType, Error=$ERROR_TYPE>"}
val define = {name:String, expression:String -> "let $name = $expression;\n"}


interface StreamNode {
    // TODO get rid of the recursion here (handledElements Set too) - see traverseGraph
    //abstract val ident:String
    //open fun generateDefinitionCode():String  = ""
    //abstract fun generateInstantiationCode(builder: StringBuilder)
    val definition: String
    val streamHandle: String
}

class StreamSource(private val node:Node):StreamNode {

    init {
        assert(node.isSource)
        assert(isValidRustPascalCase(node.name))
    }

    override val streamHandle: String get() = node.streamHandle
    override val definition: String get() = define(streamHandle,
            "${node.moduleName}::${node.name}::new()"
    )
}

class StatelessNode(private val inputHandle: String, private val node:Node):StreamNode {

    init {
        assert(isValidRustPascalCase(node.name))
    }

    override val streamHandle: String get() = node.streamHandle
    override val definition: String get() = define(streamHandle,
            "$inputHandle\n$TAB.map(|event| { ${node.moduleName}::tick(event) })"
    )
}

class Merge(val inputStreams: Iterable<String>, private val ident: String) : StreamNode {

    init {
        assert(inputStreams.count() > 1)
    }


    override val streamHandle: String get() = "merge_$ident"

    override val definition: String get() = define(streamHandle,
            inputStreams.joinToString(")\n$TAB.select(", "(", ")")
    )
}

class Copy(private val inputHandle: String, private val ident: String) : StreamNode {

    val mutex_ident:String get() = "copy_$ident"

    override val definition: String get() = define(mutex_ident,
            "StreamCopyMutex::new(${inputHandle})"
    )

    override val streamHandle: String get() = "$mutex_ident.create_output_locked()"
}

abstract class Sink(private val inputHandle: String): StreamNode {
    companion object {
        fun joinToFuture(sinks:Iterable<Sink>): String {
            val joinedSinks = sinks.joinToString (").join(","(",")"){ sink -> sink.streamHandle }
            return "$joinedSinks.for_each(|_| ok(()))"
        }
    }

}

class DropSink(inputHandle: String, val ident: String): Sink(inputHandle) {

    override val streamHandle: String get() = "sink_$ident"
    override val definition: String = define(streamHandle, "$inputHandle\n$TAB.map(|_| ())")
}

class Cursor(val node: Node, val depth: Int = 0) {

    val successors: Iterable<Cursor> get() = node.successors
            .filter { s -> s != node.parent }
            .map { s -> Cursor(s, depth + 1) }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is Cursor -> node == other.node
            is Node -> node == other
            else -> false
        }
    }

    override fun hashCode(): Int {
        return node.hashCode()
    }

}

private fun traverseGraph(rootNode: Node) : StreamNode {
    val sources = rootNode.childNodes.filter { n -> n.isSource } + rootNode.in_port.outgoingEdges.map{e -> e.targetNode}
    val visited = mutableSetOf<Cursor>()
    val cursors = sources.asSequence().map { n -> Cursor(n) }.toMutableSet()

    val definitions = StringBuilder();
    val taskGraph = StringBuilder();


    while (cursors.isNotEmpty()) {
        val current = cursors.first()
        assert(!visited.contains(current))
        val node = current.node

        val predecessor:StreamNode = when {
            node.isSource -> {
                StreamSource(node)

            }
            node.isFanIn -> {
                val inputNodes = TODO("build merge from $node.predecessors")
                Merge(inputNodes)

            }
            else -> {
                node.predecessors.first()
            }
        }

        val streamNode:StreamNode = if (node.childNodes.any()) {
            //TODO context stuff
            val edges = node.in_port.outgoingEdges
            traverseGraph(node)
            val childCursors = edges.map { e -> Cursor(e.targetNode) }.toMutableSet()
            cursors.addAll(childCursors)
        } else {
            if (node.hasContext){
                TODO("StatefullNode")
            } else {
                StatelessNode(predecessor)
            }
        }

        val successors = current.successors

        when {
            node.isSink -> { //Sink
                TODO("build sink")
            }
            node.isFanOut ->{ //Fanout

                TODO("build copy / fanout")
            }
            else -> { // Pipline
                val next = successors.first()
                TODO("pipeline")
            }
        }
        cursors.addAll(successors.filter { s -> visited.contains(s) })
        visited.add(current)
        cursors.remove(current)
    }

}


class GraphBuilder(nodes: Iterable<Node>) {
    private val pipelines =  HashMap<String, Pipeline>()
    private val merges = HashMap<String, Merge>()
    private val copies = HashMap<String, Copy>()

    init {
        val sourceNodes = nodes.filter { n -> n.isSource }
        sourceNodes.forEach {
            oldTraverseGraph(it, it, null)
        }
    }

    fun asReducesGraph():ReducedGraph = ReducedGraph(ArrayList(this.pipelines.values))




    /*
     TODO: brake this up
      */
    private fun oldTraverseGraph(sourceNode: Node, currentNode: Node, predecessor: StreamNode?) {
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
                oldTraverseGraph(sourceNode, next, predecessor)
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

                oldTraverseGraph(next, next, merge)
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
                        oldTraverseGraph(it, it, copy)
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
                        oldTraverseGraph(it, it, copy)
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

        fun flattenChildGraphs(node:Node ):Iterable<Node> = listOf(node)  +  node.childNodes.flatMap { c -> flattenChildGraphs(c) }

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

            val graphs = flattenChildGraphs(rootGraph)

            val graph_code =  traverseGraph(rootGraph)
            generateTaskGraph(graphs, outputDirectory)
        }
    }
}


    private fun generateTaskGraph(nodes: Iterable<Node>, outputDirectory: String) {
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
            builder.append(it.generateDefinitionCode())
        }

        builder.appendln("pub fn build() -> impl Future<Item=(), Error=$ERROR_TYPE)> {")
        sources.forEach {
            it.generateInstantiationCode(builder, handledElements)
        }

        builder.appendln("${sinks[0].name}")
        for (i in 1 until sinks.count()) {
            builder.appendln("${tabs(2)}.join(${sinks[i].name}).map(|_| ())")
        }
        builder.appendln("${tabs(2)}.for_each(|_| ok(()))")
        builder.appendln("}")

        Files.write(Paths.get("$outputDirectory/src/task_graph.rs"), builder.toString().toByteArray())
    }