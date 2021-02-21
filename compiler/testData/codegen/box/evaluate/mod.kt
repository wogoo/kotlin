// IGNORE_FIR_DIAGNOSTICS
// TARGET_BACKEND: JVM

// WITH_RUNTIME

@Retention(AnnotationRetention.RUNTIME)
annotation class Ann(
        val p1: Byte,
        val p2: Short,
        val p3: Int,
        val p4: Long,
)

val prop1: Byte = 10.mod(2)
val prop2: Short = 10.mod(-3)
val prop3: Int = (-10).mod(4)
val prop4: Long = (-10).mod(-5)

@Ann(10.mod(2), 10.mod(-3), (-10).mod(4), (-10).mod(5)) class MyClass

fun box(): String {
    val annotation = MyClass::class.java.getAnnotation(Ann::class.java)!!
    if (annotation.p1 != prop1) return "fail 1, expected = ${prop1}, actual = ${annotation.p1}"
    if (annotation.p2 != prop2) return "fail 2, expected = ${prop2}, actual = ${annotation.p2}"
    if (annotation.p3 != prop3) return "fail 3, expected = ${prop3}, actual = ${annotation.p3}"
    if (annotation.p4 != prop4) return "fail 4, expected = ${prop4}, actual = ${annotation.p4}"
    return "OK"
}
