// !LANGUAGE: +CompileTimeCalculations

@CompileTimeCalculation
fun tryFinally(initValue: Int): Int {
    var x = initValue
    try {
        return x
    } finally {
        x = x + 1 // result is never used
    }
}

@CompileTimeCalculation
fun tryFinally2(): String {
    var str: String = ""
    try {
        str += "Inside try block; "
    } finally {
        str += "Inside finally; "
    }
    str += "Outside try; "
    return str
}

@CompileTimeCalculation
fun tryCatchFinally(): Int {
    try {
        throw IllegalArgumentException("In try")
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("In catch")
    } finally {
        throw IllegalArgumentException("In finally")
    }
    return 0
}

const val a1 = tryFinally(0)
const val a2 = tryFinally(10)
const val b1 = tryFinally2()
const val c1 = tryCatchFinally()