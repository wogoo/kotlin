/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.specialMappings.writers

import generators.unicode.ranges.RangesWritingStrategy
import generators.unicode.toHexCharLiteral
import generators.unicode.hexCharsToStringLiteral
import java.io.FileWriter

internal class LowercaseSpecialMappingsWriter(private val strategy: RangesWritingStrategy) : SpecialMappingsWriter {
    override fun write(mappings: Map<Int, List<String>>, writer: FileWriter) {
        check(mappings.size == 1) { "Number of multi-char lowercase mappings has changed." }

        val (key, value) = mappings.entries.single()
        val char = key.toHexCharLiteral()
        val result = value.hexCharsToStringLiteral()

        writer.appendLine(lowercaseImpl(char, result))
    }

    private fun lowercaseImpl(specialCasingChar: String, specialCasingResult: String): String = """
        internal fun Char.lowercaseImpl(): String {
            if (this == $specialCasingChar) {
                return $specialCasingResult
            }
            return lowercaseCharImpl().toString()
        }
    """.trimIndent()
}