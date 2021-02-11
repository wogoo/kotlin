/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:OptIn(ExperimentalTime::class)
package test.time

import test.numbers.assertAlmostEquals
import kotlin.math.PI
import kotlin.native.concurrent.SharedImmutable
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.test.*
import kotlin.time.*

@SharedImmutable
private val units = DurationUnit.values()

class DurationTest {

    // construction white-box testing
    @Test
    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
    fun construction() {
        val expectStorageUnit = DurationUnit.NANOSECONDS
        val safeLongRange = -(Long.MAX_VALUE / 2)..(Long.MAX_VALUE / 2)

        repeat(1000) {
            val nanos = Random.nextLong(safeLongRange)
            val duration = nanos * Duration.NANOSECOND
            assertFalse(duration.isDouble())
            assertEquals(nanos, duration.toLongNanoseconds())
            assertEquals(nanos / 1_000_000_000, duration.toLong(DurationUnit.SECONDS))
            assertEquals((nanos / 86400_000_000_000).toInt(), duration.toInt(DurationUnit.DAYS))
        }

        assertTrue((PI * Duration.SECOND).isDouble())
        assertTrue((150 * (365 * Duration.DAY_24H)).isDouble())


        repeat(100) {
            val value = Random.nextInt(1_000_000)
            val unit = units.random()
            val expected = convertDurationUnit(value.toDouble(), unit, expectStorageUnit)
            assertEquals(expected, value.toDuration(unit).inNanoseconds)
            assertEquals(expected, value.toLong().toDuration(unit).inNanoseconds)
            assertEquals(expected, value.toDouble().toDuration(unit).inNanoseconds)
        }

        assertFailsWith<IllegalArgumentException> { Double.NaN.toDuration(DurationUnit.SECONDS) }
    }

    @Test
    fun equality() {
        val data = listOf<Pair<Double, DurationUnit>>(
            Pair(2.0, DurationUnit.DAYS),
            Pair(2.0, DurationUnit.HOURS),
            Pair(0.25, DurationUnit.MINUTES),
            Pair(1.0, DurationUnit.SECONDS),
            Pair(50.0, DurationUnit.MILLISECONDS),
            Pair(0.3, DurationUnit.MICROSECONDS),
            Pair(20_000_000_000.0, DurationUnit.NANOSECONDS),
            Pair(1.0, DurationUnit.NANOSECONDS)
        )

        for ((value, unit) in data) {
            repeat(10) {
                val d1 = value.toDuration(unit)
                val unit2 = units.random()
                val value2 = Duration.convert(value, unit, unit2)
                val d2 = value2.toDuration(unit2)
                assertEquals(d1, d2, "$value $unit in $unit2")
                assertEquals(d1.hashCode(), d2.hashCode())

                val d3 = (value2 * 2).toDuration(unit2)
                assertNotEquals(d1, d3, "$value $unit in $unit2")
            }
        }
    }

    @Test
    fun comparisons() {
        val d1 = 1000 * Duration.DAY_24H + 1 * Duration.NANOSECOND
        val d2 = d1 + 1 * Duration.NANOSECOND
        assertNotEquals(d1, d2)
        assertTrue(d1 < d2)
        assertFalse(d2 < d1)

        val d3 = d1 + 1.5 * Duration.NANOSECOND
        // assertTrue(d3 >= d2) // TODO: problem in addition: a + b < a + c for b > c
        // assertTrue(d3 >= d1) // TODO: problem in addition: a + b < a for positive b
    }


