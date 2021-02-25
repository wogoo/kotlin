// CHECK_BYTECODE_LISTING
// !JVM_DEFAULT_MODE: all
// TARGET_BACKEND: JVM
// JVM_TARGET: 1.8

import kotlin.reflect.KProperty

class Delegate {
    operator fun getValue(t: Any?, p: KProperty<*>): String = "OK"
}

interface Foo {

    fun test(): String {
        val x by Delegate()
        return x
    }
}

fun box(): String {
    return object : Foo {}.test()
}