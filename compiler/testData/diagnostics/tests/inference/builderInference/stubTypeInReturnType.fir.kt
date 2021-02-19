import kotlin.experimental.ExperimentalTypeInference

class Foo<Value>(x: Value)

interface CodecBuilder<Value> {
    fun contribute(x: Value)
}

fun <Value> transform(x: CodecBuilder<Value>): Foo<Value> {}

@OptIn(ExperimentalTypeInference::class)
fun <Value> makeCodec(@BuilderInference configure: CodecBuilder<Value>.() -> Unit) {}

fun <K> materialize(): K = TODO()

fun test1() {
    makeCodec {
        val x = transform(this)
        contribute("")
    }
}