    @Test
    fun conversionFromNumber() {
        val n1 = Random.nextInt(Int.MAX_VALUE)
        val n2 = Random.nextLong(Long.MAX_VALUE)
        val n3 = Random.nextDouble()

        assertEquals(n1.toDuration(DurationUnit.DAYS), n1 * Duration.DAY_24H)
        assertEquals(n2.toDuration(DurationUnit.DAYS), n2 * Duration.DAY_24H)
        assertEquals(n3.toDuration(DurationUnit.DAYS), n3 * Duration.DAY_24H)

        assertEquals(n1.toDuration(DurationUnit.HOURS), n1 * Duration.HOUR)
        assertEquals(n2.toDuration(DurationUnit.HOURS), n2 * Duration.HOUR)
        assertEquals(n3.toDuration(DurationUnit.HOURS), n3 * Duration.HOUR)

        assertEquals(n1.toDuration(DurationUnit.MINUTES), n1 * Duration.MINUTE)
        assertEquals(n2.toDuration(DurationUnit.MINUTES), n2 * Duration.MINUTE)
        assertEquals(n3.toDuration(DurationUnit.MINUTES), n3 * Duration.MINUTE)

        assertEquals(n1.toDuration(DurationUnit.SECONDS), n1 * Duration.SECOND)
        assertEquals(n2.toDuration(DurationUnit.SECONDS), n2 * Duration.SECOND)
        assertEquals(n3.toDuration(DurationUnit.SECONDS), n3 * Duration.SECOND)

        assertEquals(n1.toDuration(DurationUnit.MILLISECONDS), n1 * Duration.MILLISECOND)
        assertEquals(n2.toDuration(DurationUnit.MILLISECONDS), n2 * Duration.MILLISECOND)
        assertEquals(n3.toDuration(DurationUnit.MILLISECONDS), n3 * Duration.MILLISECOND)

        assertEquals(n1.toDuration(DurationUnit.MICROSECONDS), n1 * Duration.MICROSECOND)
        assertEquals(n2.toDuration(DurationUnit.MICROSECONDS), n2 * Duration.MICROSECOND)
        assertEquals(n3.toDuration(DurationUnit.MICROSECONDS), n3 * Duration.MICROSECOND)

        assertEquals(n1.toDuration(DurationUnit.NANOSECONDS), n1 * Duration.NANOSECOND)
        assertEquals(n2.toDuration(DurationUnit.NANOSECONDS), n2 * Duration.NANOSECOND)
        assertEquals(n3.toDuration(DurationUnit.NANOSECONDS), n3 * Duration.NANOSECOND)
    }

    @Test
    fun conversionToNumber() {
        assertEquals(24.0, (1 * Duration.DAY_24H).inHours)
        assertEquals(0.5, (12 * Duration.HOUR).inDays)
        assertEquals(15.0, (0.25 * Duration.HOUR).inMinutes)
        assertEquals(600.0, (10 * Duration.MINUTE).inSeconds)
        assertEquals(500.0, (0.5 * Duration.SECOND).inMilliseconds)
        assertEquals(50_000.0, (0.05 * Duration.SECOND).inMicroseconds)
        assertEquals(50_000.0, (0.05 * Duration.MILLISECOND).inNanoseconds)

        assertEquals(365 * 86400e9, (365 * Duration.DAY_24H).inNanoseconds)

        assertEquals(0.0, Duration.ZERO.inNanoseconds)

        assertEquals(10500, (10.5 * Duration.SECOND).toLongMilliseconds())
        assertEquals(11, (11.5 * Duration.MILLISECOND).toLongMilliseconds())
        assertEquals(-11, ((-11.5) * Duration.MILLISECOND).toLongMilliseconds())
        assertEquals(252_000_000, (252 * Duration.MILLISECOND).toLongNanoseconds())
        assertEquals(Long.MAX_VALUE, ((365 * Duration.DAY_24H) * 293).toLongNanoseconds()) // clamping overflowed value

        repeat(100) {
            val value = Random.nextLong(1000)
            val unit = units.random()
            val unit2 = units.random()

            assertAlmostEquals(Duration.convert(value.toDouble(), unit, unit2), value.toDuration(unit).toDouble(unit2))
        }
    }

