import kotlinx.atomicfu.*
import kotlin.test.*

class LoopTest {
    private val a = atomic(0)
    private val r = atomic<A>(A("aaaa"))
    private val rs = atomic<String>("bbbb")

    private class A(val s: String)

    private inline fun casLoop(to: Int): Int {
        a.loop { cur ->
            if (a.compareAndSet(cur, to)) return a.value
        }
    }

    private inline fun AtomicInt.extensionLoop(to: Int): Int {
        loop { cur ->
            if (compareAndSet(cur, to)) return value
        }
    }

    private inline fun AtomicInt.returnExtensionLoop(to: Int): Int =
        loop { cur ->
            lazySet(cur + 10)
            return if (compareAndSet(cur, to)) value else incrementAndGet()
        }

    private inline fun AtomicRef<A>.casLoop(to: String): String = loop { cur ->
        if (compareAndSet(cur, A(to))) {
            val res = value.s
            return "${res}_AtomicRef<A>"
        }
    }

    private inline fun AtomicRef<String>.casLoop(to: String): String = loop { cur ->
        if (compareAndSet(cur, to)) return "${value}_AtomicRef<String>"
    }

    fun testDeclarationWithEqualNames() {
        check(r.casLoop("kk") == "kk_AtomicRef<A>")
        check(rs.casLoop("pp") == "pp_AtomicRef<String>")
    }

    fun testIntExtensionLoops() {
        check(casLoop(5) == 5)
        check(a.extensionLoop(66) == 66)
        check(a.returnExtensionLoop(777) == 77)
    }
}

fun box(): String {
    val testClass = LoopTest()
    testClass.testIntExtensionLoops()
    testClass.testDeclarationWithEqualNames()
    return "OK"
}