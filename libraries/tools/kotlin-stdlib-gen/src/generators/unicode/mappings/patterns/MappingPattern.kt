/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.mappings.patterns

internal interface MappingPattern {
    val start: Int
    val end: Int
    val length: Int get() = end - start + 1
    fun append(charCode: Int, categoryCode: String, mapping: Int): Boolean
    fun prepend(charCode: Int, categoryCode: String, mapping: Int): Boolean
}