    @Test
    fun componentsOfProperSum() {
        repeat(100) {
            val h = Random.nextInt(24)
            val m = Random.nextInt(60)
            val s = Random.nextInt(60)
            val ns = Random.nextInt(1e9.toInt())
            (h * Duration.HOUR + m * Duration.MINUTE + s * Duration.SECOND + ns * Duration.NANOSECOND).run {
                toComponents { seconds, nanoseconds ->
                    assertEquals(h.toLong() * 3600 + m * 60 + s, seconds)
                    assertEquals(ns, nanoseconds)
                }
                toComponents { minutes, seconds, nanoseconds ->
                    assertEquals(h * 60 + m, minutes)
                    assertEquals(s, seconds)
                    assertEquals(ns, nanoseconds)
                }
                toComponents { hours, minutes, seconds, nanoseconds ->
                    assertEquals(h, hours)
                    assertEquals(m, minutes)
                    assertEquals(s, seconds)
                    assertEquals(ns, nanoseconds, "ns component of duration ${toIsoString()} differs too much, expected: $ns, actual: $nanoseconds")
                }
                toComponents { days, hours, minutes, seconds, nanoseconds ->
                    assertEquals(0, days)
                    assertEquals(h, hours)
                    assertEquals(m, minutes)
                    assertEquals(s, seconds)
                    assertEquals(ns, nanoseconds)
                }
            }
        }
    }

    @Test
    fun componentsOfCarriedSum() {
        (36 * Duration.HOUR + 90 * Duration.MINUTE + 90 * Duration.SECOND + 1500 * Duration.MILLISECOND).run {
            toComponents { days, hours, minutes, seconds, nanoseconds ->
                assertEquals(1, days)
                assertEquals(13, hours)
                assertEquals(31, minutes)
                assertEquals(31, seconds)
                assertEquals(500_000_000, nanoseconds)
            }
        }
    }

    @Test
    fun infinite() {
        assertTrue(Duration.INFINITE.isInfinite())
        assertTrue((-Duration.INFINITE).isInfinite())
        assertTrue((Double.POSITIVE_INFINITY * Duration.NANOSECOND).isInfinite())

        // seconds converted to nanoseconds overflow to infinite
        assertTrue((Double.MAX_VALUE * Duration.SECOND).isInfinite())
        assertTrue((-Double.MAX_VALUE * Duration.SECOND).isInfinite())
    }


    @Test
    fun negation() {
        repeat(100) {
            val value = Random.nextLong()
            val unit = units.random()

            assertEquals((-value).toDuration(unit), -value.toDuration(unit))
        }
    }

    @Test
    fun signAndAbsoluteValue() {
        val negative = -1 * Duration.SECOND
        val positive = 1 * Duration.SECOND
        val zero = Duration.ZERO

        assertTrue(negative.isNegative())
        assertFalse(zero.isNegative())
        assertFalse(positive.isNegative())

        assertFalse(negative.isPositive())
        assertFalse(zero.isPositive())
        assertTrue(positive.isPositive())

        assertEquals(positive, negative.absoluteValue)
        assertEquals(positive, positive.absoluteValue)
        assertEquals(zero, zero.absoluteValue)
    }

    @Test
    fun negativeZero() {
        fun equivalentToZero(value: Duration) {
            assertEquals(Duration.ZERO, value)
            assertEquals(Duration.ZERO, value.absoluteValue)
            assertEquals(value, value.absoluteValue)
            assertEquals(value, value.absoluteValue)
            assertFalse(value.isNegative())
            assertFalse(value.isPositive())
            assertEquals(Duration.ZERO.toString(), value.toString())
            assertEquals(Duration.ZERO.toIsoString(), value.toIsoString())
            assertEquals(Duration.ZERO.inSeconds, value.inSeconds)
            assertEquals(0, Duration.ZERO.compareTo(value))
            assertEquals(0, Duration.ZERO.inNanoseconds.compareTo(value.inNanoseconds))
        }
        equivalentToZero((-0.0 * Duration.SECOND))
        equivalentToZero((-0.0).toDuration(DurationUnit.DAYS))
        equivalentToZero(-Duration.ZERO)
        equivalentToZero((-1 * Duration.SECOND) / Double.POSITIVE_INFINITY)
        equivalentToZero((0 * Duration.SECOND) / -1)
        equivalentToZero((-1 * Duration.SECOND) * 0.0)
        equivalentToZero((0 * Duration.SECOND) * -1)
    }


