// !LANGUAGE: +CompileTimeCalculations

@CompileTimeCalculation
open class A {
    override fun toString(): String {
        return "toString call from A class"
    }
}

@CompileTimeCalculation
class B : A()

@CompileTimeCalculation
class C

class D @CompileTimeCalculation constructor() {
    @CompileTimeCalculation
    override fun toString(): String {
        return super.toString()
    }
}

@CompileTimeCalculation
fun getDoubledValue(a: Int): Int {
    return 2 * a
}

@CompileTimeCalculation
fun checkToStringCorrectness(value: Any, startSymbol: Char): Boolean {
    val string = value.toString()
    return string.get(0) == startSymbol && string.get(1) == '@' && string.length == 10
}

@CompileTimeCalculation fun echo(value: String) = value

const val constStr = echo("Success")
const val concat1 = echo("String concatenation example: $constStr")
const val concat2 = echo("String concatenation example with primitive: ${1}")
const val concat3 = echo("String concatenation example with primitive and explicit toString call: ${1.toString()}")
const val concat4 = "String concatenation example with function that return primitive: ${getDoubledValue(10)}"
const val concat5 = "String concatenation example with A class: ${A()}"
const val concat6 = "String concatenation example with B class, where toString is FAKE_OVERRIDDEN: ${B()}"
const val concat7 = "String concatenation example with B class and explicit toString call: ${B().toString()}"
const val concat8 = "String concatenation example with C class, where toString isn't present; is it correct: ${checkToStringCorrectness(C(), 'C')}"
const val concat9 = "String concatenation example with D class, where toString is taken from Any; is it correct: ${checkToStringCorrectness(D(), 'D')}"