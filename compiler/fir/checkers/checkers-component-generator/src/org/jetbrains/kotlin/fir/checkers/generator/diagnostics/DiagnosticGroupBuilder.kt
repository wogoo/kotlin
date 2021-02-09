/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.checkers.generator.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.fir.FirSourceElement
import org.jetbrains.kotlin.fir.PrivateForInline
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.reflect.typeOf

class DiagnosticGroupBuilder @PrivateForInline constructor(private val name: String) {
    @PrivateForInline
    val diagnostics = mutableListOf<DiagnosticData>()

    @OptIn(PrivateForInline::class)
    inline fun <reified E : FirSourceElement, reified P : PsiElement> error(
        positioningStrategy: PositioningStrategy = PositioningStrategy.DEFAULT,
        crossinline init: DiagnosticBuilder.() -> Unit = {}
    ) = diagnosticDelegateProvider<E, P>(Severity.ERROR, positioningStrategy, init)


    @OptIn(PrivateForInline::class)
    inline fun <reified E : FirSourceElement, reified P : PsiElement> warning(
        positioningStrategy: PositioningStrategy = PositioningStrategy.DEFAULT,
        crossinline init: DiagnosticBuilder.() -> Unit = {}
    ) = diagnosticDelegateProvider<E, P>(Severity.WARNING, positioningStrategy, init)

    @PrivateForInline
    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified E : FirSourceElement, reified P : PsiElement> diagnosticDelegateProvider(
        severity: Severity,
        positioningStrategy: PositioningStrategy,
        crossinline init: DiagnosticBuilder.() -> Unit = {}
    ) = PropertyDelegateProvider<Any?, AlwaysReturningUnitPropertyDelegate> { _, property ->
        diagnostics += DiagnosticBuilder(
            severity,
            name = property.name,
            sourceElementType = typeOf<E>(),
            psiType = typeOf<P>(),
            positioningStrategy,
        ).apply(init).build()
        AlwaysReturningUnitPropertyDelegate
    }

    @PrivateForInline
    object AlwaysReturningUnitPropertyDelegate : ReadOnlyProperty<Any?, Unit> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = Unit
    }

    @PrivateForInline
    fun build(): DiagnosticGroup = DiagnosticGroup(name, diagnostics)

    companion object {
        @OptIn(PrivateForInline::class)
        inline fun build(name: String, init: DiagnosticGroupBuilder.() -> Unit): DiagnosticGroup =
            DiagnosticGroupBuilder(name).apply(init).build()
    }
}