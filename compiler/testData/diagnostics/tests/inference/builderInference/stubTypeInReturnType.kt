// !DIAGNOSTICS: -UNUSED_PARAMETER -EXPERIMENTAL_IS_NOT_ENABLED -UNUSED_VARIABLE -CAST_NEVER_SUCCEEDS
// WITH_RUNTIME

import kotlin.experimental.ExperimentalTypeInference

class Foo<Value>(x: Value)

interface CodecBuilder<Value> {
    fun contribute(x: Value)
}

fun <Value> transform(x: CodecBuilder<Value>): Foo<Value> = null as Foo<Value>

@OptIn(ExperimentalTypeInference::class)
fun <Value> makeCodec(@BuilderInference configure: CodecBuilder<Value>.() -> Unit) {}

fun <K> materialize(): K = TODO()

fun test1() {
    <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>makeCodec<!> {
        val x = <!DEBUG_INFO_EXPRESSION_TYPE("Type is unknown")!><!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>transform<!>(this)<!>
        contribute("")
    }
}
