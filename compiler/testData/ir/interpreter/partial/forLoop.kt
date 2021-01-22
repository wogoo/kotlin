// !LANGUAGE: +PartialCompileTimeCalculations

class A(a: Int) {
    fun getValue() = a
}

fun loop1(): Int { // evaluate to "return 6"
    val a = arrayOf(1, 2, 3)
    var sum = 0
    for (i in a) {
        sum += i
    }
    return sum
}

fun loop2(): Int {
    var sum = 0
    for (i in 0..3) {
        val obj = A(i)
        sum += obj.getValue()
    }
    return sum
}

fun loop3(): Int {
    var sum = 0
    for (i in 0..10) {
        if (i >= 5) break
        sum += i
    }
    return sum
}