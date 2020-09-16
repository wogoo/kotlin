// TARGET_BACKEND: JVM_IR
// IGNORE_BACKEND_FIR: JVM_IR

interface AnyContext {
    operator fun Any.plus(other: Any): Any
}

object StringContext : AnyContext {
    override fun Any.plus(other: Any): Any = this.toString() + other.toString()
}

class MyClass(private val anyContext: AnyContext) {
    fun foo(a: Any, b: Any) with(anyContext) = a + b
}

fun box() = MyClass(StringContext).foo("O", "K")