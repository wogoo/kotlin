// FIR_IDENTICAL
// !LANGUAGE: +NewInference
// !USE_EXPERIMENTAL: kotlin.RequiresOptIn
// !DIAGNOSTICS: -UNUSED_PARAMETER

import kotlin.experimental.ExperimentalTypeInference

interface Build<T>

@OptIn(ExperimentalTypeInference::class)
fun <T> build(@BuilderInference fn: BuilderScope<T>.() -> Unit): Build<T> = TODO()

interface BuilderScope<T> {
    fun wrappedValueFn(fn: () -> Wrapped<T>)
    fun wrappedValueFn2(fn: T)
}

class Wrapped<T>(val value: T)

fun <L> wrappedFactory(x: L): Wrapped<L> = Wrapped(x)

val buildWithFnWrapped = build {
    wrappedValueFn2(1f)
    wrappedValueFn {
        wrappedFactory(1)
    }
}