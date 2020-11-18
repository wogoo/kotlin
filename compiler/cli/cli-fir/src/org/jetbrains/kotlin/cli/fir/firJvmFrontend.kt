/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.fir

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.backend.jvm.jvmPhases
import org.jetbrains.kotlin.cli.common.*
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.common.messages.*
import org.jetbrains.kotlin.cli.common.modules.ModuleBuilder
import org.jetbrains.kotlin.cli.jvm.*
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.plugins.PluginCliParser
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.diagnostics.*
import org.jetbrains.kotlin.fir.FirPsiSourceElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.FirAnalyzerFacade
import org.jetbrains.kotlin.fir.analysis.diagnostics.*
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.java.FirProjectSessionProvider
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.session.FirSessionFactory
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.modules.Module
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.resolve.diagnostics.SimpleDiagnostics
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformAnalyzerServices
import org.jetbrains.kotlin.utils.KotlinPaths
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File
import java.io.PrintStream
import java.util.*

data class FirJvmFrontendInputs(
    val module: Module,
    val filesToCompile: List<KtFile>,
    val configuration: CompilerConfiguration? = null
)

data class FirFrontendOutputs(
    val session: FirSession,
    val module: Module,
    val project: Project,
    val sourceFiles: List<KtFile>,
    val configuration: CompilerConfiguration,
    val packagePartProvider: PackagePartProvider?,
    val session1: FirSession,
    val scopeSession: ScopeSession?,
    val firFiles: List<FirFile>?
)

class FirJvmFrontendBuilder : CompilationStageBuilder<FirJvmFrontendInputs, FirFrontendOutputs> {
    var configuration: CompilerConfiguration = CompilerConfiguration()

    var messageCollector: MessageCollector? = null

    var project: Project? = null

    var packagePartProvider: PackagePartProvider? = null

