/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.fir

import com.intellij.openapi.Disposable
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.MetadataSerializerFactory
import org.jetbrains.kotlin.backend.jvm.codegen.MetadataSerializer
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.codegen.serialization.JvmSerializationBindings
import org.jetbrains.kotlin.diagnostics.*
import org.jetbrains.kotlin.fir.FirPsiSourceElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.diagnostics.*
import org.jetbrains.kotlin.fir.backend.jvm.FirJvmBackendClassResolver
import org.jetbrains.kotlin.fir.backend.jvm.FirMetadataSerializer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.org.objectweb.asm.Type

class FirMetadataSerializerBuilder(val session: FirSession) : MetadataSerializerFactory {
    override fun invoke(
        context: JvmBackendContext,
        irClass: IrClass,
        type: Type,
        serializationBindings: JvmSerializationBindings,
        parent: MetadataSerializer?
    ): MetadataSerializer =
        FirMetadataSerializer(session, context, irClass, serializationBindings, parent)
}

class FirJvmFrontendToIrConverterBuilder(
    val rootDisposable: Disposable,
) : CompilationStageBuilder<FirFrontendOutputs, FrontendToIrConverterResult> {

    val messageCollector: MessageCollector? = null

    override fun build(): CompilationStage<FirFrontendOutputs, FrontendToIrConverterResult> {
        return FirJvmFrontendToIrConverter(messageCollector ?: MessageCollector.NONE)
    }

    operator fun invoke(body: FirJvmFrontendToIrConverterBuilder.() -> Unit): FirJvmFrontendToIrConverterBuilder {
        this.body()
        return this
    }
}

class FirJvmFrontendToIrConverter internal constructor(
    val messageCollector: MessageCollector
) : CompilationStage<FirFrontendOutputs, FrontendToIrConverterResult> {

    override fun execute(
        input: FirFrontendOutputs
    ): ExecutionResult<FrontendToIrConverterResult> {
        val (moduleFragment, symbolTable, sourceManager, components) = input.firAnalyzerFacade.convertToIr()

        val outputs = FrontendToIrConverterResult(
            moduleFragment, emptyList(),
            symbolTable, null,
            input.project,
            input.ktFiles,
            emptyList(),
            emptyMap(),
            false,
            input.configuration,
            input.module,
            sourceManager,
            FirJvmBackendClassResolver(components),
            FirMetadataSerializerBuilder(input.session),
            input.packagePartProvider
        )

        return ExecutionResult.Success(outputs, emptyList())
    }
}

private fun FirDiagnostic<*>.toRegularDiagnostic(): Diagnostic {
    val psiSource = element as FirPsiSourceElement<*>
    @Suppress("UNCHECKED_CAST")
    when (this) {
        is FirSimpleDiagnostic ->
            return SimpleDiagnostic(
                psiSource.psi, factory.psiDiagnosticFactory as DiagnosticFactory0<PsiElement>, severity
            )
        is FirDiagnosticWithParameters1<*, *> ->
            return DiagnosticWithParameters1(
                psiSource.psi, this.a, factory.psiDiagnosticFactory as DiagnosticFactory1<PsiElement, Any>, severity
            )
        is FirDiagnosticWithParameters2<*, *, *> ->
            return DiagnosticWithParameters2(
                psiSource.psi, this.a, this.b, factory.psiDiagnosticFactory as DiagnosticFactory2<PsiElement, Any, Any>, severity
            )
        is FirDiagnosticWithParameters3<*, *, *, *> ->
            return DiagnosticWithParameters3(
                psiSource.psi, this.a, this.b, this.c,
                factory.psiDiagnosticFactory as DiagnosticFactory3<PsiElement, Any, Any, Any>, severity
            )
    }
}

