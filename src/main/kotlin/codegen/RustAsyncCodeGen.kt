package codegen

import editor.Edge
import editor.Node
import editor.Port
import editor.RootNode
import java.nio.file.Files
import java.nio.file.Paths
import java.util.regex.Pattern

class ReducedGraph(val pipelines: List<Pipeline>, val merges: List<Merge>)
open class ReducedGraphNode(val varname: String)
class Pipeline(varname: String, val firstNode: Node, val lastNode: Node, val predecessor: ReducedGraphNode?, var successor: ReducedGraphNode?) : ReducedGraphNode(varname)
class Merge(varname: String, val inputNodes: MutableList<ReducedGraphNode>, var outputNode: ReducedGraphNode?) : ReducedGraphNode(varname)
class Copy(varname: String, val inputNode: ReducedGraphNode, val outputNodes: MutableList<ReducedGraphNode>) : ReducedGraphNode(varname)


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
        } else { // Copy
            if (predecessor is Pipeline) {
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
            } else if (predecessor is Merge) {
                val pipelineId = "pipeline_${currentNode.id.toLowerCase()}_${currentNode.id.toLowerCase()}"
                val copyid = "copy_${currentNode.id.toLowerCase()}"
                val mergeId = predecessor.varname

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

                val successors = getSuccessors(currentNode)
                successors.forEach {
                    traverseGraph(it, it, copy, pipelines, merges, copies)
                }
            } else {
                println("invalid predecessor")
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
use nodes::node_${it.name.toLowerCase()};""")
            }

        }
        builder.append("""
#[derive(Debug)]
pub enum EveError {
    UnknownError
}

pub struct StreamCopy<T, S: Stream<Item=T, Error=EveError>> {
    input: S,
    buffers: Vec<Vec<T>>,
    idx: usize
}

struct StreamCopyMutex<T, S: Stream<Item=T, Error=EveError>> {
    inner: Arc<Mutex<StreamCopy<T, S>>>
}

struct StreamCopyOutPort<T, S: Stream<Item=T, Error=EveError>> {
    id: usize,
    source: StreamCopyMutex<T, S>
}

impl<T: Clone, S: Stream<Item=T, Error=EveError>> StreamCopy<T, S> {
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

impl<T: Clone, S: Stream<Item=T, Error=EveError>> Stream for StreamCopyOutPort<T, S> {
    type Item = T;
    type Error = EveError;

    fn poll(&mut self) -> Poll<Option<Self::Item>, Self::Error> {
        self.source.poll_locked(self.id)
    }
}

impl<T, S: Stream<Item=T, Error=EveError>> Clone for StreamCopyMutex<T, S> {
    fn clone(&self) -> StreamCopyMutex<T, S> {
        StreamCopyMutex {
            inner: self.inner.clone()
        }
    }
}

impl<T: Clone, S: Stream<Item=T, Error=EveError>> StreamCopyMutex<T, S> {
    fn lock(&self) -> MutexGuard<StreamCopy<T, S>> {
        self.inner.lock().unwrap()
    }

    fn create_output_locked(&self) -> StreamCopyOutPort<T, S> {
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
pub fn build() -> impl Future<Item=(), Error=EveError> {""")
        sources.forEach {
            createElement(it, builder, handledElements)
        }

        builder.append("""

    ${sinks[0].varname}""")
        for (i in 1..sinks.count()-1) {
            builder.append("""
            .join(${sinks[i].varname}).map(|_| ())""")
        }
        builder.append("""
        .for_each(|_| ok(()))
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
            return "${element.varname}.create_output_locked()"
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
    let ${element.varname} = ${element.inputNodes[0].varname}""")
            for (i in 1..element.inputNodes.count()-1) {
                builder.append("""
        .select(${element.inputNodes[i].varname})""")
            }
            builder.append(";")
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
            element.outputNodes.forEach {
                createElement(it, builder, handledElements)
            }
        }
    }

    private fun getSourcePipeline(pipeline: Pipeline): String {
        val builder = StringBuilder()
        val output = if (getOutgoingEdges(pipeline.lastNode).count() == 0) "()" else pipeline.lastNode.out_ports[0].message_type
        builder.append("""
pub fn pipeline_${pipeline.firstNode.id}_${pipeline.lastNode.id}() -> impl Stream<Item=${output}, Error=EveError> {
    node_${pipeline.firstNode.name.toLowerCase()}::${pipeline.firstNode.name}::new()""")
        if (pipeline.firstNode != pipeline.lastNode) {
            var n = getSuccessors(pipeline.firstNode)[0]
            while (true) {
                builder.append("""
        .map(|event| {
            node_${n.name.toLowerCase()}::tick(event)
        })""")
                if (n == pipeline.lastNode)
                    break
                n = getSuccessors(n)[0]
            }
        }
        builder.append("""
}
""")
        return builder.toString()
    }

    private fun getNormalPipeline(pipeline: Pipeline): String {
        val builder = StringBuilder()
        val type = if (pipeline.lastNode.out_ports.count() > 0) pipeline.lastNode.out_ports[0].message_type else "()"
        builder.append("""
pub fn pipeline_${pipeline.firstNode.id}_${pipeline.lastNode.id}(stream: impl Stream<Item=${pipeline.firstNode.in_port.message_type}, Error=EveError>) -> impl Stream<Item=${type}, Error=EveError> {
    stream""")
        var n = pipeline.firstNode
        while (true) {
            builder.append("""
        .map(|event| {
            node_${n.name.toLowerCase()}::tick(event)
        })""")
            if (n == pipeline.lastNode)
                break
            n = getSuccessors(n)[0]
        }
        builder.append("""
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

/*
 * Function that checks whether the given string matches against the regular
 * expression that is provided by the Rust developers as given here:
 * https://doc.rust-lang.org/beta/reference/identifiers.html
 */
fun isValidRustIdentifier(str: String): Boolean {
    val patternStr = "[a-zA-Z][a-zA-Z_0-9]*|_[a-zA-Z_0-9]+"
    val pattern = Pattern.compile(patternStr)
    val matcher = pattern.matcher(str)
    return matcher.matches()
}

// for rust struct names
fun isValidRustPascalCase(str: String): Boolean {
    val patternStr = "[A-Z][a-zA-Z_0-9]*"
    val pattern = Pattern.compile(patternStr)
    val matcher = pattern.matcher(str)
    return matcher.matches()
}

// for rust module names
fun isValidRustSnakeCase(str: String): Boolean {
    val patternStr = "[a-z]+[a-z_0-9]*"
    val pattern = Pattern.compile(patternStr)
    val matcher = pattern.matcher(str)
    return matcher.matches()
}

fun pascalToSnakeCase(str:String): String {
    assert(str.isNotEmpty())
    var result = str[0].toLowerCase().toString()
    for(c in str.slice(1..str.lastIndex)) {
        if (c.isUpperCase()) {
            result += "_"+c.toLowerCase()
        }else{
            result += c
        }
    }
    return result
}

/*
 * Returns true if given string appears in one of the lists of strict, reserved
 * or weak keywords of Rust as given here:
 * https://doc.rust-lang.org/beta/reference/keywords.html
 */
fun isRustKeyword(str: String): Boolean {
    val strictKeywords = arrayOf(
            "as",       "break",        "const",        "continue",     "crate",
            "else",     "enum",         "extern",       "false",        "fn",
            "for",      "if",           "impl",         "in",           "let",
            "loop",     "match",        "mod",          "move",         "mut",
            "pub",      "ref",          "return",       "self",         "Self",
            "static",   "struct",       "super",        "trait",        "true",
            "type",     "unsafe",       "use",          "where",        "while"
    )
    val reservedKeywords = arrayOf(
            "abstract",     "become",       "box",      "do",       "final",
            "macro",        "override",     "priv",     "typeof",   "unsized",
            "virtual",      "yield"
    )
    val weakKeywords = arrayOf(
            "union",        "'static",      "dyn"
    )
    return (    strictKeywords.contains(str) ||
                reservedKeywords.contains(str) ||
                weakKeywords.contains(str)
            )
}