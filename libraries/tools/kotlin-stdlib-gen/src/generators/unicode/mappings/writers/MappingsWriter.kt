/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.mappings.writers

import generators.unicode.mappings.patterns.MappingPattern
import java.io.FileWriter

internal interface MappingsWriter {
    fun write(mappings: List<MappingPattern>, writer: FileWriter)
}