/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.mappings.builders

import generators.unicode.UnicodeDataLine
import generators.unicode.mappings.patterns.LuLtLlMappingPattern
import generators.unicode.mappings.patterns.MappingPattern

internal class TitlecaseMappingsBuilder : MappingsBuilder() {

    override fun mapping(charCode: Int, line: UnicodeDataLine): Int? {
        if (line.titlecaseMapping == line.uppercaseMapping) return null

        require(line.titlecaseMapping.isNotEmpty()) { "UnicodeData.txt format has changed!" }

        val title = line.titlecaseMapping.toInt(radix = 16)
        return title - charCode
    }

    override fun evolveLastPattern(lastPattern: MappingPattern, charCode: Int, categoryCode: String, mapping: Int): MappingPattern? {
        return LuLtLlMappingPattern.from(lastPattern, charCode, categoryCode, mapping)
    }
}