package editor

import codegen.pascalToSnakeCase

class CodeGenNode(val node:Node) {
    val parent get() = node.parent
    val childNodes get() = node.childNodes
    val name get() = node.name


    val streamHandle: String get() = pascalToSnakeCase(name)
    val moduleName: String get() = pascalToSnakeCase(name)

}