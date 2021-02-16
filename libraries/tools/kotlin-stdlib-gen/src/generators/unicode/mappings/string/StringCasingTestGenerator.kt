/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.mappings.string

import generators.unicode.PropertyLine
import generators.unicode.hexToInt
import generators.unicode.toHexIntLiteral
import generators.unicode.writeHeader
import java.io.File
import java.io.FileWriter

internal class StringCasingTestGenerator(private val outputFile: File) {
    private val casedRanges = mutableListOf<PropertyLine>()
    private val caseIgnorableRanges = mutableListOf<PropertyLine>()

    init {
        outputFile.parentFile.mkdirs()
    }

    fun appendDerivedCorePropertiesLine(line: PropertyLine) {
        when (line.property) {
            "Cased" -> casedRanges.add(line)
            "Case_Ignorable" -> caseIgnorableRanges.add(line)
        }
    }

    fun generate() {
        generateCasedRanges()
        generateCaseIgnorableRanges()
        generateTests()
    }

    private fun generateTests() {
        FileWriter(outputFile).use { writer ->
            writer.writeHeader(outputFile, "test.text.unicodeData")
            writer.appendLine()
            writer.appendLine(
                """
import kotlin.test.*
import kotlin.text.isCased
import kotlin.text.isCaseIgnorable

class StringCasingTest {
    @Test
    fun isCased() {
        var lastCased = -1
        for (range in casedRanges) {
            for (codePoint in lastCased + 1 until range.first) {
                assertFalse(codePoint.isCased())
            }
            for (codePoint in range.first..range.last) {
                assertTrue(codePoint.isCased())
            }
            lastCased = range.last
        }
        for (codePoint in lastCased + 1..0x10FFFF) {
            assertFalse(codePoint.isCased())
        }
    }
    
    @Test
    fun isCaseIgnorable() {
        var lastCaseIgnorable = -1
        for (range in caseIgnorableRanges) {
            for (codePoint in lastCaseIgnorable + 1 until range.first) {
                assertFalse(codePoint.isCaseIgnorable())
            }
            for (codePoint in range.first..range.last) {
                assertTrue(codePoint.isCaseIgnorable())
            }
            lastCaseIgnorable = range.last
        }
        for (codePoint in lastCaseIgnorable + 1..0x10FFFF) {
            assertFalse(codePoint.isCaseIgnorable())
        }
    }
}
            """.trimIndent()
            )
        }
    }

    private fun generateCasedRanges() {
        val file = outputFile.resolveSibling("_CasedRanges.kt")
        generateRanges(casedRanges, file, "casedRanges")
    }

    private fun generateCaseIgnorableRanges() {
        val file = outputFile.resolveSibling("_CaseIgnorableRanges.kt")
        generateRanges(caseIgnorableRanges, file, "caseIgnorableRanges")
    }

    private fun generateRanges(ranges: List<PropertyLine>, file: File, name: String) {
        FileWriter(file).use { writer ->
            writer.writeHeader(file, "test.text.unicodeData")
            writer.appendLine()
            writer.appendLine("internal val $name = arrayOf<IntRange>(")
            ranges.forEach {
                val start = it.rangeStart.hexToInt().toHexIntLiteral()
                val end = it.rangeEnd.hexToInt().toHexIntLiteral()
                writer.appendLine("    $start..$end,")
            }
            writer.appendLine(")")
        }
    }
}