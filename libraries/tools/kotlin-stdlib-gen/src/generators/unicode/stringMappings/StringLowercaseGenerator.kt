/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.stringMappings

import generators.unicode.*
import generators.unicode.PropertyLine
import generators.unicode.SpecialCasingLine
import generators.unicode.UnicodeDataLine
import generators.unicode.ranges.RangesWritingStrategy
import generators.unicode.ranges.builders.RangesBuilder
import java.io.File
import java.io.FileWriter

internal class StringLowercaseGenerator(
    private val outputFile: File,
    private val strategy: RangesWritingStrategy,
    unicodeDataLines: List<UnicodeDataLine>,
) : StringCasingGenerator(unicodeDataLines) {

    private val casedRanges = mutableListOf<IntRange>()
    private val caseIgnorableRanges = mutableListOf<IntRange>()

    init {
        val casedRangesBuilder = CasedRangesBuilder()
        val caseIgnorableRangesBuilder = CaseIgnorableRangesBuilder()
        unicodeDataLines.forEach { line ->
            if (line.char.length > 4) {
                casedRangesBuilder.append(line.char, line.name, line.categoryCode)
                caseIgnorableRangesBuilder.append(line.char, line.name, line.categoryCode)
            }
        }
        casedRangesBuilder.build().let { (start, end, _) ->
            start.indices.forEach { casedRanges.add(start[it]..end[it]) }
        }
        caseIgnorableRangesBuilder.build().let { (start, end, _) ->
            start.indices.forEach { caseIgnorableRanges.add(start[it]..end[it]) }
        }
    }

    override fun SpecialCasingLine.mapping(): List<String> = lowercaseMapping

    override fun UnicodeDataLine.mapping(): String = lowercaseMapping

    fun appendPropListLine(line: PropertyLine) {
        when (line.property) {
            "Other_Lowercase",
            "Other_Uppercase" -> casedRanges.add(line.rangeStart.hexToInt()..line.rangeEnd.hexToInt())
        }
    }

    fun appendWordBreakPropertyLine(line: PropertyLine) {
        when (line.property) {
            "MidLetter",
            "MidNumLet",
            "Single_Quote" -> caseIgnorableRanges.add(line.rangeStart.hexToInt()..line.rangeEnd.hexToInt())
        }
    }

    fun generate() {
        check(contextDependentMappings.size == 1 || contextDependentMappings[0].conditionList == listOf("Final_Sigma")) {
            "The locale-agnostic conditional mappings $contextDependentMappings are not handled."
        }

        casedRanges.sortBy { it.first }
        caseIgnorableRanges.sortBy { it.first }

        val specialCasingChar = contextDependentMappings[0].char.hexToInt().toHexCharLiteral()
        val specialCasingResult = contextDependentMappings[0].lowercaseMapping.hexCharsToStringLiteral()

        FileWriter(outputFile).use { writer ->
            writer.writeHeader(outputFile, "kotlin.text")
            writer.appendLine()
            writer.writeIntArray("casedStart", casedRanges.map { it.first }, strategy)
            writer.writeIntArray("casedEnd", casedRanges.map { it.last }, strategy)
            writer.appendLine()
            writer.appendLine(isCased())
            writer.appendLine()
            writer.writeIntArray("caseIgnorableStart", caseIgnorableRanges.map { it.first }, strategy)
            writer.writeIntArray("caseIgnorableEnd", caseIgnorableRanges.map { it.last }, strategy)
            writer.appendLine()
            writer.appendLine(isCaseIgnorable())
            writer.appendLine()
            writer.appendLine(codePointBefore())
            writer.appendLine()
            writer.appendLine(isFinalSigmaAt())
            writer.appendLine()
            writer.appendLine(lowercaseImpl(specialCasingChar, specialCasingResult))
        }
    }

    private fun isCased(): String = """
        // Lu + Ll + Lt + Other_Lowercase + Other_Uppercase (PropList.txt of Unicode Character Database files)
        // Declared internal for testing
        internal fun Int.isCased(): Boolean {
            if (this <= Char.MAX_VALUE.toInt()) {
                when (toChar().getCategoryValue()) {
                    CharCategory.UPPERCASE_LETTER.value,
                    CharCategory.LOWERCASE_LETTER.value,
                    CharCategory.TITLECASE_LETTER.value -> return true
                }
            }
            val index = binarySearchRange(casedStart, this)
            val end = casedEnd[index]
            return this <= end
        }
    """.trimIndent()

    private fun isCaseIgnorable(): String = """
        // Mn + Me + Cf + Lm + Sk + Word_Break=MidLetter + Word_Break=MidNumLet + Word_Break=Single_Quote (WordBreakProperty.txt of Unicode Character Database files)
        // Declared internal for testing
        internal fun Int.isCaseIgnorable(): Boolean {
            if (this <= Char.MAX_VALUE.toInt()) {
                when (toChar().getCategoryValue()) {
                    CharCategory.NON_SPACING_MARK.value,
                    CharCategory.ENCLOSING_MARK.value,
                    CharCategory.FORMAT.value,
                    CharCategory.MODIFIER_LETTER.value,
                    CharCategory.MODIFIER_SYMBOL.value -> return true
                }
            }
            val index = binarySearchRange(caseIgnorableStart, this)
            val end = caseIgnorableEnd[index]
            return this <= end
        }
    """.trimIndent()

    private fun codePointBefore(): String = """
        private fun String.codePointBefore(index: Int): Int {
            val low = this[index]
            if (low.isLowSurrogate() && index - 1 >= 0) {
                val high = this[index - 1]
                if (high.isHighSurrogate()) {
                    return Char.toCodePoint(high, low)
                }
            }
            return low.toInt()
        }
    """.trimIndent()

    private fun isFinalSigmaAt(): String = """
        private fun String.isFinalSigmaAt(index: Int): Boolean {
            if (this[index] == '\u03A3' && index > 0) {
                var i = index - 1
                var codePoint: Int = codePointBefore(i)
                while (i >= 0 && codePoint.isCaseIgnorable()) {
                    i -= codePoint.charCount()
                    codePoint = codePointBefore(i)
                }
                if (i >= 0 && codePoint.isCased()) {
                    var j = index + 1
                    codePoint = codePointAt(j)
                    while (j < length && codePoint.isCaseIgnorable()) {
                        j += codePoint.charCount()
                    }
                    if (j < length && !codePoint.isCased()) {
                        return true
                    }
                }
            }
            return false
        }
    """.trimIndent()

    private fun lowercaseImpl(specialCasingChar: String, specialCasingResult: String): String = """
        internal fun String.lowercaseImpl(): String {
            var unchangedIndex = 0
            while (unchangedIndex < this.length) {
                val codePoint = codePointAt(unchangedIndex)
                if (codePoint.lowercaseCodePoint() != codePoint) { // $specialCasingChar has a lowercase mapping in UnicodeData.txt, no need to check it separately
                    break
                }
                unchangedIndex += codePoint.charCount()
            }
            if (unchangedIndex == this.length) {
                return this
            }

            val sb = StringBuilder(this.length)
            sb.appendRange(this, 0, unchangedIndex)

            var index = unchangedIndex

            while (index < this.length) {
                if (this[index] == $specialCasingChar) {
                    sb.append($specialCasingResult)
                    index++
                    continue
                }
                if (isFinalSigmaAt(index)) {
                    sb.append('\u03C2')
                    index++
                    continue
                }
                val codePoint = codePointAt(index)
                val lowercaseCodePoint = codePoint.lowercaseCodePoint()
                sb.appendCodePoint(lowercaseCodePoint)
                index += codePoint.charCount()
            }

            return sb.toString()
        }
    """.trimIndent()
}

private class CasedRangesBuilder : RangesBuilder() {
    private val id = "Cased"

    override fun categoryId(categoryCode: String): String = when (categoryCode) {
        CharCategory.UPPERCASE_LETTER.code,
        CharCategory.LOWERCASE_LETTER.code,
        CharCategory.TITLECASE_LETTER.code -> id
        else -> "Else"
    }

    override fun shouldSkip(categoryId: String): Boolean {
        return categoryId != id
    }
}

private class CaseIgnorableRangesBuilder : RangesBuilder() {
    private val id = "CaseIgnorable"

    override fun categoryId(categoryCode: String): String = when (categoryCode) {
        CharCategory.NON_SPACING_MARK.code,
        CharCategory.ENCLOSING_MARK.code,
        CharCategory.FORMAT.code,
        CharCategory.MODIFIER_LETTER.code,
        CharCategory.MODIFIER_SYMBOL.code -> id
        else -> "Else"
    }

    override fun shouldSkip(categoryId: String): Boolean {
        return categoryId != id
    }
}