    @Test
    fun addition() {
        assertEquals(1.5 * Duration.HOUR, 1 * Duration.HOUR + 30 * Duration.MINUTE)
        assertEquals(0.5 * Duration.DAY_24H, 6 * Duration.HOUR + 360 * Duration.MINUTE)
        assertEquals(0.5 * Duration.SECOND, 200 * Duration.MILLISECOND + 300_000 * Duration.MICROSECOND)

        assertEquals(1.125 * Duration.NANOSECOND, 1 * Duration.NANOSECOND + 0.125 * Duration.NANOSECOND)

        val year = 365 * Duration.DAY_24H
        assertEquals(1 * Duration.NANOSECOND, 100 * year + 1 * Duration.NANOSECOND - 100 * year, "no rounding in long range")
        assertEquals(280 * year, 140 * year + 140 * year, "long overflows to double")
        assertEquals(year * 300, year * 300 + 1 * Duration.NANOSECOND, "double underflow")

        assertFailsWith<IllegalArgumentException> { Duration.INFINITE + (-Duration.INFINITE) }
    }

    @Test
    fun subtraction() {
        assertEquals(10 * Duration.HOUR, 0.5 * Duration.DAY_24H - 120 * Duration.MINUTE)
        assertEquals(850 * Duration.MILLISECOND, Duration.SECOND - 150 * Duration.MILLISECOND)

        assertEquals(0.625 * Duration.NANOSECOND, Duration.NANOSECOND - 0.375 * Duration.NANOSECOND)

        val year = 365 * Duration.DAY_24H
        assertEquals(-1 * Duration.NANOSECOND, 100 * year - Duration.NANOSECOND - 100 * year, "no rounding in long range")
        assertEquals(-280 * year, -140 * year - 140 * year, "long overflows to double")
        assertEquals(year * 300, year * 300 - Duration.NANOSECOND, "double underflow")

        assertFailsWith<IllegalArgumentException> { Duration.INFINITE - Duration.INFINITE }
    }

    @Test
    fun multiplication() {
        assertEquals(Duration.DAY_24H, 12 * Duration.HOUR * 2)
        assertEquals(Duration.DAY_24H, 60 * Duration.MINUTE * 24.0)
        assertEquals(Duration.MICROSECOND, 20 * Duration.NANOSECOND * 50)

        assertEquals(Duration.DAY_24H, 2 * (12 * Duration.HOUR))
        assertEquals(12.5 * Duration.HOUR, 12.5 * (60 * Duration.MINUTE))
        assertEquals(1 * Duration.MICROSECOND, 50 * (20 * Duration.NANOSECOND))

        assertEquals(Duration.ZERO, 0 * (1 * Duration.HOUR))
        assertEquals(Duration.ZERO, (1 * Duration.SECOND) * 0.0)

        assertFailsWith<IllegalArgumentException> { Duration.INFINITE * 0 }
        assertFailsWith<IllegalArgumentException> { 0 * Duration.INFINITE }
    }

    @Test
    fun divisionByNumber() {
        assertEquals(12 * Duration.HOUR, Duration.DAY_24H / 2)
        assertEquals(60 * Duration.MINUTE, Duration.DAY_24H / 24.0)
        assertEquals(20 * Duration.SECOND, 2 * Duration.MINUTE / 6)

        assertEquals(Duration.INFINITE, Duration.SECOND / 0.0)
        assertEquals(-Duration.INFINITE, -Duration.SECOND / 0.0)
        assertFailsWith<IllegalArgumentException> { Duration.INFINITE / Double.POSITIVE_INFINITY }
        assertFailsWith<IllegalArgumentException> { Duration.ZERO / 0 }
    }

    @Test
    fun divisionByDuration() {
        assertEquals(24.0, Duration.DAY_24H / Duration.HOUR)
        assertEquals(0.1, (9 * Duration.MINUTE) / (1.5 * Duration.HOUR))
        assertEquals(50.0, Duration.MICROSECOND / (20 * Duration.NANOSECOND))

        assertTrue((Duration.INFINITE / Duration.INFINITE).isNaN())
    }

