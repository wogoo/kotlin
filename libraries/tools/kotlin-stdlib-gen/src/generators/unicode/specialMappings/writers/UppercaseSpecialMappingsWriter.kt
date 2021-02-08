/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.specialMappings.writers

import generators.unicode.ranges.RangesWritingStrategy
import generators.unicode.writeMappings
import java.io.FileWriter

internal class UppercaseSpecialMappingsWriter(private val strategy: RangesWritingStrategy) : SpecialMappingsWriter {
    override fun write(mappings: Map<Int, List<String>>, writer: FileWriter) {
        strategy.beforeWritingRanges(writer)
        writer.writeMappings(mappings, strategy)
        strategy.afterWritingRanges(writer)
        writer.appendLine()
        writer.appendLine(uppercaseSpecialCasing())
        writer.appendLine()
        writer.appendLine(uppercaseImpl())
    }

    private fun uppercaseSpecialCasing(): String = """
        internal fun Char.uppercaseSpecialCasing(): String? {
            val code = this.toInt()
            val index = binarySearchRange(keys, code)
            if (index >= 0 && keys[index] == code) {
                return values[index]
            }
            return null
        }
    """.trimIndent()

    private fun uppercaseImpl(): String = """
        internal fun Char.uppercaseImpl(): String {
            return uppercaseSpecialCasing() ?: uppercaseCharImpl().toString()
        }
    """.trimIndent()
}
