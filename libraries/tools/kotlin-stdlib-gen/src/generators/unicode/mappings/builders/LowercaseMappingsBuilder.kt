/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.mappings.builders

import generators.unicode.UnicodeDataLine
import generators.unicode.hexToInt

internal class LowercaseMappingsBuilder : MappingsBuilder() {
    override fun mapping(charCode: Int, line: UnicodeDataLine): Int? {
        if (line.lowercaseMapping.isEmpty()) return null

        val lower = line.lowercaseMapping.hexToInt()
        check(charCode != lower) { "UnicodeData.txt format has changed!" }

        return lower - charCode
    }
}
