fun test(map: MutableMap<Int, List<Int>>) {
    map.<!INAPPLICABLE_CANDIDATE!>getOrDefault<!>("Not a Int", listOf(1))
}
