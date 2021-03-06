package codegen

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern

const val TAB = "    "

val RustBasicTypes = arrayOf(
        "bool",
        "u8", "u16", "u32", "u64", "u128",
        "i8", "i16", "i32", "i64", "i128",
        "char", "str"
)

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
    require(str.isNotEmpty())
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
            "as",       "async",        "await",        "break",        "const",
            "continue",     "crate",
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
        require(!isRustKeyword(identifier))
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

class Function(val name: String, val arguments: List<Parameter>, val resultType:Type) : ScopeImpl(), Statement {

    class Parameter(val name:String, val type:Type) {
        override fun toString(): String {
            return "$name:$type"
        }
    }
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
        check( (resultType != Type.unit) == hasResult)
        val builder = java.lang.StringBuilder()
        builder.appendln("pub fn $signature")
        builder.appendln("{")
        builder.append(super.asStringBuilder())
        builder.appendln("}")
        return builder
    }
}

fun mainFunction() = Function("main", listOf<Function.Parameter>(), Type.unit)

class Project(val root: Path) {
    val sourceDir = root.resolve("src")
}

class CodeFile(val project: Project, val fileName: Path) : ScopeImpl() {
    var modules = mutableSetOf<String>()
    var imports = mutableSetOf<String>()

    constructor(project: Project, fileName: String):this(project, Paths.get(fileName))

    val moduleName:String = fileName.toString().removeSuffix(".rs")

    val fullPath:String get(){
        val parent = fileName.parent
        return if (parent.any()) {
            val parentModule = parent.joinToString("::")
            "crate::$parentModule::$moduleName"

        }else {
            "crate::$moduleName"
        }
    }

    fun defineFunction(function: Function) {
        require(knownIdentifiers.add(function.signature)){"function \"${function.signature}\" allready defined"}
        statements.add(function)
    }

    override fun asStringBuilder(): StringBuilder {
        val builder = java.lang.StringBuilder()
        modules.forEach { module -> builder.appendln("mod $module;")}
        builder.appendln()
        imports.forEach { import -> builder.appendln("use $import;")}
        builder.appendln()

        builder.append(super.asStringBuilder())
        return builder
    }

    fun write() {
        val path = project.sourceDir.resolve(fileName)
        Files.write(path, asStringBuilder().chunked(4096), Charsets.UTF_8)
    }
}