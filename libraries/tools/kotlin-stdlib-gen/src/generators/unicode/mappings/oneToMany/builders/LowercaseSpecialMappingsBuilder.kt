/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.mappings.oneToMany.builders

import generators.unicode.SpecialCasingLine
import generators.unicode.UnicodeDataLine

internal class LowercaseSpecialMappingsBuilder(unicodeDataLines: List<UnicodeDataLine>) : SpecialMappingsBuilder(unicodeDataLines) {
    override fun SpecialCasingLine.mapping(): List<String> = lowercaseMapping
    override fun UnicodeDataLine.mapping(): String = lowercaseMapping
}