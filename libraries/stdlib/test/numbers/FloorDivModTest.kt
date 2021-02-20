/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package test.numbers

import kotlin.math.sign
import kotlin.random.Random
import kotlin.test.*

class FloorDivModTest {

    @Test
    fun intDivMod() {
        fun check(a: Int, b: Int, expectedFd: Int? = null, expectedMod: Int? = null) {
            val div = a / b
            val rem = a % b
            val fd = a.floorDiv(b)
            val mod = a.mod(b)

            try {
                expectedFd?.let { assertEquals(it, fd) }
                expectedMod?.let { assertEquals(it, mod) }
                assertEquals(div - if (a.sign != b.sign && rem != 0) 1 else 0, fd)
                assertEquals(a - b * fd, mod)
            } catch (e: AssertionError) {
                fail("a: $a, b: $b, div: $div, rem: $rem, floorDiv: $fd, mod: $mod", e)
            }
        }

        check(10, -3, -4, -2)
        check(10, 3, 3, 1)
        check(-10, 3, -4, 2)
        check(-10, -3, 3, -1)
        val values = listOf(1, -1, 2, -2, 3, -3, Int.MIN_VALUE, Int.MAX_VALUE)
        for (a in values + 0) {
            for (b in values) {
                check(a, b)
            }
        }
        repeat(1000) {
            val a = Random.nextInt()
            val b = Random.nextInt().let { if (it == 0) 1 else it }
            check(a, b)
        }
    }

    @Test
    fun longDivMod() {
        fun check(a: Long, b: Long, expectedFd: Long? = null, expectedMod: Long? = null) {
            val div = a / b
            val rem = a % b
            val fd = a.floorDiv(b)
            val mod = a.mod(b)

            try {
                expectedFd?.let { assertEquals(it, fd) }
                expectedMod?.let { assertEquals(it, mod) }
                assertEquals(div - if (a.sign != b.sign && rem != 0L) 1 else 0, fd)
                assertEquals(a - b * fd, mod)
            } catch (e: AssertionError) {
                fail("a: $a, b: $b, div: $div, rem: $rem, floorDiv: $fd, mod: $mod", e)
            }
        }

        check(10, -3, -4, -2)
        check(10, 3, 3, 1)
        check(-10, 3, -4, 2)
        check(-10, -3, 3, -1)
        val values = listOf(1, -1, 2, -2, 3, -3, Long.MIN_VALUE, Long.MAX_VALUE)
        for (a in values + 0) {
            for (b in values) {
                check(a, b)
            }
        }
        repeat(1000) {
            val a = Random.nextLong()
            val b = Random.nextLong().let { if (it == 0L) 1 else it }
            check(a, b)
        }
    }

    @Test
    fun byteDivMod() {
        fun check(a: Byte, b: Byte, expectedFd: Int? = null, expectedMod: Int? = null) {
            val div = a / b
            val rem = a % b
            val fd = a.floorDiv(b)
            val mod = a.mod(b)

            try {
                expectedFd?.let { assertEquals(it, fd) }
                expectedMod?.let { assertEquals(it, mod) }
                assertEquals(div - if (a.toInt().sign != b.toInt().sign && rem != 0) 1 else 0, fd)
                assertEquals(a - b * fd, mod)
            } catch (e: AssertionError) {
                fail("a: $a, b: $b, div: $div, rem: $rem, floorDiv: $fd, mod: $mod", e)
            }
        }

        check(10, -3, -4, -2)
        check(10, 3, 3, 1)
        check(-10, 3, -4, 2)
        check(-10, -3, 3, -1)
        val values = listOf(1, -1, 2, -2, 3, -3, Byte.MIN_VALUE, Byte.MAX_VALUE)
        for (a in values + 0) {
            for (b in values) {
                check(a, b)
            }
        }
        repeat(1000) {
            val a = Random.nextInt().toByte()
            val b = Random.nextInt().toByte().let { if (it == 0.toByte()) 1 else it }
            check(a, b)
        }
    }
    
    @Test
    fun shortDivMod() {
        fun check(a: Short, b: Short, expectedFd: Int? = null, expectedMod: Int? = null) {
            val div = a / b
            val rem = a % b
            val fd = a.floorDiv(b)
            val mod = a.mod(b)

            try {
                expectedFd?.let { assertEquals(it, fd) }
                expectedMod?.let { assertEquals(it, mod) }
                assertEquals(div - if (a.toInt().sign != b.toInt().sign && rem != 0) 1 else 0, fd)
                assertEquals(a - b * fd, mod)
            } catch (e: AssertionError) {
                fail("a: $a, b: $b, div: $div, rem: $rem, floorDiv: $fd, mod: $mod", e)
            }
        }

        check(10, -3, -4, -2)
        check(10, 3, 3, 1)
        check(-10, 3, -4, 2)
        check(-10, -3, 3, -1)
        val values = listOf(1, -1, 2, -2, 3, -3, Short.MIN_VALUE, Short.MAX_VALUE)
        for (a in values + 0) {
            for (b in values) {
                check(a, b)
            }
        }
        repeat(1000) {
            val a = Random.nextInt().toShort()
            val b = Random.nextInt().toShort().let { if (it == 0.toShort()) 1 else it }
            check(a, b)
        }
    }

}

