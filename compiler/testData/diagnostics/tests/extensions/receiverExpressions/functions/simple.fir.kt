// !DIAGNOSTICS: -UNUSED_PARAMETER

class A {
    val x = 1
}

class B(val a: A) {
    fun f() with(a) = <!UNRESOLVED_REFERENCE!>x<!>
    fun g() with(A()) {
        <!UNRESOLVED_REFERENCE!>x<!>
    }
    fun h(a: A) with(a) = <!UNRESOLVED_REFERENCE!>x<!>
}