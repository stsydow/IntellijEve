package codegen

class FnTrait(val arguments: List<Pair<String,Type>>, val result:Type) {
    override fun toString(): String {
        val argumentString = arguments.joinToString(", "){(name, type) ->  type.toString()}
        return "Fn($argumentString) -> $result"
    }
}


sealed class AsyncTrait() {

    abstract override fun toString(): String


    class Future(val result: Type):AsyncTrait() {
        override fun toString(): String = "Future<Item=$result>"
    }

    class Stream(val item: Type):AsyncTrait() {
        override fun toString(): String = "Stream<Item=$item>"
    }


    class Sink(val item: Type):AsyncTrait() {
        override fun toString(): String = "Sink<Item=$item>"
    }
}

sealed class Type {
    companion object {
        val unit = Unit()
    }

    abstract override fun toString() : String
    class Unit:Type() {
        override fun toString(): String  = "()"
    }

    class BasicType(val type:String):Type() {
        init {
            require(type in RustBasicTypes) {"$type in not a basic rust type"}
        }

        override fun toString(): String  = type
    }

    class Reference(val type: Type, val mutable: Boolean):Type(){
        override fun toString(): String{
            return if (mutable) {
                "&mut $type"
            } else "& $type"
        }
    }

    class Function(val arguments: List<Type>, val result:Type):Type() {
        override fun toString(): String {
            val argumentString = arguments.joinToString(", ")
            return "nn($argumentString) -> $result"
        }
    }

    class ImplTrait<T: AsyncTrait>(val trait: T):Type() {
        override fun toString(): String = "impl $trait"
    }

    class UserDefined(val name: String):Type(){
        override fun toString(): String  = name
    }
}


interface CallableFunction<OutType:Type> {
    val parameters: Collection<Pair<String, Type>>
    val output: OutType
}

sealed class SyncPrimitive<OutType:Type> {
    abstract val output: OutType

    class Constant(val value: String, type: Type.BasicType)
        : SyncPrimitive<Type.BasicType>()
    {
        override val output: Type.BasicType = type
    }

    open class Function<OutType : Type>(
            override val parameters: Collection<Pair<String, Type>>,
            override val output: OutType
    ) : CallableFunction<OutType>, SyncPrimitive<OutType>()
    {
    }

    class ExternFunction<OutType : Type>(
            val name: String,
            parameters: Collection<Pair<String, Type>>,
            output: OutType
    ) : Function<OutType>(parameters, output)
    {
    }

    class AnonymousFunction<OutType : Type>(
            parameters: Collection<Pair<String, Type>>,
            output: OutType,
            val body: String
    ) : Function<OutType>(parameters, output)
    {
    }

}

sealed class AsyncPrimitive<OutType:Type, Container:AsyncTrait> {

    abstract fun isComposableTo()
    abstract class AsyncFunction<OutType:Type>(
            override val parameters: Collection<Pair<String, Type>>,
            override val output: OutType
    )
        :CallableFunction<OutType>, AsyncPrimitive<OutType, AsyncTrait.Future>()
    {

    }


    abstract class Source<OutType:Type>
        :AsyncPrimitive<OutType, AsyncTrait.Stream>()
    {
    }

    abstract class Sink<InType:Type, OutType:Type>
        :AsyncPrimitive<OutType, AsyncTrait.Sink>()
    {
    }

    abstract class StatefulProcessor<InType:Type, Context:Type, OutType:Type>:AsyncPrimitive<OutType, AsyncTrait.Stream>()
    {
    }


}

/*
type Future<Result:Any>

type Stream<Item:Any>

AsyncFunction: Function Any -> Future<Any>

Source: Function Any -> Stream<Any>

Sink: AsyncFunction Stream<Any> -> Future<Any>

Composition:
    map: Stream<A> ° Function A -> B => Stream<B>
    map: Future<A> ° Function A -> B => Future<B>
    and_then:  Future<A> ° Function A -> Future<B> => Future<B>
    ?: Stream<A> ° Function A -> Future<B> => Stream<B>


*/