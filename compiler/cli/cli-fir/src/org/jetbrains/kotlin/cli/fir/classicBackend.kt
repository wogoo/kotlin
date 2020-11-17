/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.fir

import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.KotlinCodegenFacade
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtFile

class ClassicBackendState(
)

class ClassicBackendBuilder : CompilationStageBuilder<Pair<AnalysisResult, List<KtFile>>, GenerationState, ClassicFrontendState> {
    val configuration = CompilerConfiguration()

    override fun build(): CompilationStage<Pair<AnalysisResult, List<KtFile>>, GenerationState, ClassicFrontendState> {
        TODO("Not yet implemented")
    }

    operator fun invoke(body: ClassicBackendBuilder.() -> Unit): ClassicBackendBuilder {
        this.body()
        return this
    }
}

class ClassicBackend internal constructor(
    val configuration: CompilerConfiguration
) : CompilationStage<Pair<AnalysisResult, List<KtFile>>, GenerationState, ClassicBackendState> {

    override fun execute(input: Pair<AnalysisResult, List<KtFile>>): ExecutionResult<GenerationState, ClassicBackendState> =
        execute(input, ClassicBackendState())

    override fun execute(
        input: Pair<AnalysisResult, List<KtFile>>,
        state: ClassicBackendState
    ): ExecutionResult<GenerationState, ClassicBackendState> {
        val analysisResult = input.first
        val sourceFiles = input.second
        val res = GenerationState.Builder(
            sourceFiles.first().project,
            ClassBuilderFactories.BINARIES,
            analysisResult.moduleDescriptor,
            analysisResult.bindingContext,
            sourceFiles,
            configuration
        ).build().also(KotlinCodegenFacade::compileCorrectFiles)
        return ExecutionResult.Success(res, state, emptyList())
    }
}