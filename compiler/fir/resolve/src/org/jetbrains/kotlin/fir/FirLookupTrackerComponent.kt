/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import org.jetbrains.kotlin.fir.resolve.calls.CallInfo
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.name.FqName

abstract class FirLookupTrackerComponent : FirSessionComponent {

    abstract fun recordLookup(callInfo: CallInfo, inScope: ConeKotlinType)
    abstract fun recordLookup(callInfo: CallInfo, inScope: FqName, isClassifier: Boolean)

    open fun recordLookupIfNeeded(callInfo: CallInfo, inScope: FirScope, isClassifier: Boolean = false) {
        val lookupName = inScope.scopeLookupName
        if (lookupName != null) {
            recordLookup(callInfo, lookupName, isClassifier)
        } else {
            val lookupType = inScope.scopeLookupType
            if (lookupType != null) {
                recordLookup(callInfo, lookupType)
            }
        }
    }

    abstract fun flushLookups()
}

val FirSession.firLookupTracker: FirLookupTrackerComponent? by FirSession.sessionComponentAccessor()
