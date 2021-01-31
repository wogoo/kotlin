/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode

import generators.unicode.mappings.MappingsGenerator
import generators.unicode.mappings.special.SpecialMappingsGenerator
import generators.unicode.ranges.CharCategoryTestGenerator
import generators.unicode.ranges.RangesGenerator
import templates.COPYRIGHT_NOTICE
import templates.KotlinTarget
import templates.readCopyrightNoticeFromProfile
import java.io.File
import java.net.URL
import kotlin.system.exitProcess


// Go to https://www.unicode.org/versions/latest/ to find out the latest public version of the Unicode Character Database files.
private const val unicodeVersion = "13.0.0"
private const val unicodeDataUrl = "https://www.unicode.org/Public/$unicodeVersion/ucd/UnicodeData.txt"
private const val specialCasingUrl = "https://www.unicode.org/Public/$unicodeVersion/ucd/SpecialCasing.txt"

/**
 * This program generates sources related to UnicodeData.txt and SpecialCasing.txt.
 * There are two ways to run the program.
 * 1. Pass the root directory of the project to generate sources for js and js-ir.
 *  _CharCategoryTest.kt and supporting files are also generated to test the generated sources.
 *  The generated test is meant to be run after updating Unicode version and should not be merged to master.
 * 2. Pass the name of the target to generate sources for, and the directory to generate sources in.
 *  No tests are generated.
 */
fun main(args: Array<String>) {

    val unicodeDataLines = URL(unicodeDataUrl).openStream().reader().readLines()
    val bmpUnicodeDataLines = unicodeDataLines.mapNotNull { line ->
        UnicodeDataLine(line.split(";")).takeIf { it.char.length <= 4 } // Basic Multilingual Plane (BMP)
    }
    val specialCasingLines = URL(specialCasingUrl).openStream().reader().readLines()
    val nonCommentSpecialCasingLines = specialCasingLines.mapNotNull { line ->
        if (line.isEmpty() || line.startsWith("#")) null else SpecialCasingLine(line.split("; "))
    }

    val unicodeDataGenerators = mutableListOf<UnicodeDataGenerator>()

    fun addRangesGenerators(generatedDir: File, target: KotlinTarget) {
        val category = RangesGenerator.forCharCategory(generatedDir.resolve("_CharCategories.kt"), target)
        val digit = RangesGenerator.forDigit(generatedDir.resolve("_DigitChars.kt"), target)
        val letter = RangesGenerator.forLetter(generatedDir.resolve("_LetterChars.kt"), target)
        val whitespace = RangesGenerator.forWhitespace(generatedDir.resolve("_WhitespaceChars.kt"))
        unicodeDataGenerators.add(category)
        unicodeDataGenerators.add(digit)
        unicodeDataGenerators.add(letter)
        unicodeDataGenerators.add(whitespace)
    }

    fun addMappingsGenerators(generatedDir: File, target: KotlinTarget) {
        val uppercase = MappingsGenerator.forUppercase(generatedDir.resolve("_UppercaseMappings.kt"), target)
        val lowercase = MappingsGenerator.forLowercase(generatedDir.resolve("_LowercaseMappings.kt"), target)
        val titlecase = MappingsGenerator.forTitlecase(generatedDir.resolve("_TitlecaseMappings.kt"))
        unicodeDataGenerators.add(uppercase)
        unicodeDataGenerators.add(lowercase)
        unicodeDataGenerators.add(titlecase)
    }

    val specialCasingGenerators = mutableListOf<SpecialCasingGenerator>()

    fun addSpecialMappingsGenerators(generatedDir: File, target: KotlinTarget) {
        val uppercase = SpecialMappingsGenerator.forUppercase(generatedDir.resolve("_UppercaseSpecialMappings.kt"), target, bmpUnicodeDataLines)
        val lowercase = SpecialMappingsGenerator.forLowercase(generatedDir.resolve("_LowercaseSpecialMappings.kt"), target, bmpUnicodeDataLines)
        val titlecase = SpecialMappingsGenerator.forTitlecase(generatedDir.resolve("_TitlecaseSpecialMappings.kt"), target, bmpUnicodeDataLines)
        specialCasingGenerators.add(uppercase)
        specialCasingGenerators.add(lowercase)
        specialCasingGenerators.add(titlecase)
    }

    when (args.size) {
        1 -> {
            val baseDir = File(args.first())

            val categoryTestFile = baseDir.resolve("libraries/stdlib/js/test/text/unicodeData/_CharCategoryTest.kt")
            val categoryTestGenerator = CharCategoryTestGenerator(categoryTestFile)
            unicodeDataGenerators.add(categoryTestGenerator)

            val jsGeneratedDir = baseDir.resolve("libraries/stdlib/js/src/generated/")
            addRangesGenerators(jsGeneratedDir, KotlinTarget.JS)
            addMappingsGenerators(jsGeneratedDir, KotlinTarget.Native)
            addSpecialMappingsGenerators(jsGeneratedDir, KotlinTarget.Native)

            val jsIrGeneratedDir = baseDir.resolve("libraries/stdlib/js-ir/src/generated/")
            addRangesGenerators(jsIrGeneratedDir, KotlinTarget.JS_IR)

            // For debugging. To see the file content
            fun writeContent(lines: List<String>, fileName: String) {
                val file = baseDir.resolve("libraries/tools/kotlin-stdlib-gen/src/generators/unicode/$fileName")
                file.writeText(lines.joinToString(separator = "\n"))
            }
            writeContent(unicodeDataLines, "UnicodeData.txt")
            writeContent(specialCasingLines, "SpecialCasing.txt")
        }
        2 -> {
            val (targetName, targetDir) = args

            val target = KotlinTarget.values.singleOrNull { it.name.equals(targetName, ignoreCase = true) }
                ?: error("Invalid target: $targetName")

            addRangesGenerators(File(targetDir), target)
        }
        else -> {
            println(
                """Parameters:
    <kotlin-base-dir> - generates UnicodeData.txt sources for js and js-ir targets using paths derived from specified base path
    <UnicodeData.txt-path> <target> <target-dir> - generates UnicodeData.txt sources for the specified target in the specified target directory
"""
            )
            exitProcess(1)
        }
    }

    COPYRIGHT_NOTICE =
        readCopyrightNoticeFromProfile { Thread.currentThread().contextClassLoader.getResourceAsStream("apache.xml").reader() }

    bmpUnicodeDataLines.forEach { line ->
        unicodeDataGenerators.forEach { it.appendLine(line) }
    }
    unicodeDataGenerators.forEach { it.close() }

    nonCommentSpecialCasingLines.forEach { line ->
        specialCasingGenerators.forEach { it.appendLine(line) }
    }
    specialCasingGenerators.forEach { it.close() }
}

