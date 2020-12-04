/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import org.jetbrains.kotlin.fir.resolve.calls.CallInfo
import org.jetbrains.kotlin.fir.types.ConeKotlinType

abstract class FirLookupTrackerComponent : FirSessionComponent {

    abstract fun recordLookup(callInfo: CallInfo, inScope: ConeKotlinType)

    abstract fun flushLookups()
}

val FirSession.firLookupTracker: FirLookupTrackerComponent? by FirSession.sessionComponentAccessor()
