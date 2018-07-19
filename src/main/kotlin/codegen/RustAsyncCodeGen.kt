package codegen

import editor.Edge
import editor.Node
import editor.Port
import editor.RootNode
import java.nio.file.Files
import java.nio.file.Paths

class ReducedGraph(val pipelines: List<Pipeline>, val merges: List<Merge>)
open class ReducedGraphNode(val varname: String)
class Pipeline(varname: String, val firstNode: Node, val lastNode: Node, val predecessor: ReducedGraphNode?, var successor: ReducedGraphNode?) : ReducedGraphNode(varname)
class Merge(varname: String, val inputNodes: MutableList<ReducedGraphNode>, var outputNode: ReducedGraphNode?) : ReducedGraphNode(varname)


class RustAsyncCodeGen {
    fun generateSkeleton(outputDirectory: String) {

    }

    fun generateCode(rootGraph: RootNode, outputDirectory: String) {
        println("[RustCodeGenerator] generating code...")
        if (!Files.exists(Paths.get(outputDirectory + "/src"))) {
            Files.createDirectories(Paths.get(outputDirectory + "/src"))
        }
        if (!Files.exists(Paths.get(outputDirectory + "/src/nodes"))) {
            Files.createDirectories(Paths.get(outputDirectory + "/src/nodes"))
        }
        val graphs: MutableList<Node> = getChildGraphs(rootGraph)
        generateMainRs(graphs, outputDirectory)
    }

    private fun getChildGraphs(node: Node): MutableList<Node> {
        val list = mutableListOf<Node>()
        println("adding node ${node.name}")
        list.add(node)
        node.childNodes.forEach {
            list.addAll(getChildGraphs(it))
        }
        return list
    }

    private fun getReducedGraph(sourceNodes: List<Node>): ReducedGraph{
        val pipelines =  HashMap<String, Pipeline>()
        val merges = HashMap<String, Merge>()
        sourceNodes.forEach {
            traverseGraph(it, it, null, pipelines, merges)
        }
        return ReducedGraph(ArrayList(pipelines.values), ArrayList(merges.values))
    }

    private fun traverseGraph(sourceNode: Node, currentNode: Node, predecessor: ReducedGraphNode?, pipelines: HashMap<String, Pipeline>, merges: HashMap<String, Merge>) {
        if (getOutgoingEdges(currentNode).count() == 0) { // sink
            val pipelineId = "pipeline_${sourceNode.id.toLowerCase()}_${currentNode.id.toLowerCase()}"
            if (!pipelines.containsKey(pipelineId)) {
                val pipeline = Pipeline(pipelineId, sourceNode, currentNode, predecessor, null)
                pipelines.put(pipeline.varname, pipeline)
                if (predecessor is Merge) {
                    predecessor.outputNode = pipeline
                }
            }
        } else if (getOutgoingEdges(currentNode).count() == 1) { // pipeline...
            val next = getOutgoingEdges(currentNode)[0].target.parent!!
            if (getIncomingEdges(next.in_port).count() == 1) { // ... continues with a normal successor
                traverseGraph(sourceNode, next, predecessor, pipelines, merges)
            } else { // ... stops with a merge
                val pipelineId = "pipeline_${sourceNode.id.toLowerCase()}_${currentNode.id.toLowerCase()}"
                val mergeId = "merge_${next.id.toLowerCase()}"

                val pipeline = when (pipelines.containsKey(pipelineId)) {
                    true -> pipelines[pipelineId]!!
                    false -> Pipeline(pipelineId, sourceNode, currentNode, predecessor, null)
                }
                val merge = when (merges.containsKey(mergeId)) {
                    true -> merges[mergeId]!!
                    false -> Merge("merge_${next.id.toLowerCase()}", mutableListOf(), null)
                }

                if (pipeline !in merge.inputNodes) {
                    merge.inputNodes.add(pipeline)
                }
                pipeline.successor = merge

                merges[mergeId] = merge
                pipelines[pipelineId] = pipeline

                if (predecessor is Merge) {
                    predecessor.outputNode = pipeline
                }

                traverseGraph(next, next, merge, pipelines, merges)
            }
        } else {
            throw NotImplementedError()
        }
    }

