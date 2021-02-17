fun test(map: MutableMap<Int, List<Int>>) {
    map.<!INAPPLICABLE_CANDIDATE!>getOrPut<!>("Not a Int") {
        listOf(1)
    }
}
