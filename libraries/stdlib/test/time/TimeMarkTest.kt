/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:OptIn(ExperimentalTime::class)
package test.time

import kotlin.test.*
import kotlin.time.*

class TimeMarkTest {

    @Test
    fun adjustment() {
        val timeSource = TestTimeSource()

        fun TimeMark.assertHasPassed(hasPassed: Boolean) {
            assertEquals(!hasPassed, this.hasNotPassedNow(), "Expected mark in the future")
            assertEquals(hasPassed, this.hasPassedNow(), "Expected mark in the past")

            assertEquals(!hasPassed, this.elapsedNow() < Duration.ZERO, "Mark elapsed: ${this.elapsedNow()}, expected hasPassed: $hasPassed")
        }

        val mark = timeSource.markNow()
        val markFuture1 = (mark + Duration.MILLISECOND).apply { assertHasPassed(false) }
        val markFuture2 = (mark - (-Duration.MILLISECOND)).apply { assertHasPassed(false) }

        val markPast1 = (mark - 1 * Duration.MILLISECOND).apply { assertHasPassed(true) }
        val markPast2 = (markFuture1 + (-2 * Duration.MILLISECOND)).apply { assertHasPassed(true) }

        timeSource += 500_000 * Duration.NANOSECOND

        val elapsed = mark.elapsedNow()
        val elapsedFromFuture = elapsed - Duration.MILLISECOND
        val elapsedFromPast = elapsed + Duration.MILLISECOND

        assertEquals(0.5 * Duration.MILLISECOND, elapsed)
        assertEquals(elapsedFromFuture, markFuture1.elapsedNow())
        assertEquals(elapsedFromFuture, markFuture2.elapsedNow())

        assertEquals(elapsedFromPast, markPast1.elapsedNow())
        assertEquals(elapsedFromPast, markPast2.elapsedNow())

        markFuture1.assertHasPassed(false)
        markPast1.assertHasPassed(true)

        timeSource += Duration.MILLISECOND

        markFuture1.assertHasPassed(true)
        markPast1.assertHasPassed(true)

    }
}
