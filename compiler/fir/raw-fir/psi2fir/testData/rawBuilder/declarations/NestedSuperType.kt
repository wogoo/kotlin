package p

abstract class My {
    abstract class NestedOne : My() {
        abstract class NestedTwo : NestedOne() {

        }
    }
}

class Your : My() {
    class NestedThree : NestedOne()
}

//FIR_FRAGMENTS_COUNT 5