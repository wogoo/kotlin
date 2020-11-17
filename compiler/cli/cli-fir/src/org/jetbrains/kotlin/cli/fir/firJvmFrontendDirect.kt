/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.fir

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.MetadataSerializerFactory
import org.jetbrains.kotlin.backend.jvm.codegen.MetadataSerializer
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.common.checkKotlinPackageUsage
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.codegen.JvmBackendClassResolver
import org.jetbrains.kotlin.codegen.serialization.JvmSerializationBindings
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.diagnostics.*
import org.jetbrains.kotlin.fir.FirPsiSourceElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.FirAnalyzerFacade
import org.jetbrains.kotlin.fir.analysis.diagnostics.*
import org.jetbrains.kotlin.fir.backend.jvm.FirJvmBackendClassResolver
import org.jetbrains.kotlin.fir.backend.jvm.FirMetadataSerializer
import org.jetbrains.kotlin.fir.java.FirProjectSessionProvider
import org.jetbrains.kotlin.fir.session.FirSessionFactory
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.modules.Module
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi2ir.PsiSourceManager
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.resolve.diagnostics.SimpleDiagnostics
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformAnalyzerServices
import org.jetbrains.org.objectweb.asm.Type
import java.io.PrintStream

class FirJvmFrontendDirectState(
    internal val environment: KotlinCoreEnvironment,
)

data class FirJvmFrontendDirectInputs(
    val module: Module,
    val filesToCompile: List<KtFile>,
    val configuration: CompilerConfiguration
)

data class FrontendIrOutputs(
    val moduleFragment: IrModuleFragment,
    val symbolTable: SymbolTable, 
    val sourceManager: PsiSourceManager, 
    val jvmBackendClassResolver: JvmBackendClassResolver,
    val metadataSerializerFactory: MetadataSerializerFactory
)

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

class FirJvmFrontendDirectBuilder(
    val rootDisposable: Disposable,
) : CompilationStageBuilder<FirJvmFrontendDirectInputs, FrontendIrOutputs, FirJvmFrontendDirectState> {

    var configuration: CompilerConfiguration = CompilerConfiguration()

    var messageCollector: MessageCollector = MessageCollector.NONE

    var services: Services = Services.EMPTY

    override fun build(): CompilationStage<FirJvmFrontendDirectInputs, FrontendIrOutputs, FirJvmFrontendDirectState> {
        configuration.apply {
            put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
        }
        return FirJvmFrontendDirect(rootDisposable, messageCollector, configuration)
    }

    operator fun invoke(body: FirJvmFrontendDirectBuilder.() -> Unit): FirJvmFrontendDirectBuilder {
        this.body()
        return this
    }
}

class FirJvmFrontendDirect internal constructor(
    val rootDisposable: Disposable,
    val messageCollector: MessageCollector,
    val configuration: CompilerConfiguration
) : CompilationStage<FirJvmFrontendDirectInputs, FrontendIrOutputs, FirJvmFrontendDirectState> {

    fun newState(): FirJvmFrontendDirectState {
        val environment =
            KotlinCoreEnvironment.createForProduction(
                rootDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES
            )
        return  FirJvmFrontendDirectState(environment)
    }

    override fun execute(input: FirJvmFrontendDirectInputs): ExecutionResult<FrontendIrOutputs, FirJvmFrontendDirectState> =
        execute(input, newState())

    override fun execute(
        input: FirJvmFrontendDirectInputs,
        state: FirJvmFrontendDirectState
    ): ExecutionResult<FrontendIrOutputs, FirJvmFrontendDirectState> {
        val (module, ktFiles, moduleConfiguration) = input
        val localFileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL)
        if (!checkKotlinPackageUsage(state.environment, ktFiles)) return ExecutionResult.Failure(-1, state, emptyList())

        val scope = GlobalSearchScope.filesScope(state.environment.project, ktFiles.map { it.virtualFile })
            .uniteWith(TopDownAnalyzerFacadeForJVM.AllJavaSourcesInProjectScope(state.environment.project))
        val provider = FirProjectSessionProvider(state.environment.project)

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
            val librariesScope = ProjectScope.getLibrariesScope(state.environment.project)
            FirSessionFactory.createLibrarySession(
                dependenciesInfo, provider, librariesScope,
                state.environment.project, state.environment.createPackagePartProvider(librariesScope)
            )
        }

        val firAnalyzerFacade = FirAnalyzerFacade(session, moduleConfiguration.languageVersionSettings, ktFiles)

        firAnalyzerFacade.runResolution()
        val firDiagnostics = firAnalyzerFacade.runCheckers()
        val diagnostics = firDiagnostics.map { it.toRegularDiagnostic() }
        AnalyzerWithCompilerReport.reportDiagnostics(SimpleDiagnostics(diagnostics), messageCollector)

        if (firDiagnostics.any { it.severity == Severity.ERROR }) {
            return ExecutionResult.Failure(-1, state, emptyList())
        }

        val (moduleFragment, symbolTable, sourceManager, components) = firAnalyzerFacade.convertToIr()

        val outputs = FrontendIrOutputs(
            moduleFragment, symbolTable, sourceManager,
            FirJvmBackendClassResolver(components),
            FirMetadataSerializerBuilder(session)
        )

        return ExecutionResult.Success(outputs, state, emptyList())
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

    val frontendBuilder = session.createStage(FirJvmFrontendDirectBuilder::class) as FirJvmFrontendDirectBuilder
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

