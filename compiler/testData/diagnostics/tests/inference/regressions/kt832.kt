// !WITH_NEW_INFERENCE
//KT-832 Provide better diagnostics when type inference fails for an expression that returns a function
package a

fun <T> fooT2() : (t : T) -> T {
    return {it}
}

fun test() {
    <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER{NI}, TYPE_INFERENCE_NO_INFORMATION_FOR_PARAMETER{OI}!>fooT2<!>()(1) // here 1 should not be marked with an error
}
