// !LANGUAGE: +CompileTimeCalculations

package test

@CompileTimeCalculation
class A(val a: Int, val b: String) {
    val String.propertyWithExtension: Int
        get() = this.length * a

    fun Int.funWithExtension(other: Int) = this + other
}

const val simpleName = A::class.simpleName!!
const val qualifiedName = A::class.qualifiedName!!
const val members = A::class.members.joinToString()
const val constructors = A::class.constructors.joinToString()