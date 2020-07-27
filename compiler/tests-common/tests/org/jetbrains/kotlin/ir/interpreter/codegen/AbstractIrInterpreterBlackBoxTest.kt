/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.interpreter.codegen

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContextImpl
import org.jetbrains.kotlin.backend.common.ir.BuiltinSymbolsBase
import org.jetbrains.kotlin.backend.jvm.JvmGeneratorExtensions
import org.jetbrains.kotlin.backend.jvm.JvmNameProvider
import org.jetbrains.kotlin.backend.jvm.serialization.JvmIdSignatureDescriptor
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.NoScopeRecordCliBindingTrace
import org.jetbrains.kotlin.codegen.CodegenTestCase
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.konan.DeserializedKlibModuleOrigin
import org.jetbrains.kotlin.descriptors.konan.KlibModuleOrigin
import org.jetbrains.kotlin.idea.MainFunctionDetector
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.backend.jvm.jvmResolveLibraries
import org.jetbrains.kotlin.ir.backend.jvm.serialization.EmptyLoggingContext
import org.jetbrains.kotlin.ir.backend.jvm.serialization.JvmIrLinker
import org.jetbrains.kotlin.ir.backend.jvm.serialization.JvmManglerDesc
import org.jetbrains.kotlin.ir.builders.TranslationPluginContext
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.descriptors.IrFunctionFactory
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrErrorExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.interpreter.IrInterpreter
import org.jetbrains.kotlin.ir.interpreter.checker.EvaluationMode
import org.jetbrains.kotlin.ir.interpreter.checker.IrCompileTimeChecker
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi2ir.Psi2IrConfiguration
import org.jetbrains.kotlin.psi2ir.Psi2IrTranslator
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.JvmResolveUtil
import org.jetbrains.kotlin.test.ConfigurationKind
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.TestJdkKind
import org.jetbrains.kotlin.util.DummyLogger
import org.junit.Assert
import org.junit.Assume
import java.io.File

abstract class AbstractIrInterpreterBlackBoxTest : CodegenTestCase() {
    private val fullRuntimeKlib = "libraries/stdlib/js-ir/build/classes/kotlin/js/main"

    override fun doMultiFileTest(wholeFile: File, files: List<TestFile>) {
        wholeFile.absolutePath.replace(".kt", ".exceptions.compiletime.txt")
        try {
            compile(files, true, false)
        } catch (e: Throwable) {
            //ignore
        }

        val project = myEnvironment.project
        val configuration = myEnvironment.configuration
        val psiFiles = myFiles.psiFiles

        val analysisResult = try {
            JvmResolveUtil.analyzeAndCheckForErrors(
                project,
                psiFiles,
                configuration,
                myEnvironment::createPackagePartProvider,
                NoScopeRecordCliBindingTrace(),
                klibList = emptyList()
            ).apply { this.throwIfError() }
        } catch (e: Throwable) {
            return
        }

        val generationState = GenerationState.Builder(
            project, classBuilderFactory, analysisResult.moduleDescriptor, analysisResult.bindingContext, psiFiles, configuration
        ).isIrBackend(true).build()

        val irModuleFragment = compile(psiFiles, generationState)

        val boxFunction = irModuleFragment.files
            .flatMap { it.declarations }
            .filterIsInstance<IrFunction>()
            .first { it.name.asString() == "box" && it.valueParameters.isEmpty() }

        val boxIrCall = IrCallImpl(
            UNDEFINED_OFFSET, UNDEFINED_OFFSET, irModuleFragment.irBuiltins.stringType, boxFunction.symbol as IrSimpleFunctionSymbol, 0, 0
        )

        //val canBeEvaluated = IrCompileTimeChecker(mode = EvaluationMode.FULL).visitCall(boxIrCall, null)
        @Suppress("UNCHECKED_CAST")
        val irInterpreter = IrInterpreter(irModuleFragment.irBuiltins, configuration[CommonConfigurationKeys.IR_BODY_MAP] as Map<IdSignature, IrBody>)        val interpreterResult = irInterpreter.interpret(boxIrCall)

        if (canBeEvaluated && interpreterResult is IrErrorExpression) Assert.fail(interpreterResult.description)
        if (!canBeEvaluated && interpreterResult is IrConst<*>) Assert.fail("Wrong checker")
        if (!canBeEvaluated && interpreterResult is IrErrorExpression) return
        Assert.assertEquals("OK", (interpreterResult as IrConst<*>).value)
    }

