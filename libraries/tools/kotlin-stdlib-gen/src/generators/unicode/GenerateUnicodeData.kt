/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode

import generators.unicode.mappings.MappingsGenerator
import generators.unicode.specialMappings.SpecialMappingsGenerator
import generators.unicode.ranges.CharCategoryTestGenerator
import generators.unicode.ranges.RangesGenerator
import generators.unicode.ranges.RangesWritingStrategy
import generators.unicode.mappings.string.StringCasingTestGenerator
import generators.unicode.mappings.string.StringLowercaseGenerator
import generators.unicode.mappings.string.StringUppercaseGenerator
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
private const val propListUrl = "https://www.unicode.org/Public/$unicodeVersion/ucd/PropList.txt"
private const val wordBreakPropertyUrl = "https://www.unicode.org/Public/$unicodeVersion/ucd/auxiliary/WordBreakProperty.txt"
private const val derivedCorePropertiesUrl = "https://www.unicode.org/Public/$unicodeVersion/ucd/DerivedCoreProperties.txt"

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
    fun readLines(url: String): List<String> {
        return URL(url).openStream().reader().readLines()
    }

    fun downloadFile(fromUrl: String, dest: File) {
        dest.writeText(readLines(fromUrl).joinToString(separator = "\n"))
    }

    val unicodeDataLines = readLines(unicodeDataUrl).map { line -> UnicodeDataLine(line.split(";")) }
    val bmpUnicodeDataLines = unicodeDataLines.filter { line -> line.char.length <= 4 } // Basic Multilingual Plane (BMP)

    val specialCasingLines = readLines(specialCasingUrl).filterNot {
        it.isEmpty() || it.startsWith("#")
    }.map { line ->
        SpecialCasingLine(line.split("; "))
    }

    val propListLines = URL(propListUrl).openStream().reader().readLines().filterNot {
        it.isEmpty() || it.startsWith("#")
    }.map { line ->
        PropertyLine(line.split("; ").map { it.trim() })
    }

    val wordBreakPropertyLines = URL(wordBreakPropertyUrl).openStream().reader().readLines().filterNot {
        it.isEmpty() || it.startsWith("#")
    }.map { line ->
        PropertyLine(line.split("; ").map { it.trim() })
    }

    val derivedCorePropertiesLines = URL(derivedCorePropertiesUrl).openStream().reader().readLines().filterNot {
        it.isEmpty() || it.startsWith("#")
    }.map { line ->
        PropertyLine(line.split("; ").map { it.trim() })
    }

    val categoryRangesGenerators = mutableListOf<UnicodeDataGenerator>()

    fun addRangesGenerators(generatedDir: File, target: KotlinTarget) {
        val category = RangesGenerator.forCharCategory(generatedDir.resolve("_CharCategories.kt"), target)
        val digit = RangesGenerator.forDigit(generatedDir.resolve("_DigitChars.kt"), target)
        val letter = RangesGenerator.forLetter(generatedDir.resolve("_LetterChars.kt"), target)
        val whitespace = RangesGenerator.forWhitespace(generatedDir.resolve("_WhitespaceChars.kt"))
        categoryRangesGenerators.add(category)
        categoryRangesGenerators.add(digit)
        categoryRangesGenerators.add(letter)
        categoryRangesGenerators.add(whitespace)
    }

    val caseMappingsGenerators = mutableListOf<UnicodeDataGenerator>()

    fun addMappingsGenerators(generatedDir: File, target: KotlinTarget) {
        val uppercase = MappingsGenerator.forUppercase(generatedDir.resolve("_UppercaseMappings.kt"), target)
        val lowercase = MappingsGenerator.forLowercase(generatedDir.resolve("_LowercaseMappings.kt"), target)
        val titlecase = MappingsGenerator.forTitlecase(generatedDir.resolve("_TitlecaseMappings.kt"))
        caseMappingsGenerators.add(uppercase)
        caseMappingsGenerators.add(lowercase)
        caseMappingsGenerators.add(titlecase)
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

    var stringUppercaseGenerator: StringUppercaseGenerator? = null
    var stringLowercaseGenerator: StringLowercaseGenerator? = null
    var stringCasingTestGenerator: StringCasingTestGenerator? = null

    when (args.size) {
        1 -> {
            val baseDir = File(args.first())

            val categoryTestFile = baseDir.resolve("libraries/stdlib/js/test/text/unicodeData/_CharCategoryTest.kt")
            val categoryTestGenerator = CharCategoryTestGenerator(categoryTestFile)
            categoryRangesGenerators.add(categoryTestGenerator)

            val jsGeneratedDir = baseDir.resolve("libraries/stdlib/js/src/generated/")
            addRangesGenerators(jsGeneratedDir, KotlinTarget.JS)

            val jsIrGeneratedDir = baseDir.resolve("libraries/stdlib/js-ir/src/generated/")
            addRangesGenerators(jsIrGeneratedDir, KotlinTarget.JS_IR)

            // For debugging. To see the file content
            fun downloadFile(url: String, fileName: String) {
                val file = baseDir.resolve("libraries/tools/kotlin-stdlib-gen/src/generators/unicode/$fileName")
                downloadFile(url, file)
            }
            downloadFile(unicodeDataUrl, "UnicodeData.txt")
            downloadFile(specialCasingUrl, "SpecialCasing.txt")
        }
        2 -> {
            val (targetName, targetDir) = args

            val target = KotlinTarget.values.singleOrNull { it.name.equals(targetName, ignoreCase = true) }
                ?: error("Invalid target: $targetName")

            val generatedDir = File(targetDir)
            addRangesGenerators(generatedDir, target)

            if (target == KotlinTarget.Native) {
                addMappingsGenerators(generatedDir, target)
                addSpecialMappingsGenerators(generatedDir, target)
                stringUppercaseGenerator = StringUppercaseGenerator(generatedDir.resolve("_StringUppercase.kt"), bmpUnicodeDataLines)
                stringLowercaseGenerator = StringLowercaseGenerator(generatedDir.resolve("_StringLowercase.kt"), RangesWritingStrategy.of(target), unicodeDataLines)
            }
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
        categoryRangesGenerators.forEach { it.appendLine(line) }
    }
    categoryRangesGenerators.forEach { it.close() }

    unicodeDataLines.forEach { line ->
        caseMappingsGenerators.forEach { it.appendLine(line) }
    }
    caseMappingsGenerators.forEach { it.close() }

    specialCasingLines.forEach { line ->
        specialCasingGenerators.forEach { it.appendLine(line) }
    }
    specialCasingGenerators.forEach { it.close() }

    stringUppercaseGenerator?.let {
        specialCasingLines.forEach { line -> it.appendSpecialCasingLine(line) }
        it.generate()
    }
    stringLowercaseGenerator?.let {
        specialCasingLines.forEach { line -> it.appendSpecialCasingLine(line) }
        propListLines.forEach { line -> it.appendPropListLine(line) }
        wordBreakPropertyLines.forEach { line -> it.appendWordBreakPropertyLine(line) }
        it.generate()
    }
    stringCasingTestGenerator?.let {
        derivedCorePropertiesLines.forEach { line -> it.appendDerivedCorePropertiesLine(line) }
        it.generate()
    }

}


internal interface UnicodeDataGenerator {
    fun appendLine(line: UnicodeDataLine)
    fun close()
}

internal interface SpecialCasingGenerator {
    fun appendLine(line: SpecialCasingLine)
    fun close()
}
