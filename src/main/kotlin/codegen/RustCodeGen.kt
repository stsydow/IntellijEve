package codegen

import com.intellij.util.io.exists
import editor.Node
import editor.RootNode
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

interface Statement {}

class Expression(val expression:String):Statement {
    override fun toString() = expression
}

class Definition(val identifier:String, val expression: Expression) : Statement {
    override fun toString() = "let $identifier = $expression;"
}

class CodeBlock : Statement  {
    private val statements = mutableListOf<Statement>()
    private val known_indentifiers = mutableSetOf<String>()
    private var result: Expression? = null
    val hasResult: Boolean get() = result != null

    fun define(identifier:String, expression:String) {
        require(result == null)
        require(known_indentifiers.add(identifier)){"identifier allready defined"}
        statements.add(Definition(identifier, Expression(expression)))
    }

    fun defineFromBlock(identifier:String, block:CodeBlock) {
        require(result == null)
        require(block.hasResult)
        require(known_indentifiers.add(identifier)){"identifier allready defined"}
        statements.add(Definition(identifier, Expression(block.toString())))
    }

    fun defineReserved(identifier:String) {
        require(known_indentifiers.add(identifier)){"reserved identifier allready defined"}
    }

    fun exec(expression:String) {
        require(result == null)
        statements.add(Expression("$expression;"))
    }

    fun result(expression: String) {
        require(result == null)
        result = Expression(expression)
    }

    fun asStringBuilder() : StringBuilder {
        val builder = StringBuilder()
        builder.appendln("{")
        statements.forEach { statement ->
            builder.appendln("$TAB$statement")
        }
        if(result != null) {
            builder.appendln("$TAB$result")
        }
        builder.appendln("}")
        return builder
    }

    override fun toString(): String {
        return asStringBuilder().toString()
    }
}
class Parameter(val name:String, val type:String) {
    override fun toString(): String {
        return "$name:$type"
    }
}

class Function(val name: String, val body:CodeBlock, val arguments: List<Parameter>, val resultType:String) : Statement {
    companion object {
        fun main(body: CodeBlock) = Function("main", body, listOf<Parameter>(), "()")
    }
    init {
        arguments.map { a -> a.name }.forEach { arg_name ->
            body.defineReserved(arg_name)
        }
    }
    //constructor(name: String):this(name, CodeBlock(), listOf<Parameter>(), "()")

    fun asStringBuilder() : StringBuilder {
        check( (resultType != "()") == body.hasResult)
        val builder = java.lang.StringBuilder()
        val argumentString = arguments.joinToString(", ")
        builder.appendln("pub fn $name(${argumentString}) -> $resultType")
        builder.append(body)
        return builder
    }

    override fun toString(): String {
        return asStringBuilder().toString()
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

            val next_nodes = sources.toMutableSet()

            val code = CodeBlock()
            while (next_nodes.any()) {
                val node = next_nodes.first()
                node.codeGen.generate(code)

                visited.add(node)
                next_nodes.addAll(node.successors.filter { n -> !visited.contains(n) })
                next_nodes.remove(node)
            }

            val sinkFutureHandle = "${rootNode.codeGen.nodeHandle}_sink_future"
            val sinkHandles = sinks.map { s -> s.codeGen.nodeHandle }
            code.define(sinkFutureHandle, sinkHandles.joinToString(").join(","(", ")"))
            code.define("runtime", "Core::new().unwrap()")
            code.exec("runtime.run($sinkFutureHandle).unwrap()")
            //".map(|_| ())"
            //"${tabs(2)}.for_each(|_| ok(()))"

            val graphFile = Paths.get("$outputDirectory/src/task_graph.rs")
            Files.write(graphFile, code.asStringBuilder().chunked(4096), Charsets.UTF_8)
        }
    }
}
