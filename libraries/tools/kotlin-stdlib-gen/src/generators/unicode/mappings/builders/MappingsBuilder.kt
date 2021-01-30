/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.mappings.builders

import generators.unicode.UnicodeDataLine
import generators.unicode.mappings.patterns.EqualDistanceMappingPattern
import generators.unicode.mappings.patterns.MappingPattern

internal abstract class MappingsBuilder {
    private val patterns = mutableListOf<MappingPattern>()

    fun append(line: UnicodeDataLine) {
        val charCode = line.char.toInt(radix = 16)
        val mapping = mapping(charCode, line) ?: return

        if (patterns.isEmpty()) {
            patterns.add(createMapping(charCode, line.categoryCode, mapping))
            return
        }

        val lastPattern = patterns.last()

        if (!lastPattern.append(charCode, line.categoryCode, mapping)) {
            val newLastPattern = evolveLastPattern(lastPattern, charCode, line.categoryCode, mapping)
            if (newLastPattern != null) {
                patterns[patterns.lastIndex] = newLastPattern
            } else {
                patterns.add(createMapping(charCode, line.categoryCode, mapping))
            }
        }
    }

    fun build(): List<MappingPattern> {
//        println(patterns.joinToString(separator = "\n"))
//        println("${this.javaClass} # ${patterns.size}")
        return patterns
    }

    abstract fun mapping(charCode: Int, line: UnicodeDataLine): Int?

    open fun evolveLastPattern(lastPattern: MappingPattern, charCode: Int, categoryCode: String, mapping: Int): MappingPattern? {
        return null
    }

    private fun createMapping(charCode: Int, categoryCode: String, mapping: Int): MappingPattern {
        return EqualDistanceMappingPattern.from(charCode, categoryCode, mapping)
    }
}