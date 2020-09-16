// TARGET_BACKEND: JVM_IR
// IGNORE_BACKEND_FIR: JVM_IR

class A(val x: String)

class B {
    fun f() with(A("O")) = x
    fun g(a: A) with(a) = x
}

fun box(): String {
    val b = B()
    return b.f() + b.g(A("K"))
}