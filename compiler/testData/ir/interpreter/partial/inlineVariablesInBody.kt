// !LANGUAGE: +PartialCompileTimeCalculations

class A(var a: Int) {
    fun inc() {
        a++
    }
}

fun box1(): Int { // evaluate to "return 10"
    val obj = A(10)
    return obj.a
}

fun box2(): Int { // nothing to evaluate, but can remove variable
    val obj = A(nonConstCall())
    return obj.a
}

fun box3(): Int { // nothing to evaluate
    val obj = A(10)
    obj.inc()
    return obj.a
}

fun box4(a: Int): Int { // nothing to evaluate, we can only inline `b`
    val b = nonConstCall(a)
    return b
}

fun box5(a: Int): Int { // will evaluate `c` and inline `b` and `c`
    val b = nonConstCall(a)
    val c = if (true) nonConstCall() else anotherNonConstCall()
    return b + c
}

fun box6(a: Int): Int { // evaluate to "return 6"
    var sum = 0
    sum += 1
    sum += 2
    sum += 3
    return sum
}

fun box7(a: Int): Int { // nothing to evaluate
    var sum = 0
    sum += 1
    sum += 2
    sum += A(10).a
    return sum
}

fun box8(a: Int): Int { // remove useless if
    var sum = 0
    sum += 1
    sum += 2
    if (sum == 0) sum += 3
    return sum
}

fun box8(a: Int): Int { // nothing to evaluate; `if` cannot be interpreted => must step into body and delete all visited variables from stack
    var sum = 0
    sum += 1
    sum += 2
    if (nonConstCall() == 0) sum += 3
    return sum
}

fun nonConstCall() = 10
fun nonConstCall(a: Int) = a
fun anotherNonConstCall() = 20
