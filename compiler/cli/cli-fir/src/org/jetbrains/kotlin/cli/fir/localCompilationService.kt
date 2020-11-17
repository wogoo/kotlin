/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.fir

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlin.reflect.KClass

class LocalCompilationSession(val rootDisposable: Disposable) : CompilationSession {

    private val byClass = mutableMapOf(ClassicCliJvmCompiler::class to { ClassicCliJvmCompilerBuilder(rootDisposable) })

    override fun <T : CompilationStageBuilder<*, *, *>> createStage(impl: KClass<out T>): CompilationStageBuilder<*, *, *> =
        (byClass[impl] ?: throw RuntimeException("Unknown compilation stage: $impl")).invoke()

    override fun <T : Any, R : Any> createStage(from: KClass<T>, to: KClass<R>): CompilationStageBuilder<T, R, *> {
        TODO("Not yet implemented")
    }

    override fun close() {
        rootDisposable.dispose()
    }
}

class LocalCompilationServiceBuilder(var rootDisposable: Disposable = Disposer.newDisposable()) : CompilationServiceBuilder {
    override fun build(): CompilationService = LocalCompilationService(rootDisposable)
}

class LocalCompilationService(val rootDisposable: Disposable) : CompilationService {
    override fun createSession(name: String): CompilationSession = LocalCompilationSession(rootDisposable)
}