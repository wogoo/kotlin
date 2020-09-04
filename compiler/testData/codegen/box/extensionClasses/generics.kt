// TARGET_BACKEND: JVM_IR

context(T) class B<T : CharSequence> {
    val result = if (length == 2) "OK" else "fail"
}

fun box() = with("OK") {
    B().result
}