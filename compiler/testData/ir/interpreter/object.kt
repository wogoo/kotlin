// !LANGUAGE: +CompileTimeCalculations

@CompileTimeCalculation
class A {
    val a = 10

    companion object {
        @CompileTimeCalculation
        val static = -10

        val willNotCompute = -10

        @CompileTimeCalculation
        fun getStaticNumber(): Int {
            return Int.MAX_VALUE
        }
    }
}

object Obj {
    @CompileTimeCalculation
    val static = "Const val"

    val willNotCompute = "Will not compute"
}

const val num = A().a
const val numStatic = A.static
const val numStaticFromFun = A.getStaticNumber()
val willNotCompute = A.willNotCompute

const val valFromObject = Obj.static
val valFromObjectNotCompute = Obj.willNotCompute