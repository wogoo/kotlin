/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.mappings.special.writers

import generators.unicode.ranges.RangesWritingStrategy
import generators.unicode.ranges.writers.hexCharLiteral
import generators.unicode.ranges.writers.hexCharsToStringLiteral
import java.io.FileWriter

internal class LowercaseSpecialMappingsWriter(private val strategy: RangesWritingStrategy) : SpecialMappingsWriter {
    override fun write(mappings: Map<Int, List<String>>, writer: FileWriter) {

        check(mappings.size == 1) { "Number of multi-char lowercase mappings has changed." }

        val (key, value) = mappings.entries.single()

        writer.appendLine(
            """
            internal fun Char.lowercaseImpl(): String {
                if (this == ${key.hexCharLiteral()}) {
                    return ${value.hexCharsToStringLiteral()}
                }
                return lowercaseCharImpl().toString()
            }
            """.trimIndent()
        )
    }
}