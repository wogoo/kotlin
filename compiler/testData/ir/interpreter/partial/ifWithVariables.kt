// !LANGUAGE: +PartialCompileTimeCalculations

/*fun ifTrue(): Int {
    val b = if (true) {
        val a = nonConstCall()
        a * 10
    } else {
        val a = anotherNonConstCall()
        a * 10
    }
    val a = getInt()
    return a + b
}*/

class A(val a: Int)

fun ifFalse(): Int {
    val b = if (true) {
        val a = A(10)
        a
    } else {
        val a = A(20)
        a
    }
    val a = getInt()
    return a + b.a
}

fun nonConstCall() = 10
fun anotherNonConstCall() = 20
fun getInt() = 123