    private fun generateMainRs(nodes: MutableList<Node>, outputDirectory: String) {
        val sourceNodes = nodes.filter { x -> isSourceNode(x) }
        val graph = getReducedGraph(sourceNodes)
        val builder = StringBuilder()
        builder.append(
"""extern crate futures;
extern crate tokio_core;

mod nodes;
mod structs;

use tokio_core::reactor::Core;
use futures::Future;
use futures::future::ok;
use futures::Poll;
use futures::Stream;
use futures::Async;

use structs::*;""")
        nodes.forEach {
            if (it.childNodes.count() == 0) {
                builder.append("""
use nodes::node_${it.name.toLowerCase()}::${it.name};""")
            }

        }
        builder.append("""

#[derive(Debug)]
pub enum EveError {
    UnknownError
}

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
            if (isSourceNode(it.firstNode)) {
                sources.add(it)
                builder.append(getSourcePipeline(it))
            } else {
                if (isSinkNode(it.lastNode)) {
                    sinks.add(it)
                }
                builder.append(getNormalPipeline(it))
            }
        }

        builder.append("""
pub fn build() -> Box<Future<Item=(), Error=EveError>> {""")
        sources.forEach {
            createElement(it, builder, handledElements)
        }

        builder.append("""

    Box::from(${sinks[0].varname}""")
        for (i in 1..sinks.count()-1) {
            builder.append("""
            .join(pipeline_${sinks[i].varname}).map(|_| ())""")
        }
        builder.append("""
        .for_each(|_| ok(())))
}

""")

        Files.write(Paths.get(outputDirectory + "/src/main.rs"), builder.toString().toByteArray())
    }

    private fun createElement(element: ReducedGraphNode, builder: StringBuilder, handledElements: MutableSet<String>) {
        if (element.varname in handledElements) {
            return
        }
        if (element is Pipeline) {
            if (element.predecessor != null) {
                createElement(element.predecessor, builder, handledElements)
            }
            if (element.varname in handledElements) {
                return
            }
            val pred = if (element.predecessor != null)  element.predecessor.varname else ""
            builder.append("""
    let ${element.varname} = ${element.varname}(${pred});""")
            handledElements.add(element.varname)
            if (element.successor != null) {
                createElement(element.successor!!, builder, handledElements)
            }
        } else if (element is Merge) {
            element.inputNodes.forEach {
                createElement(it, builder, handledElements)
            }
            if (element.varname in handledElements) {
                return
            }
            builder.append("""
    let ${element.varname} = Box::from(${element.inputNodes[0].varname}""")
            for (i in 1..element.inputNodes.count()-1) {
                builder.append("""
        .select(${element.inputNodes[i].varname})""")
            }
            builder.append(");")
            handledElements.add(element.varname)
            if (element.outputNode != null) {
                createElement(element.outputNode!!, builder, handledElements)
            }

        }
    }

    private fun getSourcePipeline(pipeline: Pipeline): String {
        val builder = StringBuilder()
        builder.append("""
pub fn pipeline_${pipeline.firstNode.id}_${pipeline.lastNode.id}() -> Box<Stream<Item=${pipeline.lastNode.out_ports[0].message_type}, Error=EveError>> {
    Box::from(
        ${pipeline.firstNode.name}::new()""")
        if (pipeline.firstNode != pipeline.lastNode) {
            var n = getSuccessors(pipeline.firstNode)[0]
            while (true) {
                builder.append("""
        .map(|event| {
            ${n.name}::tick(event)
        })""")
                if (n == pipeline.lastNode)
                    break
                n = getSuccessors(n)[0]
            }
        }
        builder.append("""
    )
}
""")
        return builder.toString()
    }

    private fun getNormalPipeline(pipeline: Pipeline): String {
        val builder = StringBuilder()
        val type = if (pipeline.lastNode.out_ports.count() > 0) pipeline.lastNode.out_ports[0].message_type else "()"
        builder.append("""
pub fn pipeline_${pipeline.firstNode.id}_${pipeline.lastNode.id}(stream: Box<Stream<Item=${pipeline.firstNode.in_port.message_type}, Error=EveError>>) -> Box<Stream<Item=${type}, Error=EveError>> {
    Box::from(stream""")
        var n = pipeline.firstNode
        while (true) {
            builder.append("""
        .map(|event| {
            ${n.name}::tick(event)
        })""")
            if (n == pipeline.lastNode)
                break
            n = getSuccessors(n)[0]
        }
        builder.append("""
    )
}
""")
        return builder.toString()
    }

    private fun isSourceNode(g: Node): Boolean {
        return getIncomingEdges(g.in_port).count() == 0 && g.childNodes.count() == 0
    }

    private fun isSinkNode(g: Node): Boolean {
        return getOutgoingEdges(g).count() == 0
    }

    private fun getIncomingEdges(p: Port): List<Edge> {
        val list: MutableList<Edge> = mutableListOf()
        val parent: Node = p.parent!!
        parent.childEdges.forEach {
            if (it.target == p) {
                list.add(it)
            }
        }

        val gparent = p.parent.parent
        if (gparent != null)
        {
            gparent.childEdges.forEach {
                if (it.target == p) {
                    list.add(it)
                }
            }
        }
        return list
    }

    private fun getOutgoingEdges(n: Node): List<Edge> {
        val list: MutableList<Edge> = mutableListOf()
        n.out_ports.forEach {
            list.addAll(getOutgoingEdges(it))
        }
        return list
    }

    private fun getOutgoingEdges(p: Port): List<Edge> {
        val list: MutableList<Edge> = mutableListOf()
        val parent: Node = p.parent!!
        parent.childEdges.forEach {
            if (it.source == p) {
                list.add(it)
            }
        }
        val gparent:Node = p.parent.parent!!
        gparent.childEdges.forEach {
            if (it.source == p) {
                list.add(it)
            }
        }
        return list
    }

    private fun getSuccessors(n: Node): List<Node> {
        val list = mutableListOf<Node>()
        getOutgoingEdges(n).forEach {
            list.add(it.target.parent!!)
        }
        return list
    }
}