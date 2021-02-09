class NoPrimary {
    val x: String

    constructor(x: String) {
        this.x = x
    }

    constructor(): this("")
}

//FIR_FRAGMENTS_COUNT 3