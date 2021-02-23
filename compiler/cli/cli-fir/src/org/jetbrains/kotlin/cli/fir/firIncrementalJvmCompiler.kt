/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("unused")

package org.jetbrains.kotlin.cli.fir

//import com.intellij.openapi.util.Disposer
//import com.intellij.psi.search.ProjectScope
//import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
//import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
//import org.jetbrains.kotlin.cli.common.computeKotlinPaths
//import org.jetbrains.kotlin.cli.common.messages.GroupingMessageCollector
//import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
//import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
//import org.jetbrains.kotlin.cli.common.modules.ModuleBuilder
//import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
//import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
//import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
//import java.io.File
//import java.io.PrintStream
//
//private fun incrementalCompilerExample(args: List<String>, outStream: PrintStream) {
//
//    val service = LocalCompilationServiceBuilder().build()
//
//    val session = service.createSession("")
//
//    val rootDisposable = Disposer.newDisposable()
//
//    try {
//        val arguments = K2JVMCompilerArguments()
//        parseCommandLineArguments(args, arguments)
//        val collector = GroupingMessageCollector(
//            PrintingMessageCollector(outStream, MessageRenderer.WITHOUT_PATHS, arguments.verbose),
//            arguments.allWarningsAsErrors
//        )
//        val paths = computeKotlinPaths(collector, arguments)
//
//        var environment: KotlinCoreEnvironment? = null
//
//        val frontendBuilder = session.createStage(FirJvmFrontendBuilder::class) as FirJvmFrontendBuilder
//        val frontend = frontendBuilder {
//
//            configureDefaultJvmFirFrontend(collector, arguments, paths)
//
//            environment = KotlinCoreEnvironment.createForProduction(
//                rootDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES
//            ).also {
//                project = it.project
//                packagePartProvider = it.createPackagePartProvider(ProjectScope.getLibrariesScope(project!!))
//            }
//
//        }.build()
//
//        val fir2IrBuilder = session.createStage(FirJvmFrontendToIrConverterBuilder::class) as FirJvmFrontendToIrConverterBuilder
//        val fir2ir = fir2IrBuilder {
//            messageCollector = collector
//        }.build()
//
//        val backendBuilder = session.createStage(IrJvmBackendBuilder::class) as IrJvmBackendBuilder
//        val backend = backendBuilder {
//
//            messageCollector = collector
//        }.build()
//
//        val destination = arguments.destination?.let { File(it) }
//        val moduleName = arguments.moduleName ?: JvmProtoBufUtil.DEFAULT_MODULE_NAME
//        val module = ModuleBuilder(moduleName, destination?.path ?: ".", "java-production")
//
//
//
//    } finally {
//        // TODO: error handling
//        rootDisposable.dispose()
//        session.close()
//    }
//}