// !LANGUAGE: +CompileTimeCalculations

@CompileTimeCalculation
fun foo(i: Int): Int = foo(i + 1)

const val a = foo(0)