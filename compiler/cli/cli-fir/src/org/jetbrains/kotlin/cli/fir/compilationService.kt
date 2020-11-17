/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.fir

import kotlin.reflect.KClass

interface CompilationStage<T, R, S> {
    fun execute(input: T): ExecutionResult<R, S>
    fun execute(input: T, state: S): ExecutionResult<R, S>
}

interface CompilationStageBuilder<T, R, S> {
    fun build(): CompilationStage<T, R, S>
}

interface CompilationSession {
    fun <T: CompilationStageBuilder<*, *, *>> createStage(impl: KClass<out T>): CompilationStageBuilder<*, *, *>
    fun <T: Any, R: Any> createStage(from: KClass<T>, to: KClass<R>): CompilationStageBuilder<T, R, *>
    fun close()
}

interface CompilationService {
    fun createSession(name: String): CompilationSession
}

interface CompilationServiceBuilder {
    fun build(): CompilationService
}
