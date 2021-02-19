// WITH_REFLECT

import kotlin.reflect.jvm.reflect

fun test(f: () -> Any?): String {
    return if (f.reflect()!!.returnType.toString() == "kotlin.String") "OK" else "NOK"
}

fun box(): String {
    return test { "" }
}