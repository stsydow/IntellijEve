package codegen

import com.intellij.util.io.exists
import editor.Node
import editor.RootNode
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

interface Statement {
    override fun toString() : String
}

class Expression(val expression:String):Statement {
    override fun toString() = expression
}

class Definition(val identifier:String, val expression: Expression, val mutable:Boolean = false) : Statement {
    override fun toString() = if (mutable) {
        "let mut $identifier = $expression;"
    }else {
        "let $identifier = $expression;"
    }
}

interface Scope {
    val statements: MutableList<Statement>
    val knownIdentifiers: MutableSet<String>
    var result: Expression?
    val hasResult: Boolean get() = result != null

    fun checkIdent(identifier:String) {
        require(! identifier.isEmpty()) {"Can't define an anonymous variable."}
        require(knownIdentifiers.add(identifier)){"identifier \"$identifier\" already defined."}
    }

    fun define(identifier:String, expression:String) {
        checkIdent(identifier)
        require(result == null)
        statements.add(Definition(identifier, Expression(expression)))
    }

    fun defineMut(identifier:String, expression:String) {
        checkIdent(identifier)
        require(result == null)
        statements.add(Definition(identifier, Expression(expression), true))
    }

    fun defineFromBlock(identifier:String, block:CodeBlock) {
        checkIdent(identifier)
        require(result == null)
        require(block.hasResult)
        statements.add(Definition(identifier, block.asExpression()))
    }

    fun exec(expression:Statement) {
        require(result == null)
        statements.add(Expression("$expression;"))
    }

    fun result(expression: String) {
        require(result == null)
        result = Expression(expression)
    }

     fun asStringBuilder() : StringBuilder {
        val builder = StringBuilder()
        statements.forEach { statement ->
            builder.appendln("$TAB$statement")
        }
        if(result != null) {
            builder.appendln("$TAB$result")
        }
        return builder
    }
}

abstract class ScopeImpl : Scope {
    override val statements = mutableListOf<Statement>()
    override val knownIdentifiers = mutableSetOf<String>()
    override var result: Expression? = null

    override fun toString() = asStringBuilder().toString()
}

class CodeBlock : ScopeImpl() , Statement {

    override fun asStringBuilder() : StringBuilder {
        val builder = StringBuilder()
        builder.appendln("{")
        builder.append(super.asStringBuilder())
        builder.appendln("}")
        return builder
    }

    fun asExpression() = Expression(toString())
}

class Parameter(val name:String, val type:String) {
    override fun toString(): String {
        return "$name:$type"
    }
}

class Function(val name: String, val arguments: List<Parameter>, val resultType:String) : ScopeImpl(), Statement {

    init {
        arguments.forEach { arg ->
            require(knownIdentifiers.add(arg.name)){"argument \"${arg.name}\" allready defined"}
        }
    }

    val signature: String get() {
        val argumentString = arguments.joinToString(", ")
        return "$name($argumentString) -> $resultType"
    }

    override fun asStringBuilder() : StringBuilder {
        check( (resultType != "()") == hasResult)
        val builder = java.lang.StringBuilder()
        builder.appendln("pub fn $signature")
        builder.appendln("{")
        builder.append(super.asStringBuilder())
        builder.appendln("}")
        return builder
    }
}

fun mainFunction() = Function("main", listOf<Parameter>(), "()")

class CodeFile(val fileName: String) : ScopeImpl() {

    var externCrates = mutableSetOf<String>()
    var modules = mutableSetOf<String>()
    var imports = mutableSetOf<String>()


    fun defineFunction(function: Function) {
        require(knownIdentifiers.add(function.signature)){"function \"${function.signature}\" allready defined"}
        statements.add(function)
    }

    override fun asStringBuilder(): StringBuilder {
        val builder = java.lang.StringBuilder()
        externCrates.forEach { crate -> builder.appendln("extern crate $crate;")}
        modules.forEach { module -> builder.appendln("mod $module;")}
        imports.forEach { import -> builder.appendln("use $import;")}

        builder.append(super.asStringBuilder())
        return builder
    }

    fun write() {
        val path = Paths.get(fileName)
        Files.write(path, asStringBuilder().chunked(4096), Charsets.UTF_8)
    }
}

class RustCodeGen {
    companion object {
        private fun getTemplate(filePath:String): URL {
            val classloader = this::class.java.classLoader // the inteliJ ClassLoader is quit brittle
            return classloader.getResource("/templates/$filePath")
        }
        fun generateCode(rootGraph: RootNode, projectDirectory: String) {
            val projectPath = Paths.get(projectDirectory)
            require(projectPath.exists())
            println("[RustCodeGenerator] generating code...")
            val srcPath = Paths.get("$projectPath/src")
            val nodesPath =  Paths.get("$srcPath/nodes")
            Files.createDirectories(nodesPath)

            val streamCopyPath = Paths.get("$srcPath/stream_copy.rs")
            if (!Files.exists(streamCopyPath)) {
                val templateUrl = getTemplate("src/stream_copy.rs")
                val byteStream = templateUrl.openStream()
                Files.copy(byteStream, streamCopyPath)
            }

            generateTaskGraph(rootGraph, projectPath)
        }

        private fun generateTaskGraph(rootNode: Node, outputDirectory:Path) {
            require(rootNode is RootNode)
            val sources = rootNode.childNodes.filter { n -> n.isSource }
            val sinks = rootNode.childNodes.filter { n -> n.isSink }

            val incoming = rootNode.in_port.outgoingEdges.map{ e -> e.targetNode}.toSet()
            val outgoing = rootNode.out_ports.flatMap { p -> p.incommingEdges }.map{e -> e.sourceNode}
            if (incoming.any() || outgoing.any()) TODO("hierarchy")

            val visited = mutableSetOf<Node>()

            val nextNodes = sources.toMutableSet()

            val buildGraph = Function("build_graph", listOf(),"impl Future<Item=(), Error=EveError>")
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

            val graphFile = CodeFile("$outputDirectory/src/task_graph.rs")

            graphFile.imports.addAll(listOf(
                    "futures::{Future, Stream}",
                    "use futures::future::ok",
                    "crate::nodes::*",
                    "crate::structs::*",
                    "crate::stream_copy::StreamCopyMutex"
            ))

            graphFile.defineFunction(buildGraph)

            graphFile.write()

            val mainFile = CodeFile("$outputDirectory/src/main.rs")
            mainFile.modules.addAll(listOf("nodes", "structs", "task_graph", "stream_copy"))
            mainFile.imports.addAll(listOf(
                    "self::tokio_core::reactor::Core"
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
