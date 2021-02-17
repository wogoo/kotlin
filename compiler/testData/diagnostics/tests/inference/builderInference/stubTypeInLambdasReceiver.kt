// !DIAGNOSTICS: -UNUSED_EXPRESSION -UNUSED_PARAMETER -EXPERIMENTAL_API_USAGE_ERROR
// WITH_RUNTIME

import kotlin.experimental.ExperimentalTypeInference

data class Foo(val bar: String)

class A<T> {}

interface CodecBuilder<Value : Any> {
    fun decode(fn: (value: String) -> Value)
    fun encode(fn: Value.(value: String) -> Unit)
    fun encode3(fn: A<A<Value>>.(value: String) -> Unit)
}

fun <K : Any> CodecBuilder<K>.encode2(fn: K.(value: String) -> Unit) {}
fun <K : Any> CodecBuilder<K>.encode4(fn: A<A<K>>.(value: String) -> Unit) {}

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