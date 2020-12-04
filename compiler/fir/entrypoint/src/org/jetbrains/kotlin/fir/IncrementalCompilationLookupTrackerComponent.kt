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
import org.jetbrains.kotlin.name.Name
import java.util.concurrent.ConcurrentLinkedQueue

class IncrementalCompilationLookupTrackerComponent(
    private val lookupTracker: LookupTracker,
    private val firFileToPath: (FirSourceElement) -> String
) : FirLookupTrackerComponent() {

    val lookupsToTypes = MultiMap.createSet<FirSourceElement, Pair<Name, ConeKotlinType>>()

    override fun recordLookup(
        callInfo: CallInfo,
        inScope: ConeKotlinType
    ) {
        lookupsToTypes.putValue(callInfo.containingFile.source, callInfo.name to inScope)
    }

    override fun flushLookups() {
        for (fileSource in lookupsToTypes.keySet()) {
            val path = firFileToPath(fileSource)
            for ((name, toScope) in lookupsToTypes.get(fileSource)) {
                lookupTracker.record(
                    path, Position.NO_POSITION,
                    toScope.render() /* TODO: check if correct */, ScopeKind.CLASSIFIER,
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

    protected class Lookup(
        val name: Name,
        val scopeFqName: String?,
        val inClassifier: ConeKotlinType?,
        val from: FirSourceElement?,
        val fromFile: FirSourceElement?
    )

    private val lookups = ConcurrentLinkedQueue<Lookup>()

    override fun recordLookup(
        callInfo: CallInfo,
        inScope: ConeKotlinType
    ) {
        lookups.add(Lookup(callInfo.name, null, inScope, callInfo.callSite.source, callInfo.containingFile.source))
    }

    override fun flushLookups() {
        val filesToPaths = HashMap<FirSourceElement, String>()
        for (lookup in lookups) {
            val scopeFqName = lookup.scopeFqName ?: lookup.inClassifier?.render() ?: error("lookup without target")

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
                scopeFqName, ScopeKind.CLASSIFIER,
                lookup.name.asString()
            )
        }
    }
}