    private fun compile(files: Collection<KtFile>, state: GenerationState): IrModuleFragment {
        val extensions = JvmGeneratorExtensions()
        val mangler = JvmManglerDesc(MainFunctionDetector(state.bindingContext, state.languageVersionSettings))
        val psi2ir = Psi2IrTranslator(state.languageVersionSettings, Psi2IrConfiguration())
        val symbolTable = SymbolTable(JvmIdSignatureDescriptor(mangler), IrFactoryImpl, JvmNameProvider)
        val psi2irContext = psi2ir.createGeneratorContext(state.module, state.bindingContext, symbolTable, extensions)
        val pluginExtensions = IrGenerationExtension.getInstances(state.project)
        val functionFactory = IrFunctionFactory(psi2irContext.irBuiltIns, psi2irContext.symbolTable)
        psi2irContext.irBuiltIns.functionFactory = functionFactory

        val stubGenerator = DeclarationStubGenerator(
            psi2irContext.moduleDescriptor, psi2irContext.symbolTable, psi2irContext.irBuiltIns.languageVersionSettings, extensions
        )
        val frontEndContext = object : TranslationPluginContext {
            override val moduleDescriptor: ModuleDescriptor
                get() = psi2irContext.moduleDescriptor
            override val bindingContext: BindingContext
                get() = psi2irContext.bindingContext
            override val symbolTable: ReferenceSymbolTable
                get() = symbolTable
            override val typeTranslator: TypeTranslator
                get() = psi2irContext.typeTranslator
            override val irBuiltIns: IrBuiltIns
                get() = psi2irContext.irBuiltIns
        }
        val irLinker = JvmIrLinker(
            psi2irContext.moduleDescriptor,
            IrMessageLogger.None,
            psi2irContext.irBuiltIns,
            symbolTable,
            functionFactory,
            frontEndContext,
            stubGenerator,
            mangler
        ).apply {
            val klibForCompileTimeCalculations = jvmResolveLibraries(listOf(fullRuntimeKlib), DummyLogger).getFullList().single()
            addDeserializerForCompileTimeDeclarations(psi2irContext.moduleDescriptor, klibForCompileTimeCalculations)
        }

        val pluginContext by lazy {
            psi2irContext.run {
                val symbols = BuiltinSymbolsBase(irBuiltIns, moduleDescriptor.builtIns, symbolTable.lazyWrapper)
                IrPluginContextImpl(
                    moduleDescriptor, bindingContext, languageVersionSettings, symbolTable, typeTranslator, irBuiltIns, irLinker, IrMessageLogger.None, symbols
                )
            }
        }

        for (extension in pluginExtensions) {
            psi2ir.addPostprocessingStep { module ->
                val old = stubGenerator.unboundSymbolGeneration
                try {
                    stubGenerator.unboundSymbolGeneration = true
                    extension.generate(module, pluginContext)
                } finally {
                    stubGenerator.unboundSymbolGeneration = old
                }
            }
        }

        val dependencies = psi2irContext.moduleDescriptor.allDependencyModules.map {
            val kotlinLibrary = (it.getCapability(KlibModuleOrigin.CAPABILITY) as? DeserializedKlibModuleOrigin)?.library
            irLinker.deserializeIrModuleHeader(it, kotlinLibrary)
        }
        val irProviders = listOf(irLinker)

        val irModuleFragment = psi2ir.generateModuleFragment(
            psi2irContext, files, irProviders, pluginExtensions, expectDescriptorToSymbol = null
        )
        irLinker.postProcess()

        stubGenerator.unboundSymbolGeneration = true

        if (state.languageVersionSettings.supportsFeature(LanguageFeature.CompileTimeCalculations)) {
            state.configuration.put(CommonConfigurationKeys.IR_BODY_MAP, irLinker.deserializerForCompileTime!!.getBodies() as Map<*, *>)
        }

        // We need to compile all files we reference in Klibs
        irModuleFragment.files.addAll(dependencies.flatMap { it.files })
        return irModuleFragment
    }

    override fun getTestJdkKind(files: List<TestFile>): TestJdkKind = TestJdkKind.FULL_JDK

    override fun extractConfigurationKind(files: List<TestFile>): ConfigurationKind = ConfigurationKind.ALL

    override val backend: TargetBackend = TargetBackend.JVM_IR

    override fun setupEnvironment(environment: KotlinCoreEnvironment) {
        val newInference = environment.configuration.languageVersionSettings.supportsFeature(LanguageFeature.NewInference)
        environment.configuration.languageVersionSettings = LanguageVersionSettingsImpl(
            LanguageVersion.LATEST_STABLE, ApiVersion.LATEST_STABLE,
            specificFeatures = mapOf(
                LanguageFeature.CompileTimeCalculations to LanguageFeature.State.ENABLED,
                LanguageFeature.NewInference to if (newInference) LanguageFeature.State.ENABLED else LanguageFeature.State.DISABLED
            )
        )

        environment.configuration.put(JVMConfigurationKeys.KLIB_PATH_FOR_COMPILE_TIME, fullRuntimeKlib)
    }
}
