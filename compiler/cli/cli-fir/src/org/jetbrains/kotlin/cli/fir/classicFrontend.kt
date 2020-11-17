/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.fir

import com.intellij.openapi.Disposable
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.NoScopeRecordCliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.psi.KtFile

class ClassicFrontendState(
    internal val environment: KotlinCoreEnvironment,
    internal val analyzer: AnalyzerWithCompilerReport
)

class ClassicFrontendBuilder(
    val rootDisposable: Disposable,
) : CompilationStageBuilder<Pair<K2JVMCompilerArguments, List<KtFile>>, AnalysisResult, ClassicFrontendState> {

    var configuration: CompilerConfiguration = CompilerConfiguration()

    var messageCollector: MessageCollector = MessageCollector.NONE

    var services: Services = Services.EMPTY

    override fun build(): CompilationStage<Pair<K2JVMCompilerArguments, List<KtFile>>, AnalysisResult, ClassicFrontendState> {
        configuration.apply {
            put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
        }
        return ClassicFrontend(rootDisposable, messageCollector, configuration)
    }

    operator fun invoke(body: ClassicFrontendBuilder.() -> Unit): ClassicFrontendBuilder {
        this.body()
        return this
    }
}

class ClassicFrontend internal constructor(
    val rootDisposable: Disposable,
    val messageCollector: MessageCollector,
    val configuration: CompilerConfiguration
) : CompilationStage<Pair<K2JVMCompilerArguments, List<KtFile>>, AnalysisResult, ClassicFrontendState> {

    fun newState(): ClassicFrontendState {
        val environment =
            KotlinCoreEnvironment.createForProduction(
                rootDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES
            )
        val analyzerWithCompilerReport = AnalyzerWithCompilerReport(messageCollector, configuration.languageVersionSettings)
        return  ClassicFrontendState(environment, analyzerWithCompilerReport)
    }

    override fun execute(input: Pair<K2JVMCompilerArguments, List<KtFile>>): ExecutionResult<AnalysisResult, ClassicFrontendState> =
        execute(input, newState())

    override fun execute(
        input: Pair<K2JVMCompilerArguments, List<KtFile>>,
        state: ClassicFrontendState
    ): ExecutionResult<AnalysisResult, ClassicFrontendState> {
        val sourceFiles = input.second
        state.analyzer.analyzeAndReport(sourceFiles) {
            val project = state.environment.project
            TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                project,
                sourceFiles,
                NoScopeRecordCliBindingTrace(),
                state.environment.configuration,
                state.environment::createPackagePartProvider
            )
        }
        return ExecutionResult.Success(state.analyzer.analysisResult, state, emptyList())
    }
}

