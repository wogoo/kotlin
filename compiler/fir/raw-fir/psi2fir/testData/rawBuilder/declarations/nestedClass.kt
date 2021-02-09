abstract class Base(val s: String)

class Outer {
    class Derived(s: String) : Base(s)

    object Obj : Base("")
}

//FIR_FRAGMENTS_COUNT 5