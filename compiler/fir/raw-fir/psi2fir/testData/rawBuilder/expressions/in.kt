fun foo(x: Int, y: Int, c: Collection<Int>) =
    x in c && y !in c

//FIR_FRAGMENTS_COUNT 4