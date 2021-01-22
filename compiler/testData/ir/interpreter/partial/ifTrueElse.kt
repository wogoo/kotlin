// !LANGUAGE: +PartialCompileTimeCalculations

fun ifTrue(): String {
    return if (true) callIfTrue() else callIfFalse()
}

fun ifFalse(): String {
    return if (false) callIfTrue() else callIfFalse()
}

fun ifWithNonConstCondition(): String {
    return if (getBool()) callIfTrue() else callIfFalse()
}

fun callIfTrue() = "OK"
fun callIfFalse() = "NOK"
fun getBool() = true
