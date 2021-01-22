import kotlin.reflect.KProperty1

// !LANGUAGE: +PartialCompileTimeCalculations



// advanced one
// if we work without inlining optimization, only first line will be evaluated
// otherwise, joinToString will be partially evaluated and inlide
fun exmpl1(obj: A): String { // evaluate to "A(a=${a})"
    val name = A::class.simpleName // we can write obj::class.simpleName and this will be evaluated if and only if obj's type is final
    val properties = A::class.members.filterIsInstance<KProperty1<Any, *>>()
    val propertiesFormatted = properties.joinToString { "${it.name}=${it.invoke(obj)}" }
    // in case of inlining
    /*
        val buffer = StringBuilder() //???
        buffer.append("")
        buffer.appendElement(element) { "${it.name}=${it.invoke(obj)}" }
        buffer.append("")
        val propertiesFormatted = buffer.toString()
    */


    return "$name($propertiesFormatted)"
}


fun exmpl2(): Int {
    var i = 0
    i++
    i++
    return i
}