    @Test
    fun toIsoString() {
        // zero
        assertEquals("PT0S", Duration.ZERO.toIsoString())

        // single unit
        assertEquals("PT24H", Duration.DAY_24H.toIsoString())
        assertEquals("PT1H", Duration.HOUR.toIsoString())
        assertEquals("PT1M", Duration.MINUTE.toIsoString())
        assertEquals("PT1S", Duration.SECOND.toIsoString())
        assertEquals("PT0.001S", Duration.MILLISECOND.toIsoString())
        assertEquals("PT0.000001S", Duration.MICROSECOND.toIsoString())
        assertEquals("PT0.000000001S", Duration.NANOSECOND.toIsoString())

        // rounded to zero
        assertEquals("PT0S", (0.1 * Duration.NANOSECOND).toIsoString())
        assertEquals("PT0S", (0.9 * Duration.NANOSECOND).toIsoString())

        // several units combined
        assertEquals("PT24H1M", (Duration.DAY_24H + Duration.MINUTE).toIsoString())
        assertEquals("PT24H0M1S", (Duration.DAY_24H + Duration.SECOND).toIsoString())
        assertEquals("PT24H0M0.001S", (Duration.DAY_24H + Duration.MILLISECOND).toIsoString())
        assertEquals("PT1H30M", (Duration.HOUR + 30 * Duration.MINUTE).toIsoString())
        assertEquals("PT1H0M0.500S", (Duration.HOUR + 500 * Duration.MILLISECOND).toIsoString())
        assertEquals("PT2M0.500S", (2 * Duration.MINUTE + 500 * Duration.MILLISECOND).toIsoString())
        assertEquals("PT1M30.500S", (90_500 * Duration.MILLISECOND).toIsoString())

        // negative
        assertEquals("-PT23H45M", (-Duration.DAY_24H + 15 * Duration.MINUTE).toIsoString())
        assertEquals("-PT24H15M", (-Duration.DAY_24H - 15 * Duration.MINUTE).toIsoString())

        // infinite
        assertEquals("PT2147483647H", Duration.INFINITE.toIsoString())
    }

    @Test
    fun toStringInUnits() {
        var d =
            1 * Duration.DAY_24H + 15 * Duration.HOUR + 31 * Duration.MINUTE + 45 * Duration.SECOND +
                    678 * Duration.MILLISECOND + 920 * Duration.MICROSECOND + 516.34 * Duration.NANOSECOND

        fun test(unit: DurationUnit, vararg representations: String) {
            assertFails { d.toString(unit, -1) }
            assertEquals(representations.toList(), representations.indices.map { d.toString(unit, it) })
        }

        test(DurationUnit.DAYS, "2d", "1.6d", "1.65d", "1.647d")
        test(DurationUnit.HOURS, "40h", "39.5h", "39.53h")
        test(DurationUnit.MINUTES, "2372m", "2371.8m", "2371.76m")
        d -= 39 * Duration.HOUR
        test(DurationUnit.SECONDS, "1906s", "1905.7s", "1905.68s", "1905.679s")
        d -= 1904 * Duration.SECOND
        test(DurationUnit.MILLISECONDS, "1679ms", "1678.9ms", "1678.92ms", "1678.921ms")
        d -= 1678 * Duration.MILLISECOND
        test(DurationUnit.MICROSECONDS, "921us", "920.5us", "920.52us", "920.516us")
        d -= 920 * Duration.MICROSECOND
        // sub-nanosecond precision errors
        test(DurationUnit.NANOSECONDS, "516ns", "516.3ns", "516.31ns", "516.313ns", "516.3125ns")
        d = (d - 516 * Duration.NANOSECOND) / 17
        test(DurationUnit.NANOSECONDS, "0ns", "0.0ns", "0.02ns", "0.018ns", "0.0184ns")

        d = Double.MAX_VALUE * Duration.NANOSECOND
        test(DurationUnit.DAYS, "2.08e+294d")
        test(DurationUnit.NANOSECONDS, "1.80e+308ns")

        assertEquals("0.500000000000s", (0.5 * Duration.SECOND).toString(DurationUnit.SECONDS, 100))
        assertEquals("99999000000000.000000000000ns", (99_999 * Duration.SECOND).toString(DurationUnit.NANOSECONDS, 15))
        assertEquals("1.00e+14ns", (100_000 * Duration.SECOND).toString(DurationUnit.NANOSECONDS, 9))

        d = Duration.INFINITE
        test(DurationUnit.DAYS, "Infinity", "Infinity")
        d = -Duration.INFINITE
        test(DurationUnit.NANOSECONDS, "-Infinity", "-Infinity")
    }


