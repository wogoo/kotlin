/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.mappings

import generators.requireExistingDir
import generators.unicode.UnicodeDataGenerator
import generators.unicode.UnicodeDataLine
import generators.unicode.mappings.builders.*
import generators.unicode.mappings.writers.*
import generators.unicode.ranges.RangesWritingStrategy
import generators.unicode.writeHeader
import templates.KotlinTarget
import java.io.File
import java.io.FileWriter

internal class MappingsGenerator private constructor(
    private val outputFile: File,
    private val mappingsBuilder: MappingsBuilder,
    private val mappingsWriter: MappingsWriter,
) : UnicodeDataGenerator {

    init {
        outputFile.parentFile.requireExistingDir()
    }

    override fun appendLine(line: UnicodeDataLine) {
        mappingsBuilder.append(line)
    }

    override fun close() {
        val mappings = mappingsBuilder.build()

        FileWriter(outputFile).use { writer ->
            writer.writeHeader(outputFile, "kotlin.text")
            writer.appendLine()
            writer.appendLine("// ${mappings.size} ranges totally")

            mappingsWriter.write(mappings, writer)
        }
    }

    companion object {
        fun forUppercase(outputFile: File, target: KotlinTarget): MappingsGenerator {
            val builder = UppercaseMappingsBuilder()
            val writer = UppercaseMappingsWriter(RangesWritingStrategy.of(target, "Uppercase"))
            return MappingsGenerator(outputFile, builder, writer)
        }

        fun forLowercase(outputFile: File, target: KotlinTarget): MappingsGenerator {
            val builder = LowercaseMappingsBuilder()
            val writer = LowercaseMappingsWriter(RangesWritingStrategy.of(target, "Lowercase"))
            return MappingsGenerator(outputFile, builder, writer)
        }

        fun forTitlecase(outputFile: File): MappingsGenerator {
            val builder = TitlecaseMappingsBuilder()
            val writer = TitlecaseMappingsWriter()
            return MappingsGenerator(outputFile, builder, writer)
        }
    }
}