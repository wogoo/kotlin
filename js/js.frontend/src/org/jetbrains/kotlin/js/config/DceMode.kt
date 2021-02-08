/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.config

enum class DceMode {
    REMOVAL_DECLARATION,
    LOGGING,
    THROWING_EXCEPTION;

    companion object {
        val DEFAULT = REMOVAL_DECLARATION
    }
}

fun DceMode.dceModeToArgumentOfUnreachableMethod(): Int {
    return when (this) {
        DceMode.LOGGING -> 0
        DceMode.THROWING_EXCEPTION -> 1
        else -> error("Only logging and throwing exception allowed for unreachable method")
    }
}