internal class UnicodeDataLine(properties: List<String>) {
    init {
        require(properties.size == 15)
    }

    val char: String = properties[0]
    val name: String = properties[1]
    val categoryCode: String = properties[2]
    val uppercaseMapping: String = properties[12]
    val lowercaseMapping: String = properties[13]
    val titlecaseMapping: String = properties[14]

    override fun toString(): String {
        return "UnicodeDataLine{char=$char" +
                ", categoryCode=$categoryCode" +
                ", uppercaseMapping=$uppercaseMapping" +
                ", lowercaseMapping=$lowercaseMapping" +
                ", titlecaseMapping=$titlecaseMapping" +
                ", name=$name" +
                "}"
    }
}

internal interface UnicodeDataGenerator {
    fun appendLine(line: UnicodeDataLine)
    fun close()
}


internal class SpecialCasingLine(properties: List<String>) {
    init {
        require(properties.size in 5..6)
    }

    val char: String = properties[0]
    val lowercaseMapping: List<String> = properties[1].split(" ")
    val titlecaseMapping: List<String> = properties[2].split(" ")
    val uppercaseMapping: List<String> = properties[3].split(" ")
    val conditionList: List<String> = if (properties.size == 6) properties[4].split(" ") else emptyList()

    override fun toString(): String {
        return "SpecialCasingLine{char=$char" +
                ", lowercaseMapping=$lowercaseMapping" +
                ", uppercaseMapping=$uppercaseMapping" +
                ", titlecaseMapping=$titlecaseMapping" +
                ", conditionList=$conditionList" +
                "}"
    }
}

internal interface SpecialCasingGenerator {
    fun appendLine(line: SpecialCasingLine)
    fun close()
}
