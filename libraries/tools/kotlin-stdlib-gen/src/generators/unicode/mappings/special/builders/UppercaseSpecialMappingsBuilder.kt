/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.mappings.special.builders

import generators.unicode.SpecialCasingLine
import generators.unicode.UnicodeDataLine

internal class UppercaseSpecialMappingsBuilder(unicodeDataLines: List<UnicodeDataLine>) : SpecialMappingsBuilder(unicodeDataLines) {
    override fun SpecialCasingLine.mapping(): List<String> = uppercaseMapping
    override fun UnicodeDataLine.mapping(): String = uppercaseMapping
}
