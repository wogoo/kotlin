/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import org.jetbrains.kotlin.fir.resolve.calls.CallInfo
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.name.Name

abstract class FirLookupTrackerComponent : FirSessionComponent {

    abstract fun recordLookup(name: Name, source: FirSourceElement?, fileSource: FirSourceElement?, inScopes: Array<String>)
    open fun recordLookup(name: Name, source: FirSourceElement?, fileSource: FirSourceElement?, inScope: String) =
        recordLookup(name, source, fileSource, arrayOf(inScope))

    open fun recordLookup(callInfo: CallInfo, inScope: String) {
        recordLookup(callInfo.name, callInfo.callSite.source, callInfo.containingFile.source!!, inScope)
    }

    open fun recordLookup(callInfo: CallInfo, inScopes: Array<String>) {
        recordLookup(callInfo.name, callInfo.callSite.source, callInfo.containingFile.source!!, inScopes)
    }

    open fun recordLookup(callInfo: CallInfo, inScope: FirScope) {
        inScope.scopeLookupNames.takeIf { it.isNotEmpty() }?.let {
            recordLookup(callInfo, it)
        }
    }

    open fun recordLookup(typeRefs: Iterable<FirTypeRef>, fileSource: FirSourceElement?, scopes: Iterable<FirScope>) {
        for (typeRef in typeRefs) {
            recordLookup(typeRef, fileSource, scopes)
        }
    }

    open fun recordLookup(typeRef: FirTypeRef, fileSource: FirSourceElement?, scopes: Iterable<FirScope>) {
        val name = when (typeRef) {
//            is FirResolvedTypeRef -> typeRef.type.let {
//                when (it) {
//
//                }
//            }
            is FirUserTypeRef -> typeRef.qualifier.first().name
            else -> null
        }
        if (name != null) {
            val scopesLookupNames = ArrayList<String>()
            for (scope in scopes) {
                scopesLookupNames.addAll(scope.scopeLookupNames)
            }
            recordLookup(name, typeRef.source, fileSource, scopesLookupNames.toTypedArray())
        }
    }

    open fun recordLookup(typeRefs: Iterable<FirTypeRef>, fileSource: FirSourceElement?, scope: FirScope) {
        recordLookup(typeRefs, fileSource, listOf(scope))
    }

    open fun recordLookup(typeRef: FirTypeRef, fileSource: FirSourceElement?, scope: FirScope) {
        recordLookup(typeRef, fileSource, listOf(scope))
    }

    abstract fun flushLookups()
}

val FirSession.firLookupTracker: FirLookupTrackerComponent? by FirSession.nullableSessionComponentAccessor()
