/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package samples.time

import samples.*

import kotlin.time.*

class Durations {

    @Sample
    fun toIsoString() {
        assertPrints((25 * Duration.NANOSECOND).toIsoString(), "PT0.000000025S")
        assertPrints((120.3 * Duration.MILLISECOND).toIsoString(), "PT0.120300S")
        assertPrints((30.5 * Duration.SECOND).toIsoString(), "PT30.500S")
        assertPrints((30.5 * Duration.MINUTE).toIsoString(), "PT30M30S")
        assertPrints((86420 * Duration.SECOND).toIsoString(), "PT24H0M20S")
        assertPrints((2 * Duration.DAY_24H).toIsoString(), "PT48H")
        assertPrints(Duration.ZERO.toIsoString(), "PT0S")
        assertPrints(Duration.INFINITE.toIsoString(), "PT2147483647H")
    }

    @Sample
    fun toStringDefault() {
        assertPrints((45 * Duration.DAY_24H), "45.0d")
        assertPrints((1.5 * Duration.DAY_24H), "36.0h")
        assertPrints((1230 * Duration.MINUTE), "20.5h")
        assertPrints((920 * Duration.MINUTE), "920m")
        assertPrints((1.546 * Duration.SECOND), "1.55s")
        assertPrints((25.12 * Duration.MILLISECOND), "25.1ms")
    }

    @Sample
    fun toStringDecimals() {
        assertPrints((1230 * Duration.MINUTE).toString(DurationUnit.DAYS, 2), "0.85d")
        assertPrints((1230 * Duration.MINUTE).toString(DurationUnit.HOURS, 2), "20.50h")
        assertPrints((1230 * Duration.MINUTE).toString(DurationUnit.MINUTES), "1230m")
        assertPrints((1230 * Duration.MINUTE).toString(DurationUnit.SECONDS), "73800s")
    }



}