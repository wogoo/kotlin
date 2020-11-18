/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.fir

import org.jetbrains.kotlin.backend.common.serialization.signature.IdSignatureDescriptor
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.JvmGeneratorExtensions
import org.jetbrains.kotlin.backend.jvm.MetadataSerializerFactory
import org.jetbrains.kotlin.backend.jvm.codegen.MetadataSerializer
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.codegen.serialization.JvmSerializationBindings
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.backend.Fir2IrConverter
import org.jetbrains.kotlin.fir.backend.jvm.FirJvmBackendClassResolver
import org.jetbrains.kotlin.fir.backend.jvm.FirJvmKotlinMangler
import org.jetbrains.kotlin.fir.backend.jvm.FirJvmVisibilityConverter
import org.jetbrains.kotlin.fir.backend.jvm.FirMetadataSerializer
import org.jetbrains.kotlin.ir.backend.jvm.serialization.JvmManglerDesc
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
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

class FirJvmFrontendToIrConverterBuilder : CompilationStageBuilder<FirFrontendOutputs, FrontendToIrConverterResult> {
    var messageCollector: MessageCollector? = null

    var irFactory: IrFactory = IrFactoryImpl

    override fun build(): CompilationStage<FirFrontendOutputs, FrontendToIrConverterResult> {
        return FirJvmFrontendToIrConverter(messageCollector ?: MessageCollector.NONE, irFactory)
    }

    operator fun invoke(body: FirJvmFrontendToIrConverterBuilder.() -> Unit): FirJvmFrontendToIrConverterBuilder {
        this.body()
        return this
    }
}

class FirJvmFrontendToIrConverter internal constructor(
    val messageCollector: MessageCollector,
    val irFactory: IrFactory
) : CompilationStage<FirFrontendOutputs, FrontendToIrConverterResult> {

    override fun execute(
        input: FirFrontendOutputs
    ): ExecutionResult<FrontendToIrConverterResult> {
        val signaturer = IdSignatureDescriptor(JvmManglerDesc())
        val (moduleFragment, symbolTable, sourceManager, components) = Fir2IrConverter.createModuleFragment(
            input.session, input.scopeSession!!, input.firFiles!!,
            input.configuration.languageVersionSettings, signaturer,
            JvmGeneratorExtensions(generateFacades = true), FirJvmKotlinMangler(input.session), irFactory,
            FirJvmVisibilityConverter
        )

        val outputs = FrontendToIrConverterResult(
            moduleFragment, emptyList(),
            symbolTable, null,
            input.project,
            input.sourceFiles,
            emptyList(),
            HashMap(),
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

