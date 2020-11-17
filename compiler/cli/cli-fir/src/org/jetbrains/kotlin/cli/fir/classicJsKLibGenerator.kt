/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.fir

import com.intellij.openapi.Disposable
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.backend.js.expectActualLinker
import org.jetbrains.kotlin.ir.backend.js.serializeModuleIntoKlib
import org.jetbrains.kotlin.ir.util.ExpectDeclarationRemover
import org.jetbrains.kotlin.ir.visitors.acceptVoid

class ClassicJsKLibGeneratorState(
)

class ClassicJsKLibGeneratorResult(
)

class ClassicJsKLibGeneratorBuilder(
    val rootDisposable: Disposable,
) : CompilationStageBuilder<ClassicJsFrontendToIrConverterResult, ClassicJsKLibGeneratorResult, ClassicJsKLibGeneratorState> {

    var outputKlibPath: String? = null

    var nopack: Boolean? = null

    override fun build(): CompilationStage<ClassicJsFrontendToIrConverterResult, ClassicJsKLibGeneratorResult, ClassicJsKLibGeneratorState> {
        return ClassicJsKLibGenerator(outputKlibPath!!, nopack!!)
    }

    operator fun invoke(body: ClassicJsKLibGeneratorBuilder.() -> Unit): ClassicJsKLibGeneratorBuilder {
        this.body()
        return this
    }
}

class ClassicJsKLibGenerator internal constructor(
    val outputKlibPath: String,
    val nopack: Boolean
) : CompilationStage<ClassicJsFrontendToIrConverterResult, ClassicJsKLibGeneratorResult, ClassicJsKLibGeneratorState> {

    fun newState(): ClassicJsKLibGeneratorState {
        return  ClassicJsKLibGeneratorState()
    }

    override fun execute(input: ClassicJsFrontendToIrConverterResult): ExecutionResult<ClassicJsKLibGeneratorResult, ClassicJsKLibGeneratorState> =
        execute(input, newState())

    override fun execute(
        input: ClassicJsFrontendToIrConverterResult,
        state: ClassicJsKLibGeneratorState
    ): ExecutionResult<ClassicJsKLibGeneratorResult, ClassicJsKLibGeneratorState> {
        val configuration = input.configuration

        val moduleName = configuration[CommonConfigurationKeys.MODULE_NAME]!!

        val (moduleFragment, icData, psi2IrContext, project, files, dependenciesList, expectDescriptorToSymbol, hasErrors) = input

        if (!configuration.expectActualLinker) {
            moduleFragment.acceptVoid(ExpectDeclarationRemover(psi2IrContext.symbolTable, false))
        }

        serializeModuleIntoKlib(
            moduleName,
            project,
            configuration,
            psi2IrContext.bindingContext,
            files,
            outputKlibPath,
            dependenciesList,
            moduleFragment,
            expectDescriptorToSymbol,
            icData,
            nopack,
            perFile = false,
            hasErrors
        )


        return ExecutionResult.Success(
            ClassicJsKLibGeneratorResult(),
            state, emptyList()
        )
    }
}

