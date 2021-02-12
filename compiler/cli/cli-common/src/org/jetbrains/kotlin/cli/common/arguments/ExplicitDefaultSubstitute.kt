/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.common.arguments

import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation


sealed class ExplicitDefaultSubstitute {
    abstract val substitution: List<String>
}

object JvmTargetDefaultSubstitute : ExplicitDefaultSubstitute() {
    private val property: KProperty1<K2JVMCompilerArguments, String?> = K2JVMCompilerArguments::jvmTarget
    private const val default: String = "1.6"
    override val substitution: List<String>
        get() = listOf(property.findAnnotation<Argument>()!!.value, default)
}
