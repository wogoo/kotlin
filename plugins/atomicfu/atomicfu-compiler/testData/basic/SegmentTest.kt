import kotlinx.atomicfu.*
import kotlin.test.*

class SegmentTest {

    internal inline fun <S> AtomicRef<S>.foo(res: S): S { return res }

    private val tail = atomic("aaa")

    fun testClose() {
        val res = tail.foo("bbb")
        assertEquals(res, "bbb")
    }
}


fun box(): String {
    val testClass = SegmentTest()
    testClass.testClose()
    return "OK"
}