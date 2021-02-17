// !DIAGNOSTICS: -UNUSED_EXPRESSION -UNUSED_PARAMETER -EXPERIMENTAL_API_USAGE_ERROR
// WITH_RUNTIME

import kotlin.experimental.ExperimentalTypeInference

data class Foo(val bar: String)

interface CodecBuilder<Value : Any> {
    fun decode(fn: (value: String) -> Value)
    fun encode(fn: Value.(value: String) -> Unit)
}

fun <Value : Any> makeCodec(@BuilderInference configure: CodecBuilder<Value>.() -> Unit) {}

fun main() {
    makeCodec {
        decode { string ->
            Foo(string)
        }
        encode { value ->
            this
        }
    }
}