    @Test
    fun toStringDefault() {
        fun test(duration: Duration, vararg expectedOptions: String) {
            val actual = duration.toString()

            if (!expectedOptions.contains(actual)) {
                assertEquals<Any>(expectedOptions.toList(), duration.toString())
            }
            if (duration > Duration.ZERO)
                assertEquals("-$actual", (-duration).toString())
        }

        test(101 * Duration.DAY_24H, "101d")
        test(45.3 * Duration.DAY_24H, "45.3d")
        test(45 * Duration.DAY_24H, "45.0d")

        test(40.5 * Duration.DAY_24H, "972h")
        test(40 * Duration.HOUR + 15 * Duration.MINUTE, "40.3h", "40.2h")
        test(40 * Duration.HOUR, "40.0h")

        test(12.5 * Duration.HOUR, "750m")
        test(30 * Duration.MINUTE, "30.0m")
        test(17.5 * Duration.MINUTE, "17.5m")

        test(16.5 * Duration.MINUTE, "990s")
        test(90.36 * Duration.SECOND, "90.4s")
        test(50 * Duration.SECOND, "50.0s")
        test(1.3 * Duration.SECOND, "1.30s")
        test(1 * Duration.SECOND, "1.00s")

        test(0.5 * Duration.SECOND, "500ms")
        test(40.2 * Duration.MILLISECOND, "40.2ms")
        test(4.225 * Duration.MILLISECOND, "4.23ms", "4.22ms")
        test(4.245 * Duration.MILLISECOND, "4.25ms")
        test(1 * Duration.MILLISECOND, "1.00ms")

        test(0.75 * Duration.MILLISECOND, "750us")
        test(75.35 * Duration.MICROSECOND, "75.4us", "75.3us")
        test(7.25 * Duration.MICROSECOND, "7.25us")
        test(1.035 * Duration.MICROSECOND, "1.04us", "1.03us")
        test(1.005 * Duration.MICROSECOND, "1.01us", "1.00us")

        test(950.5 * Duration.NANOSECOND, "951ns", "950ns")
        test(85.23 * Duration.NANOSECOND, "85.2ns")
        test(8.235 * Duration.NANOSECOND, "8.24ns", "8.23ns")
        test(1.3 * Duration.NANOSECOND, "1.30ns")

        test(0.75 * Duration.NANOSECOND, "0.75ns")
        test(0.7512 * Duration.NANOSECOND, "0.7512ns")
        test(0.023 * Duration.NANOSECOND, "0.023ns")
        test(0.0034 * Duration.NANOSECOND, "0.0034ns")
        test(0.0000035 * Duration.NANOSECOND, "0.0000035ns")

        test(Duration.ZERO, "0s")
        test((365 * Duration.DAY_24H) * 10000, "3650000d")
        test((300 * Duration.DAY_24H) * 100000, "3.00e+7d")
        test((365 * Duration.DAY_24H) * 100000, "3.65e+7d")

        val universeAge = (365.25 * Duration.DAY_24H) * 13.799e9
        val planckTime = 5.4e-44 * Duration.SECOND

        test(universeAge, "5.04e+12d")
        test(planckTime, "5.40e-44s")
        test(Double.MAX_VALUE * Duration.NANOSECOND, "2.08e+294d")
        test(Duration.INFINITE, "Infinity")
    }

}