    override fun build(): CompilationStage<FirJvmFrontendInputs, FirFrontendOutputs> {
        return FirJvmFrontend(
            messageCollector ?: configuration.getNotNull(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY),
            project ?: error(""),
            configuration,
            packagePartProvider ?: error("")
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
        val actualConfiguration = moduleConfiguration ?: configuration
        val localFileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL)
        if (!actualConfiguration.getBoolean(CLIConfigurationKeys.ALLOW_KOTLIN_PACKAGE) &&
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

        val firAnalyzerFacade = FirAnalyzerFacade(session, actualConfiguration.languageVersionSettings, ktFiles)

        firAnalyzerFacade.runResolution()
        val firDiagnostics = firAnalyzerFacade.runCheckers()
        val diagnostics = firDiagnostics.map { it.toRegularDiagnostic() }
        AnalyzerWithCompilerReport.reportDiagnostics(SimpleDiagnostics(diagnostics), messageCollector)

        if (firDiagnostics.any { it.severity == Severity.ERROR }) {
            return ExecutionResult.Failure(-1, emptyList())
        }

        val outputs = FirFrontendOutputs(
            session,
            module,
            project,
            ktFiles,
            actualConfiguration,
            packagePartProvider,
            firAnalyzerFacade.session,
            firAnalyzerFacade.scopeSession,
            firAnalyzerFacade.firFiles
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

    val rootDisposable = Disposer.newDisposable()

    try {
        val arguments = K2JVMCompilerArguments()
        parseCommandLineArguments(args, arguments)
        val collector = GroupingMessageCollector(
            PrintingMessageCollector(outStream, MessageRenderer.WITHOUT_PATHS, arguments.verbose),
            arguments.allWarningsAsErrors
        )
        val paths = computeKotlinPaths(collector, arguments)

        var environment: KotlinCoreEnvironment? = null

        val frontendBuilder = session.createStage(FirJvmFrontendBuilder::class) as FirJvmFrontendBuilder
        val frontend = frontendBuilder {

            configureDefaultJvmFirFrontend(collector, arguments, paths)

            environment = KotlinCoreEnvironment.createForProduction(
                rootDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES
            ).also {
                project = it.project
                packagePartProvider = it.createPackagePartProvider(ProjectScope.getLibrariesScope(project!!))
            }

        }.build()

        val fir2IrBuilder = session.createStage(FirJvmFrontendToIrConverterBuilder::class) as FirJvmFrontendToIrConverterBuilder
        val fir2ir = fir2IrBuilder {
            messageCollector = collector
        }.build()

        val backendBuilder = session.createStage(IrJvmBackendBuilder::class) as IrJvmBackendBuilder
        val backend = backendBuilder {

            messageCollector = collector
        }.build()

        val destination = arguments.destination?.let { File(it) }
        val moduleName = arguments.moduleName ?: JvmProtoBufUtil.DEFAULT_MODULE_NAME
        val module = ModuleBuilder(moduleName, destination?.path ?: ".", "java-production")

        val frontendRes = frontend.execute(FirJvmFrontendInputs( module, environment!!.getSourceFiles()))
        if (frontendRes is ExecutionResult.Success) {
            val convertorRes = fir2ir.execute(frontendRes.value)

            if (convertorRes is ExecutionResult.Success) {
                val backendRes = backend.execute(convertorRes.value)
            }
        }
    } finally {
        // TODO: error handling
        rootDisposable.dispose()
        session.close()
    }
}

internal fun FirJvmFrontendBuilder.configureDefaultJvmFirFrontend(
    collector: GroupingMessageCollector,
    arguments: K2JVMCompilerArguments,
    paths: KotlinPaths?
) {
    configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, collector)
    messageCollector = collector

    val services: Services = Services.EMPTY

    configuration.setupCommonArguments(arguments)
    configuration.setupJvmSpecificArguments(arguments)

    configuration.putIfNotNull(CLIConfigurationKeys.REPEAT_COMPILE_MODULES, arguments.repeatCompileModules?.toIntOrNull())
    configuration.put(CLIConfigurationKeys.PHASE_CONFIG, createPhaseConfig(jvmPhases, arguments, collector))

    if (!configuration.configureJdkHome(arguments)) error("") //ExitCode.COMPILATION_ERROR

    configuration.put(JVMConfigurationKeys.DISABLE_STANDARD_SCRIPT_DEFINITION, arguments.disableStandardScript)

    val pluginLoadResult = loadPlugins(paths, arguments, configuration)
    if (pluginLoadResult != ExitCode.OK) error("") //return pluginLoadResult

    val moduleName = arguments.moduleName ?: JvmProtoBufUtil.DEFAULT_MODULE_NAME
    configuration.put(CommonConfigurationKeys.MODULE_NAME, moduleName)

    configuration.configureExplicitContentRoots(arguments)
    configuration.configureStandardLibs(paths, arguments)
    configuration.configureAdvancedJvmOptions(arguments)
    configuration.configureKlibPaths(arguments)
}

private fun <A : CommonCompilerArguments> loadPlugins(paths: KotlinPaths?, arguments: A, configuration: CompilerConfiguration): ExitCode {
    var pluginClasspaths: Iterable<String> = arguments.pluginClasspaths?.asIterable() ?: emptyList()
    val pluginOptions = arguments.pluginOptions?.toMutableList() ?: ArrayList()

    if (!arguments.disableDefaultScriptingPlugin) {
        val explicitOrLoadedScriptingPlugin =
            pluginClasspaths.any { File(it).name.startsWith(PathUtil.KOTLIN_SCRIPTING_COMPILER_PLUGIN_NAME) } ||
                    tryLoadScriptingPluginFromCurrentClassLoader(configuration)
        if (!explicitOrLoadedScriptingPlugin) {
            val kotlinPaths = paths ?: PathUtil.kotlinPathsForCompiler
            val libPath = kotlinPaths.libPath.takeIf { it.exists() && it.isDirectory } ?: File(".")
            val (jars, missingJars) =
                PathUtil.KOTLIN_SCRIPTING_PLUGIN_CLASSPATH_JARS.map { File(libPath, it) }.partition { it.exists() }
            if (missingJars.isEmpty()) {
                pluginClasspaths = jars.map { it.canonicalPath } + pluginClasspaths
            } else {
                val messageCollector = configuration.getNotNull(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY)
                messageCollector.report(
                    CompilerMessageSeverity.LOGGING,
                    "Scripting plugin will not be loaded: not all required jars are present in the classpath (missing files: $missingJars)"
                )
            }
        }
        // TODO: restore
//        pluginOptions.addPlatformOptions(arguments)
    } else {
        pluginOptions.add("plugin:kotlin.scripting:disable=true")
    }
    return PluginCliParser.loadPluginsSafe(pluginClasspaths, pluginOptions, configuration)
}

private fun tryLoadScriptingPluginFromCurrentClassLoader(configuration: CompilerConfiguration): Boolean = try {
    val pluginRegistrarClass = PluginCliParser::class.java.classLoader.loadClass(
        "org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCompilerConfigurationComponentRegistrar"
    )
    val pluginRegistrar = pluginRegistrarClass.newInstance() as? ComponentRegistrar
    if (pluginRegistrar != null) {
        configuration.add(ComponentRegistrar.PLUGIN_COMPONENT_REGISTRARS, pluginRegistrar)
        true
    } else false
} catch (_: Throwable) {
    // TODO: add finer error processing and logging
    false
}

