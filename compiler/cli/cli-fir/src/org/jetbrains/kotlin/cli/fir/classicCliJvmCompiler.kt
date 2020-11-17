/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.fir

import com.intellij.openapi.Disposable
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.File
import java.io.PrintStream

class ClassicJvmCompilerState(internal val compiler: K2JVMCompiler)

class ClassicCliJvmCompilerBuilder(
    val rootDisposable: Disposable,

) : CompilationStageBuilder<K2JVMCompilerArguments, List<File>, ClassicJvmCompilerState> {

    var compilerArguments: K2JVMCompilerArguments = K2JVMCompilerArguments()

    var messageCollector: MessageCollector = MessageCollector.NONE

    var services: Services = Services.EMPTY

    override fun build(): CompilationStage<K2JVMCompilerArguments, List<File>, ClassicJvmCompilerState> =
        ClassicCliJvmCompiler(compilerArguments, messageCollector, services)

    operator fun invoke(body: ClassicCliJvmCompilerBuilder.() -> Unit): ClassicCliJvmCompilerBuilder {
        this.body()
        return this
    }
}

class ClassicCliJvmCompiler internal constructor(
    val compilerArguments: K2JVMCompilerArguments,
    val messageCollector: MessageCollector,
    val services: Services,
): CompilationStage<K2JVMCompilerArguments, List<File>, ClassicJvmCompilerState> {

    override fun execute(input: K2JVMCompilerArguments): ExecutionResult<List<File>, ClassicJvmCompilerState> =
        execute(input, ClassicJvmCompilerState(K2JVMCompiler()))

    override fun execute(
        input: K2JVMCompilerArguments,
        state: ClassicJvmCompilerState
    ): ExecutionResult<List<File>, ClassicJvmCompilerState> {
        try {
            val res = state.compiler.exec(messageCollector, services, compilerArguments)
            return if (res == ExitCode.OK) ExecutionResult.Success(emptyList(), state, emptyList())
            else ExecutionResult.Failure(res, state, emptyList())
        } catch (e: Throwable) {
            return ExecutionResult.Failure(ExitCode.INTERNAL_ERROR, state, listOf(ExecutionExceptionWrapper(e)))
        }
    }
}

private fun example(args: List<String>, outStream: PrintStream) {

    val service = LocalCompilationServiceBuilder().build()

    val session = service.createSession("")

    val arguments = K2JVMCompilerArguments()
    parseCommandLineArguments(args, arguments)
    val collector = PrintingMessageCollector(outStream, MessageRenderer.WITHOUT_PATHS, arguments.verbose)

    val compilerBuilder = session.createStage(ClassicCliJvmCompilerBuilder::class) as ClassicCliJvmCompilerBuilder
    val compiler = compilerBuilder {
        compilerArguments = arguments
        messageCollector = collector
    }.build()

    compiler.execute(arguments)
}
