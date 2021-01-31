/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.specialMappings.writers

import generators.unicode.ranges.RangesWritingStrategy
import generators.unicode.ranges.writers.writeMappings
import java.io.FileWriter

internal class TitlecaseSpecialMappingsWriter(private val strategy: RangesWritingStrategy) : SpecialMappingsWriter {
    override fun write(mappings: Map<Int, List<String>>, writer: FileWriter) {
        strategy.beforeWritingRanges(writer)
        writer.writeMappings("mappings", mappings, strategy)
        strategy.afterWritingRanges(writer)
        writer.appendLine()
        writer.appendLine(titlecaseImpl())
    }

    private fun titlecaseImpl(): String = """
        internal fun Char.titlecaseImpl(): String {
            return mappings[this] ?: titlecaseCharImpl().toString()
        }
    """.trimIndent()
}
