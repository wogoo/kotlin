/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.FirTypeAlias
import org.jetbrains.kotlin.fir.declarations.FirVariable
import org.jetbrains.kotlin.fir.resolve.calls.CallInfo
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.render
import org.jetbrains.kotlin.name.Name

abstract class FirLookupTrackerComponent : FirSessionComponent {

    abstract fun recordLookup(name: Name, source: FirSourceElement?, fileSource: FirSourceElement?, inScopes: Array<String>)

    fun recordLookup(name: Name, source: FirSourceElement?, fileSource: FirSourceElement?, inScope: String) {
        recordLookup(name, source, fileSource, arrayOf(inScope))
    }

    fun recordLookup(name: Name, source: FirSourceElement?, fileSource: FirSourceElement?, inScopes: Iterable<FirScope>) {
        val scopesLookupNames = ArrayList<String>()
        for (scope in inScopes) {
            scopesLookupNames.addAll(scope.scopeLookupNames)
        }
        recordLookup(name, source, fileSource, scopesLookupNames.toTypedArray())
    }

    fun recordLookup(name: Name, source: FirSourceElement?, fileSource: FirSourceElement?, inScope: FirScope) {
        recordLookup(name, source, fileSource, inScope.scopeLookupNames)
    }

    fun recordLookup(callInfo: CallInfo, inScope: String) {
        recordLookup(callInfo.name, callInfo.callSite.source, callInfo.containingFile.source, inScope)
    }

    fun recordLookup(callInfo: CallInfo, type: ConeKotlinType) {
        recordLookup(callInfo.name, callInfo.callSite.source, callInfo.containingFile.source, type.render().replace('/', '.'))
    }

    fun recordLookup(callInfo: CallInfo, inScope: FirScope) {
        recordLookup(callInfo.name, callInfo.callSite.source, callInfo.containingFile.source, inScope.scopeLookupNames)
    }

    fun recordLookup(typeRef: FirTypeRef, fileSource: FirSourceElement?, scopes: Iterable<FirScope>) {
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
            recordLookup(name, typeRef.source, fileSource, scopes)
        }
    }

    fun recordLookup(typeRef: FirTypeRef, fileSource: FirSourceElement?, scope: FirScope) {
        recordLookup(typeRef, fileSource, listOf(scope))
    }

    fun recordLookup(symbol: FirBasedSymbol<*>, source: FirSourceElement?, fileSource: FirSourceElement?, scopes: Iterable<FirScope>) {
        val name = when (val fir = symbol.fir) {
            is FirSimpleFunction -> fir.name
            is FirVariable<*> -> fir.name
            is FirRegularClass -> fir.name
            is FirTypeAlias -> fir.name
            else -> null
        }
        if (name != null) {
            recordLookup(name, source, fileSource, scopes)
        }
    }

    abstract fun flushLookups()
}

val FirSession.firLookupTracker: FirLookupTrackerComponent? by FirSession.nullableSessionComponentAccessor()
