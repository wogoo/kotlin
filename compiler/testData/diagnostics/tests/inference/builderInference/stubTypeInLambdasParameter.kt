// !DIAGNOSTICS: -UNUSED_EXPRESSION -UNUSED_PARAMETER -EXPERIMENTAL_API_USAGE_ERROR
// WITH_RUNTIME

import kotlin.experimental.ExperimentalTypeInference

data class Foo(val bar: String)

class A<T> {}

interface CodecBuilder<Value : Any> {
    fun decode(fn: (value: String) -> Value)
    fun encode(fn: (value: Value) -> Unit)
    fun encode3(fn: (value: A<A<Value>>) -> Unit)
}

fun <K : Any> CodecBuilder<K>.encode2(fn: (value: K) -> Unit) {}
fun <K : Any> CodecBuilder<K>.encode4(fn: (value: A<A<K>>) -> Unit) {}

fun <Value : Any> makeCodec(@BuilderInference configure: CodecBuilder<Value>.() -> Unit) {}

fun test1() {
    <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>makeCodec<!> {
        decode { string ->
            Foo(string)
        }
        encode { <!CANNOT_INFER_PARAMETER_TYPE!>value<!> ->
            <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>value<!>
        }
    }
}

fun test2() {
    <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>makeCodec<!> {
        decode { string ->
            Foo(string)
        }
        <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>encode2<!> { <!CANNOT_INFER_PARAMETER_TYPE!>value<!> ->
            <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>value<!>
        }
    }
}

fun test3() {
    <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>makeCodec<!> {
        decode { string ->
            Foo(string)
        }
        encode3 { <!CANNOT_INFER_PARAMETER_TYPE!>value<!> ->
            <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>value<!>
        }
    }
}

fun test4() {
    <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>makeCodec<!> {
        decode { string ->
            Foo(string)
        }
        <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>encode4<!> { <!CANNOT_INFER_PARAMETER_TYPE!>value<!> ->
            <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>value<!>
        }
    }
}

fun test5() {
    makeCodec {
        decode { string ->
            Foo(string)
        }
        <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.reflect.KFunction1<(TypeVariable(Value)) -> kotlin.Unit, kotlin.Unit>")!>::encode<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("Type is unknown"), TYPE_INFERENCE_POSTPONED_VARIABLE_IN_RECEIVER_TYPE!>::<!TYPE_INFERENCE_INCORPORATION_ERROR!>encode2<!><!>
        <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.reflect.KFunction1<(value: A<A<TypeVariable(Value)>>) -> kotlin.Unit, kotlin.Unit>")!>::encode3<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("Type is unknown"), TYPE_INFERENCE_POSTPONED_VARIABLE_IN_RECEIVER_TYPE!>::<!TYPE_INFERENCE_INCORPORATION_ERROR!>encode4<!><!>
    }
}