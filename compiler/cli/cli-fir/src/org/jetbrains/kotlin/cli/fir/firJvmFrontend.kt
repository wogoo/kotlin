/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.fir

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.common.checkKotlinPackageUsage
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.diagnostics.*
import org.jetbrains.kotlin.fir.FirPsiSourceElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.FirAnalyzerFacade
import org.jetbrains.kotlin.fir.analysis.diagnostics.*
import org.jetbrains.kotlin.fir.java.FirProjectSessionProvider
import org.jetbrains.kotlin.fir.session.FirSessionFactory
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider
import org.jetbrains.kotlin.modules.Module
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.resolve.diagnostics.SimpleDiagnostics
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformAnalyzerServices
import java.io.PrintStream

data class FirJvmFrontendInputs(
    val module: Module,
    val filesToCompile: List<KtFile>,
    val configuration: CompilerConfiguration
)

data class FirFrontendOutputs(
    val firAnalyzerFacade: FirAnalyzerFacade,
    val session: FirSession,
    val module: Module,
    val project: Project,
    val ktFiles: List<KtFile>,
    val configuration: CompilerConfiguration,
    val packagePartProvider: PackagePartProvider?
)

class FirJvmFrontendBuilder(
    val rootDisposable: Disposable,
) : CompilationStageBuilder<FirJvmFrontendInputs, FirFrontendOutputs> {

    var environment: KotlinCoreEnvironment? = null

    val configuration: CompilerConfiguration? = null

    val messageCollector: MessageCollector? = null

    override fun build(): CompilationStage<FirJvmFrontendInputs, FirFrontendOutputs> {
        val actualConfiguration = configuration ?: environment?.configuration ?: error("")
        val project = environment?.project ?: error("")
        return FirJvmFrontend(
            messageCollector ?: actualConfiguration.getNotNull(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY),
            project,
            actualConfiguration,
            environment?.createPackagePartProvider(ProjectScope.getLibrariesScope(project)) ?: error("")
        )
    }

    operator fun invoke(body: FirJvmFrontendBuilder.() -> Unit): FirJvmFrontendBuilder {
        this.body()
        return this
    }
}

class FirJvmFrontend internal constructor(
    val messageCollector: MessageCollector,
    val project: Project,
    val configuration: CompilerConfiguration,
    val packagePartProvider: PackagePartProvider,
) : CompilationStage<FirJvmFrontendInputs, FirFrontendOutputs> {

    override fun execute(
        input: FirJvmFrontendInputs
    ): ExecutionResult<FirFrontendOutputs> {
        val (module, ktFiles, moduleConfiguration) = input
        val localFileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL)
        if (!configuration.getBoolean(CLIConfigurationKeys.ALLOW_KOTLIN_PACKAGE) &&
            !checkKotlinPackageUsage(ktFiles, messageCollector)
        ) return ExecutionResult.Failure(-1, emptyList())

        val scope = GlobalSearchScope.filesScope(project, ktFiles.map { it.virtualFile })
            .uniteWith(TopDownAnalyzerFacadeForJVM.AllJavaSourcesInProjectScope(project))
        val provider = FirProjectSessionProvider(project)

        class FirJvmModuleInfo(override val name: Name) : ModuleInfo {
            constructor(moduleName: String) : this(Name.identifier(moduleName))

            val dependencies: MutableList<ModuleInfo> = mutableListOf()

            override val platform: TargetPlatform
                get() = JvmPlatforms.unspecifiedJvmPlatform

            override val analyzerServices: PlatformDependentAnalyzerServices
                get() = JvmPlatformAnalyzerServices

            override fun dependencies(): List<ModuleInfo> {
                return dependencies
            }
        }

        val moduleInfo = FirJvmModuleInfo(module.getModuleName())
        val session: FirSession = FirSessionFactory.createJavaModuleBasedSession(moduleInfo, provider, scope) {
//            if (extendedAnalysisMode) {
//                registerExtendedCommonCheckers()
//            }
        }.also {
            val dependenciesInfo = FirJvmModuleInfo(Name.special("<dependencies>"))
            moduleInfo.dependencies.add(dependenciesInfo)
            val librariesScope = ProjectScope.getLibrariesScope(project)
            FirSessionFactory.createLibrarySession(dependenciesInfo, provider, librariesScope, project, packagePartProvider)
        }

        val firAnalyzerFacade = FirAnalyzerFacade(session, moduleConfiguration.languageVersionSettings, ktFiles)

        firAnalyzerFacade.runResolution()
        val firDiagnostics = firAnalyzerFacade.runCheckers()
        val diagnostics = firDiagnostics.map { it.toRegularDiagnostic() }
        AnalyzerWithCompilerReport.reportDiagnostics(SimpleDiagnostics(diagnostics), messageCollector)

        if (firDiagnostics.any { it.severity == Severity.ERROR }) {
            return ExecutionResult.Failure(-1, emptyList())
        }

        val outputs = FirFrontendOutputs(
            firAnalyzerFacade,
            session,
            module,
            project,
            ktFiles,
            configuration,
            packagePartProvider
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

private fun example(args: List<String>, outStream: PrintStream) {

    val service = LocalCompilationServiceBuilder().build()

    val session = service.createSession("")

    val arguments = K2JVMCompilerArguments()
    parseCommandLineArguments(args, arguments)
    val collector = PrintingMessageCollector(outStream, MessageRenderer.WITHOUT_PATHS, arguments.verbose)

    val frontendBuilder = session.createStage(FirJvmFrontendBuilder::class) as FirJvmFrontendBuilder
    val frontend = frontendBuilder {
        compilerArguments = arguments
        messageCollector = collector
    }.build()

    val backendBuilder = session.createStage(IrJvmBackendBuilder::class) as IrJvmBackendBuilder
    val backend = backendBuilder {

        messageCollector = collector
    }.build()

    frontend.execute(arguments)
}

