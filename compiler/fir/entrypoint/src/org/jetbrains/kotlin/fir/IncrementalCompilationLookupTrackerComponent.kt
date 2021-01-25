/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.diagnostics.DiagnosticUtils.getLineAndColumnInPsiFile
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.incremental.components.Position
import org.jetbrains.kotlin.incremental.components.ScopeKind
import org.jetbrains.kotlin.name.Name
import java.util.concurrent.ConcurrentLinkedQueue

class IncrementalCompilationLookupTrackerComponent(
    private val lookupTracker: LookupTracker,
    private val firFileToPath: (FirSourceElement) -> String
) : FirLookupTrackerComponent() {

    private class Lookup(
        val name: Name,
        val scopeFqNames: Array<String>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Lookup

            if (name != other.name) return false
            if (!scopeFqNames.contentEquals(other.scopeFqNames)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + scopeFqNames.contentHashCode()
            return result
        }
    }

    private val lookupsToTypes = MultiMap.createSet<FirSourceElement, Lookup>()

    override fun recordLookup(name: Name, source: FirSourceElement?, fileSource: FirSourceElement, inScopes: Array<String>) {
        lookupsToTypes.putValue(
            fileSource,
            Lookup(name, inScopes /* TODO: check if correct */)
        )
    }

    override fun flushLookups() {
        for (fileSource in lookupsToTypes.keySet()) {
            val path = firFileToPath(fileSource)
            for (record in lookupsToTypes.get(fileSource)) {
                for (toScope in record.scopeFqNames) {
                    lookupTracker.record(
                        path, Position.NO_POSITION,
                        toScope, ScopeKind.CLASSIFIER,
                        record.name.asString()
                    )
                }
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
        val scopeFqNames: Array<String>,
        val from: FirSourceElement?,
        val fromFile: FirSourceElement?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Lookup

            if (name != other.name) return false
            if (!scopeFqNames.contentEquals(other.scopeFqNames)) return false
            if (from != other.from) return false
            if (fromFile != other.fromFile) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + scopeFqNames.contentHashCode()
            result = 31 * result + (from?.hashCode() ?: 0)
            result = 31 * result + (fromFile?.hashCode() ?: 0)
            return result
        }
    }

    private val lookups = ConcurrentLinkedQueue<Lookup>()

    override fun recordLookup(name: Name, source: FirSourceElement?, fileSource: FirSourceElement, inScopes: Array<String>) {
        lookups.add(Lookup(name, inScopes, source, fileSource))
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

            for (toScope in lookup.scopeFqNames) {
                lookupTracker.record(
                    path, position,
                    toScope, ScopeKind.CLASSIFIER,
                    lookup.name.asString()
                )
            }
        }
    }
}
