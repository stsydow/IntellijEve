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
class Copy(varname: String, val inputNode: Pipeline, val outputNodes: MutableList<ReducedGraphNode>) : ReducedGraphNode(varname)


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
        val copies = HashMap<String, Copy>()
        sourceNodes.forEach {
            traverseGraph(it, it, null, pipelines, merges, copies)
        }
        return ReducedGraph(ArrayList(pipelines.values), ArrayList(merges.values))
    }

    private fun traverseGraph(sourceNode: Node, currentNode: Node, predecessor: ReducedGraphNode?, pipelines: HashMap<String, Pipeline>, merges: HashMap<String, Merge>, copies: HashMap<String, Copy>) {
        if (getOutgoingEdges(currentNode).count() == 0) { // sink
            val pipelineId = "pipeline_${sourceNode.id.toLowerCase()}_${currentNode.id.toLowerCase()}"
            if (!pipelines.containsKey(pipelineId)) {
                val pipeline = Pipeline(pipelineId, sourceNode, currentNode, predecessor, null)
                pipelines.put(pipeline.varname, pipeline)
                if (predecessor is Merge) {
                    predecessor.outputNode = pipeline
                } else if (predecessor is Copy) {
                    if (pipeline !in predecessor.outputNodes) {
                        predecessor.outputNodes.add(pipeline)
                    }
                }
            }
        } else if (getOutgoingEdges(currentNode).count() == 1) { // pipeline...
            val next = getOutgoingEdges(currentNode)[0].target.parent!!
            if (getIncomingEdges(next.in_port).count() == 1) { // ... continues with a normal successor
                traverseGraph(sourceNode, next, predecessor, pipelines, merges, copies)
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

                traverseGraph(next, next, merge, pipelines, merges, copies)
            }
        } else {
            val pipelineId = "pipeline_${sourceNode.id.toLowerCase()}_${currentNode.id.toLowerCase()}"
            val copyid = "copy_${currentNode.id.toLowerCase()}"

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

            val successors = getSuccessors(currentNode)
            successors.forEach {
                traverseGraph(it, it, copy, pipelines, merges, copies)
            }
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

use std::sync::Arc;
use std::sync::Mutex;
use std::clone::Clone;
use std::sync::MutexGuard;

use structs::*;""")
        nodes.forEach {
            if (it.childNodes.count() == 0) {
                builder.append("""
use nodes::node_${it.name.toLowerCase()}::${it.name};""")
            }

        }
        builder.append("""
type EveStream<T> = Stream<Item=T, Error=EveError>;

#[derive(Debug)]
pub enum EveError {
    UnknownError
}

pub struct StreamCopy<T> {
    input: Box<EveStream<T>>,
    buffers: Vec<Vec<T>>,
    idx: usize
}

struct StreamCopyMutex<T> {
    inner: Arc<Mutex<StreamCopy<T>>>
}

struct StreamCopyOutPort<T> {
    id: usize,
    source: StreamCopyMutex<T>
}

impl<T: Clone> StreamCopy<T> {
    fn poll(&mut self, id: usize) -> Poll<Option<T>, EveError> {
        let buffered = self.buffered_poll(id);
        match buffered {
            Some(buffered) => {
                Ok(Async::Ready(Some(buffered)))
            }
            None => {
                let result = self.input.poll();
                match result {
                    Ok(async) => {
                        match async {
                            Async::Ready(ready) => {
                                match ready {
                                    Some(event) => {
                                        for mut buffer in &mut self.buffers {
                                            buffer.push(event.clone())
                                        }
                                        self.poll(id)
                                    },
                                    None => Ok(Async::Ready(None))
                                }
                            },
                            Async::NotReady => Ok(Async::NotReady)
                        }
                    }
                    Err(e) => Err(e)
                }
            }
        }
    }

    fn buffered_poll(&mut self, id: usize) -> Option<T>{
        let mut buffer = &mut self.buffers[id];
        if buffer.len() > 0 {
            Some(buffer.remove(0))
        } else {
            None
        }
    }
}

impl<T: Clone> Stream for StreamCopyOutPort<T> {
    type Item = T;
    type Error = EveError;

    fn poll(&mut self) -> Poll<Option<Self::Item>, Self::Error> {
        self.source.poll_locked(self.id)
    }
}

impl<T> Clone for StreamCopyMutex<T> {
    fn clone(&self) -> StreamCopyMutex<T> {
        StreamCopyMutex {
            inner: self.inner.clone()
        }
    }
}

impl<T: Clone> StreamCopyMutex<T> {
    fn lock(&self) -> MutexGuard<StreamCopy<T>> {
        self.inner.lock().unwrap()
    }

    fn create_output_locked(&self) -> StreamCopyOutPort<T> {
        let mut inner = self.lock();
        let val = StreamCopyOutPort {
            source: (*self).clone(),
            id: inner.idx
        };
        inner.buffers.push(vec!());
        inner.idx += 1;
        val
    }

    fn poll_locked(&self, id: usize) -> Poll<Option<T>, EveError> {
        let mut inner = self.lock();
        inner.poll(id)
    }
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
                if (isSinkNode(it.lastNode)) {
                    sinks.add(it)
                }
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
            .join(${sinks[i].varname}).map(|_| ())""")
        }
        builder.append("""
        .for_each(|_| ok(())))
}

""")

        Files.write(Paths.get(outputDirectory + "/src/main.rs"), builder.toString().toByteArray())
    }

    private fun get_predecessor_argument(element: ReducedGraphNode?): String {
        if (element == null)
            return ""
        else if (element is Pipeline)
            return element.varname
        else if (element is Merge)
            return element.varname
        else if (element is Copy)
            return "Box::new(${element.varname}.create_output_locked())"
        throw Error()
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
            builder.append("""
    let ${element.varname} = ${element.varname}(${get_predecessor_argument(element.predecessor)});""")
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
        } else if (element is Copy) {
            builder.append("""
    let ${element.varname} = StreamCopyMutex {
        inner: Arc::new(Mutex::new(StreamCopy {
            input: ${element.inputNode.varname},
            buffers: vec!(),
            idx: 0
        }
    ))};""")
            handledElements.add(element.varname)
        }
    }

    private fun getSourcePipeline(pipeline: Pipeline): String {
        val builder = StringBuilder()
        val output = if (getOutgoingEdges(pipeline.lastNode).count() == 0) "()" else pipeline.lastNode.out_ports[0].message_type
        builder.append("""
pub fn pipeline_${pipeline.firstNode.id}_${pipeline.lastNode.id}() -> Box<Stream<Item=${output}, Error=EveError>> {
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