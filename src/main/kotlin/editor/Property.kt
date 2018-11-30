package editor

enum class ContextType{None, Global, Selection}

interface Property {
    fun exchange(node:Node) : Property
}

// TODO: parent context; termination condition
class Context(val type: ContextType, val selector:String, val structName: String):Property {

    companion object {
        val None = Context(ContextType.None, "", "")
        fun Global(struct: String) = Context(ContextType.Global, "", struct)
        fun Select(expression: String, struct:String) = Context(ContextType.Selection, expression, struct)

        private const val SELECT_PREFIX = "SELECT:"

        fun parse(expression: String, structName: String): Context = when(expression) {
            "" -> None
            "GLOBAL" -> {
                //TODO: require(structName.isNotEmpty())
                Global(structName)
            }
            else ->
            {
                //TODO: require(structName.isNotEmpty())
                val selector = if (expression.startsWith(SELECT_PREFIX, ignoreCase = false)) {
                    expression.substring(SELECT_PREFIX.length)
                } else {
                    expression
                }
                Select(selector,structName)
            }
        }
    }

    init {
        require((type == ContextType.Selection) == selector.isNotEmpty())
    }

    override fun exchange(node: Node): Property {
        val oldContext = node.context
        node.context = this
        return oldContext
    }

    override fun toString(): String = when (type) {
        ContextType.None -> "None"
        ContextType.Global -> "Global"
        ContextType.Selection -> "Select:$selector"
    }

    fun asExpression(): String = when (type) {
        ContextType.None -> ""
        ContextType.Global -> "GLOBAL"
        ContextType.Selection -> selector
    }

    override fun equals(other: Any?): Boolean =
            if(other is Context && type == other.type) {
                when (type) {
                    ContextType.None -> true
                    ContextType.Global -> structName == other.structName
                    ContextType.Selection -> structName == other.structName && selector == other.selector
                }
            }else {
                false
            }

    override fun hashCode(): Int  = when (type) {
        ContextType.None -> 0
        ContextType.Global -> structName.hashCode()
        ContextType.Selection -> 31 * structName.hashCode() * selector.hashCode()
    }
}

class Filter(val expression:String):Property {
    override fun exchange(node: Node): Property {
        val oldFilter = node.filterExpression
        node.filterExpression = expression
        return Filter(oldFilter)
    }

    override fun equals(other: Any?): Boolean = when (other) {
        is Filter -> expression == other.expression
        else -> false
    }

    override fun hashCode(): Int  = expression.hashCode()
}

class Order(val expression: String):Property {
    override fun exchange(node: Node): Property {
        val oldOrder = node.orderExpression
        node.orderExpression = expression
        return Order(oldOrder)
    }

    override fun equals(other: Any?): Boolean = when (other) {
        is Order -> expression == other.expression
        else -> false
    }

    override fun hashCode(): Int  = expression.hashCode()
}