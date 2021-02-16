/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.frontend.api.fir.utils

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.fir.FirRenderer
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.idea.fir.low.level.api.api.FirModuleResolveState
import org.jetbrains.kotlin.idea.fir.low.level.api.api.withFirDeclaration
import org.jetbrains.kotlin.idea.frontend.api.ValidityToken
import org.jetbrains.kotlin.idea.frontend.api.ValidityTokenOwner
import org.jetbrains.kotlin.idea.frontend.api.assertIsValidAndAccessible
import org.jetbrains.kotlin.idea.util.getElementTextInContext
import org.jetbrains.kotlin.psi.KtElement
import java.lang.ref.WeakReference
import kotlin.reflect.KClass

internal class FirRefWithValidityCheck<out D : FirDeclaration>(fir: D, resolveState: FirModuleResolveState, val token: ValidityToken) {
    private val firWeakRef = WeakReference(fir)
    private val resolveStateWeakRef = WeakReference(resolveState)

    private val data: DebugData? = DebugData.createIfTestMode(fir, resolveState)

    @TestOnly
    internal fun isCollected(): Boolean =
        firWeakRef.get() == null && resolveStateWeakRef.get() == null

    inline fun <R> withFir(phase: FirResolvePhase = FirResolvePhase.RAW_FIR, crossinline action: (fir: D) -> R): R {
        token.assertIsValidAndAccessible()
        val fir = firWeakRef.get()
            ?: throw EntityWasGarbageCollectedException("FirElement", additionalMessage = data?.data)
        val resolveState = resolveStateWeakRef.get()
            ?: throw EntityWasGarbageCollectedException("FirModuleResolveState", additionalMessage = data?.data)
        return when (phase) {
            FirResolvePhase.BODY_RESOLVE -> {
                /*
                 The BODY_RESOLVE phase is the maximum possible phase we can resolve our declaration to
                 So there is not need to run whole `action` under read lock
                 */
                action(fir.withFirDeclaration(resolveState, phase) { it })
            }
            else -> fir.withFirDeclaration(resolveState, phase) { action(it) }
        }
    }

    /**
     * Runs [action] with fir element *without* any lock hold
     * Consider using this only when you are completely sure
     * that fir or one of it's container already holds the lock (i.e, corresponding withFir call was made)
     */
    inline fun <R> withFirUnsafe(action: (fir: D) -> R): R {
        token.assertIsValidAndAccessible()
        val fir = firWeakRef.get()
            ?: throw EntityWasGarbageCollectedException("FirElement")
        return action(fir)
    }

    val resolveState
        get() = resolveStateWeakRef.get() ?: throw EntityWasGarbageCollectedException("FirModuleResolveState")

    inline fun <R> withFirAndCache(phase: FirResolvePhase = FirResolvePhase.RAW_FIR, crossinline createValue: (fir: D) -> R) =
        ValidityAwareCachedValue(token) {
            withFir(phase) { fir -> createValue(fir) }
        }
}


internal class DebugData(fir: FirDeclaration, resolveState: FirModuleResolveState) {
    val data = run {
        val sessionClass = fir.session::class.renderClass()
        val renderedDeclaration = fir.render(FirRenderer.RenderMode.Full)
        val declarationClass = fir::class.renderClass()
        val psiText = fir.psi?.let { psi -> (psi as? KtElement)?.getElementTextInContext() ?: psi.text }
        val containingFilePath = fir.psi?.containingFile?.virtualFile?.path
        val resolveStateClass = resolveState::class.renderClass()
        """
            |FirModuleResolveState is $resolveStateClass
            |
            |FirDeclaration is $declarationClass
            |
            |FirSession is $sessionClass
            |
            |
            |FirDeclaration rendered: 
            |$renderedDeclaration
            |
            |
            |PSI:
            |$psiText
            |
            |
            |Containing PSI file is $containingFilePath
        """.trimMargin()
    }

    private fun KClass<*>.renderClass() = qualifiedName ?: simpleName ?: "<no name>"

    companion object {
        fun createIfTestMode(fir: FirDeclaration, resolveState: FirModuleResolveState): DebugData? {
            if (!ApplicationManager.getApplication().isUnitTestMode) return null
            return DebugData(fir, resolveState)
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun <D : FirDeclaration> ValidityTokenOwner.firRef(fir: D, resolveState: FirModuleResolveState) =
    FirRefWithValidityCheck(fir, resolveState, token)