package codegen

import com.intellij.util.io.exists
import editor.Node
import editor.RootNode
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class RustCodeGen {
    companion object {
        private fun getTemplate(filePath:String): URL {
            val classloader = this::class.java.classLoader // the inteliJ ClassLoader is quit brittle
            return classloader.getResource("/templates/$filePath")
        }

        fun generateCode(rootGraph: RootNode, moduleDirectory: String) {
            val projectPath = Paths.get(moduleDirectory)
            require(projectPath.exists())
            println("[RustCodeGenerator] generating code...")
            val srcPath = projectPath.resolve("src")
            val nodesPath =  srcPath.resolve("nodes")
            Files.createDirectories(nodesPath)

            val templates = listOf("stream_copy", "context")

            templates.forEach { template ->
                val destination = srcPath.resolve("$template.rs")
                if(!destination.exists()) {
                    val templateUrl = getTemplate("src/$template.rs")
                    val byteStream = templateUrl.openStream()
                    Files.copy(byteStream, destination)
                }
            }

            generateTaskGraph(rootGraph, projectPath)
        }

        private fun generateTaskGraph(rootNode: Node, outputDirectory:Path) {
            require(rootNode is RootNode)

            val project = Project(outputDirectory)
            val sources = rootNode.childNodes.filter { n -> n.isSource }
            val sinks = rootNode.childNodes.filter { n -> n.isSink }

            val incoming = rootNode.in_port.outgoingEdges.map{ e -> e.targetNode}.toSet()
            val outgoing = rootNode.out_ports.flatMap { p -> p.incommingEdges }.map{e -> e.sourceNode}
            if (incoming.any() || outgoing.any()) TODO("hierarchy")

            val visited = mutableSetOf<Node>()

            val nextNodes = sources.toMutableSet()

            val buildGraph = Function("build_graph", listOf(), Type.ImplTrait(AsyncTrait.Future(Type.unit))) //TODO Error Type
            while (nextNodes.any()) {
                val node = nextNodes.first()
                node.codeGen.generate(buildGraph)

                visited.add(node)
                nextNodes.addAll(node.successors.filter { n -> !visited.contains(n) })
                nextNodes.remove(node)
            }

            val sinkFutureHandle = "${rootNode.codeGen.nodeHandle}_sink_future"
            val sinkHandles = sinks.map { s -> s.codeGen.nodeHandle }
            buildGraph.define(sinkFutureHandle, sinkHandles.joinToString(").join(","(", ")"))
            buildGraph.result(sinkFutureHandle)

            val graphFile = CodeFile(project, "task_graph.rs")

            graphFile.imports.addAll(listOf(
                    "futures::{Future, Stream}",
                    "futures::future::ok",
                    "crate::nodes::*",
                    "crate::structs::*",
                    "crate::stream_copy::StreamCopyMutex",
                    "crate::context::GlobalContext"
            ))

            graphFile.defineFunction(buildGraph)

            graphFile.write()

            val mainFile = CodeFile(project,"main.rs")
            mainFile.modules.addAll(listOf("nodes", "structs", "task_graph", "stream_copy", "context"))
            mainFile.imports.addAll(listOf(
                    "tokio_core::reactor::Core"
            ))
            val main = mainFunction()
            main.defineMut("runtime", "Core::new().unwrap()")
            main.define("task_graph", "task_graph::build_graph()")
            main.exec(Expression("runtime.run(task_graph).unwrap()"))
            mainFile.defineFunction(main)
            mainFile.write()
        }
    }
}
