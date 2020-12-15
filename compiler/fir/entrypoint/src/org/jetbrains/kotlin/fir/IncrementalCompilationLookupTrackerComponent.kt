/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.diagnostics.DiagnosticUtils.getLineAndColumnInPsiFile
import org.jetbrains.kotlin.fir.resolve.calls.CallInfo
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.render
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.incremental.components.Position
import org.jetbrains.kotlin.incremental.components.ScopeKind
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.util.concurrent.ConcurrentLinkedQueue

class IncrementalCompilationLookupTrackerComponent(
    private val lookupTracker: LookupTracker,
    private val firFileToPath: (FirSourceElement) -> String
) : FirLookupTrackerComponent() {

    private data class Lookup(
        val name: Name,
        val scopeFqName: String,
        val scopeKind: ScopeKind
    )

    private val lookupsToTypes = MultiMap.createSet<FirSourceElement, Lookup>()

    override fun recordLookup(callInfo: CallInfo, inScope: ConeKotlinType) {
        lookupsToTypes.putValue(
            callInfo.containingFile.source,
            Lookup(callInfo.name, inScope.render() /* TODO: check if correct */, ScopeKind.CLASSIFIER)
        )
    }

    override fun recordLookup(callInfo: CallInfo, inScope: FqName, isClassifier: Boolean) {
        lookupsToTypes.putValue(
            callInfo.containingFile.source,
            Lookup(callInfo.name, inScope.asString(), if (isClassifier) ScopeKind.CLASSIFIER else ScopeKind.PACKAGE)
        )
    }

    override fun flushLookups() {
        for (fileSource in lookupsToTypes.keySet()) {
            val path = firFileToPath(fileSource)
            for ((name, toScope, scopeKind) in lookupsToTypes.get(fileSource)) {
                lookupTracker.record(
                    path, Position.NO_POSITION,
                    toScope, scopeKind,
                    name.asString()
                )
            }
        }
    }
}

class DebugIncrementalCompilationLookupTrackerComponent(
    private val lookupTracker: LookupTracker,
    private val firFileToPath: (FirSourceElement) -> String
) : FirLookupTrackerComponent() {

    private class Lookup(
        val name: Name,
        val scopeFqName: String,
        val scopeKind: ScopeKind,
        val from: FirSourceElement?,
        val fromFile: FirSourceElement?
    )

    private val lookups = ConcurrentLinkedQueue<Lookup>()

    override fun recordLookup(
        callInfo: CallInfo,
        inScope: ConeKotlinType
    ) {
        lookups.add(Lookup(callInfo.name, inScope.render(), ScopeKind.CLASSIFIER, callInfo.callSite.source, callInfo.containingFile.source))
    }

    override fun recordLookup(callInfo: CallInfo, inScope: FqName, isClassifier: Boolean) {
        lookups.add(
            Lookup(
                callInfo.name, inScope.asString(), if (isClassifier) ScopeKind.CLASSIFIER else ScopeKind.PACKAGE,
                callInfo.callSite.source, callInfo.containingFile.source
            )
        )
    }

    override fun flushLookups() {
        val filesToPaths = HashMap<FirSourceElement, String>()
        for (lookup in lookups) {
            val path = filesToPaths.getOrPut(lookup.fromFile!!) {
                firFileToPath(lookup.fromFile)
            }

            val position = lookup.from?.let {
                it as? FirPsiSourceElement<*>
            }?.let {
                getLineAndColumnInPsiFile(it.psi.containingFile, it.psi.textRange).let { Position(it.line, it.column) }
            } ?: Position.NO_POSITION

            lookupTracker.record(
                path, position,
                lookup.scopeFqName, lookup.scopeKind,
                lookup.name.asString()
            )
        }
    }
}
