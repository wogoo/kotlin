// !DIAGNOSTICS: -UNUSED_PARAMETER

class A {
    val x = 1
}

class B(val a: A) {
    fun f() with(a) = x
    fun g() with(A()) {
        x
    }
    fun h(a: A) with(a) = x
}