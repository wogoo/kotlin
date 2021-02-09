fun test1(s: String?) contract [returnsNotNull()] {
    contract {
        returns() implies (s != null)
    }
    test1()
}

//FIR_FRAGMENTS_COUNT 6