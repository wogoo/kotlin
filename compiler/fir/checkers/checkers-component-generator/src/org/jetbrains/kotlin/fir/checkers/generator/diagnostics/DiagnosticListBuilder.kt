/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.checkers.generator.diagnostics

import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.fir.PrivateForInline
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class DiagnosticListBuilder private constructor() {
    @PrivateForInline
    val diagnosticGroups = mutableListOf<DiagnosticGroup>()

    @OptIn(PrivateForInline::class)
    inline fun group(groupName: String, init: DiagnosticGroupBuilder.() -> Unit) {
        diagnosticGroups += DiagnosticGroupBuilder.build(groupName, init)
    }

    @OptIn(PrivateForInline::class)
    private fun build() = DiagnosticList(diagnosticGroups)

    companion object {
        fun buildDiagnosticList(init: DiagnosticListBuilder.() -> Unit) =
            DiagnosticListBuilder().apply(init).build()
    }
}

class DiagnosticBuilder(
    private val severity: Severity,
    private val name: String,
    private val sourceElementType: KType,
    private val psiType: KType,
    private val positioningStrategy: PositioningStrategy,
) {
    @PrivateForInline
    val parameters = mutableListOf<DiagnosticParameter>()

    @OptIn(PrivateForInline::class, ExperimentalStdlibApi::class)
    inline fun <reified T> parameter(name: String) {
        if (parameters.size == 3) {
            error("Diagnostic cannot have more than 3 parameters")
        }
        parameters += DiagnosticParameter(
            name = name,
            type = typeOf<T>()
        )
    }

    @OptIn(PrivateForInline::class)
    fun build() = DiagnosticData(
        severity,
        name,
        sourceElementType,
        psiType,
        parameters,
        positioningStrategy,
    )
}