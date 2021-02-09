fun foo() {
    val x = 1
    var y = x + 1
    val z = y * 2
    y = y + z
    val w = y - x
    return w
}

//FIR_FRAGMENTS_COUNT 8