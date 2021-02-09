abstract class A {
    abstract class Nested
}

typealias TA = A

class B : TA() {
    class NestedInB : Nested()
}

//FIR_FRAGMENTS_COUNT 5