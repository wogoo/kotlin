/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlinx.atomicfu

internal inline fun <T> atomicfu_getValue(getter: () -> T, setter: (T) -> Unit): T {
    return getter()
}

internal inline fun <T> atomicfu_setValue(value: T, getter: () -> T, setter: (T) -> Unit): Unit {
    setter(value)
}

internal inline fun <T> atomicfu_lazySet(value: T, getter: () -> T, setter: (T) -> Unit): Unit {
    setter(value)
}

internal inline fun <T> atomicfu_compareAndSet(expect: T, update: T, getter: () -> T, setter: (T) -> Unit): Boolean {
    if (getter() == expect) {
        setter(update)
        return true
    } else {
        return false
    }
}

internal inline fun <T> atomicfu_getAndSet(value: T, getter: () -> T, setter: (T) -> Unit): T {
    val oldValue = getter()
    setter(value)
    return oldValue
}

internal inline fun atomicfu_getAndIncrement(getter: () -> Int, setter: (Int) -> Unit): Int {
    val oldValue = getter()
    setter(oldValue + 1)
    return oldValue
}

internal inline fun atomicfu_getAndIncrement(getter: () -> Long, setter: (Long) -> Unit): Long {
    val oldValue = getter()
    setter(oldValue + 1)
    return oldValue
}

internal inline fun atomicfu_incrementAndGet(getter: () -> Int, setter: (Int) -> Unit): Int {
    setter(getter() + 1)
    return getter()
}

internal inline fun atomicfu_incrementAndGet(getter: () -> Long, setter: (Long) -> Unit): Long {
    setter(getter() + 1)
    return getter()
}

internal inline fun atomicfu_getAndDecrement(getter: () -> Int, setter: (Int) -> Unit): Int {
    val oldValue = getter()
    setter(oldValue - 1)
    return oldValue
}

internal inline fun atomicfu_getAndDecrement(getter: () -> Long, setter: (Long) -> Unit): Long {
    val oldValue = getter()
    setter(oldValue - 1)
    return oldValue
}

internal inline fun atomicfu_decrementAndGet(getter: () -> Int, setter: (Int) -> Unit): Int {
    setter(getter() - 1)
    return getter()
}

internal inline fun atomicfu_decrementAndGet(getter: () -> Long, setter: (Long) -> Unit): Long {
    setter(getter() - 1)
    return getter()
}

internal inline fun atomicfu_getAndAdd(value: Int, getter: () -> Int, setter: (Int) -> Unit): Int {
    val oldValue = getter()
    setter(oldValue + value)
    return oldValue
}

internal inline fun atomicfu_getAndAdd(value: Long, getter: () -> Long, setter: (Long) -> Unit): Long {
    val oldValue = getter()
    setter(oldValue + value)
    return oldValue
}

internal inline fun atomicfu_addAndGet(value: Int, getter: () -> Int, setter: (Int) -> Unit): Int {
    setter(getter() + value)
    return getter()
}

internal inline fun atomicfu_addAndGet(value: Long, getter: () -> Long, setter: (Long) -> Unit): Long {
    setter(getter() + value)
    return getter()
}

internal inline fun <T> atomicfu_loop(action: (T) -> Unit, getter: () -> T, setter: (T) -> Unit): Nothing {
    val cur = getter()
    while (true) {
        action(cur)
    }
}

internal inline fun <T> atomicfu_update(function: (T) -> T, getter: () -> T, setter: (T) -> Unit) {
    while (true) {
        val cur = getter()
        val upd = function(cur)
        if (atomicfu_compareAndSet(cur, upd, getter, setter)) return
    }
}

internal inline fun <T> atomicfu_getAndUpdate(function: (T) -> T, getter: () -> T, setter: (T) -> Unit): T {
    while (true) {
        val cur = getter()
        val upd = function(cur)
        if (atomicfu_compareAndSet(cur, upd, getter, setter)) return cur
    }
}

internal inline fun <T> atomicfu_updateAndGet(function: (T) -> T, getter: () -> T, setter: (T) -> Unit): T {
    while (true) {
        val cur = getter()
        val upd = function(cur)
        if (atomicfu_compareAndSet(cur, upd, getter, setter)) return upd
    }
}