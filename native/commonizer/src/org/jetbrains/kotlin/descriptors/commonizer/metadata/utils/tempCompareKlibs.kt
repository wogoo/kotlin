/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("DuplicatedCode")

package org.jetbrains.kotlin.descriptors.commonizer.metadata.utils

import kotlinx.metadata.*
import kotlinx.metadata.klib.KlibModuleMetadata
import kotlinx.metadata.klib.fqName
import org.jetbrains.kotlin.descriptors.commonizer.metadata.utils.MetadataDeclarationsComparator.Mismatch
import org.jetbrains.kotlin.descriptors.commonizer.metadata.utils.MetadataDeclarationsComparator.Result
import org.jetbrains.kotlin.library.ToolingSingleFileKlibResolveStrategy
import org.jetbrains.kotlin.library.resolveSingleFileKlib
import java.io.File
import org.jetbrains.kotlin.konan.file.File as KFile

@Suppress("SpellCheckingInspection")
fun main() {
    val baseDir1 = File("/Users/Dmitriy.Dolovov/temp/commonizer-output.511") // original
    val baseDir2 = File("/Users/Dmitriy.Dolovov/temp/commonizer-output.512") // fixed

    val klibPaths1 = baseDir1.listKlibs
    val klibPaths2 = baseDir2.listKlibs
    check(klibPaths1 == klibPaths2) {
        """
            Two sets of KLIB paths differ:
            $klibPaths1
            $klibPaths2
        """.trimIndent()
    }

    for (klibPath in klibPaths1) {
        doCompare(klibPath, baseDir1, baseDir2, false)
    }
}

private val File.listKlibs: Set<File>
    get() = walkTopDown()
        .filter { it.isDirectory && (it.name == "common" || it.parentFile.name == "platform") }
        .map { it.relativeTo(this) }
        .toSet()

private class NewResolver(libs: Collection<KFile>) {
    private val classes = mutableMapOf<String, KmClass>()
    private val typeAliases = mutableMapOf<String, KmTypeAlias>()
    private val properties = mutableMapOf<String, KmProperty>()
    private val functions = mutableMapOf<String, KmFunction>()

    init {
        libs.forEach { file ->
            val library = resolveSingleFileKlib(file, strategy = ToolingSingleFileKlibResolveStrategy)
            val metadata = KlibModuleMetadata.read(KotlinMetadataLibraryProvider(library))
            metadata.fragments.forEach { fragment ->
                fragment.classes.associateByTo(classes) { it.name }

                fragment.pkg?.let { pkg ->
                    val fqName = fragment.fqName?.takeIf { it.isNotEmpty() }?.let { "$it/" } ?: ""
                    pkg.typeAliases.associateByTo(typeAliases) { fqName + it.name }
                    pkg.properties.associateByTo(properties) { fqName + it.name }
                    pkg.functions.associateByTo(functions) { fqName + it.name }
                }
            }
        }
    }

    fun findClass(fullName: String): KmClass = classes[fullName] ?: error("Class not found: $fullName")
}


//private class ClassifierResolver(libs: Collection<KFile>) {
//    private val allFragments: Map<String, List<KmModuleFragment>>
//
//    init {
//        val result = mutableMapOf<String, MutableList<KmModuleFragment>>()
//        libs.forEach { file ->
//            val library = resolveSingleFileKlib(file, strategy = ToolingSingleFileKlibResolveStrategy)
//            val metadata = KlibModuleMetadata.read(KotlinMetadataLibraryProvider(library))
//            metadata.fragments.forEach { fragment ->
//                result.getOrPut(fragment.fqName!!) { mutableListOf() } += fragment
//            }
//        }
//        allFragments = result
//    }
//
//    fun findTypeAlias(packageName: String, typeAliasName: String): KmTypeAlias {
//        @Suppress("UNUSED_VARIABLE")
//        val packageName = packageName.replace('/', '.')
//
//        allFragments[packageName]?.forEach { fragment ->
//            fragment.pkg?.typeAliases?.forEach { typeAlias ->
//                if (typeAlias.name == typeAliasName)
//                    return typeAlias
//            }
//        }
//
//        error("Not found: type alias $typeAliasName in package $packageName")
//    }
//
//    fun findTypeAlias(fullTypeAliasName: String): KmTypeAlias {
//        val typeAliasName = fullTypeAliasName.substringAfterLast('/')
//        val packageName = fullTypeAliasName.substringBefore(typeAliasName).trimEnd('/').replace('/', '.')
//        return findTypeAlias(packageName, typeAliasName)
//    }
//
//    fun expandTypeAlias(typeAlias: KmTypeAlias): List<KmType> =
//        generateSequence(typeAlias) {
//            (it.underlyingType.classifier as? KmClassifier.TypeAlias)
//                ?.let { typeAliasClassifier -> findTypeAlias(typeAliasClassifier.name) }
//        }.map { it.underlyingType }.toList()
//}


//private fun checkTypeAliasIsNullableIfExpand(resolver: ClassifierResolver, typeAlias: KmTypeAlias) {
//    val expansionList = resolver.expandTypeAlias(typeAlias).map { Flag.Type.IS_NULLABLE(it.flags) to it.classifier }
//
//    var nullableReached = false
//    expansionList.forEach { (nullable, _) ->
//        when {
//            nullable == nullableReached -> Unit
//            nullable && !nullableReached -> nullableReached = true
//            !nullable && nullableReached -> error(
//                """|
//                   |There is a non-nullable type after nullable type in expansion list:
//                   |TypeAlias: ${typeAlias.name}, $typeAlias
//                   |Expansion list: $expansionList
//                """.trimMargin()
//            )
//        }
//    }
//
//    check(nullableReached) {
//        """|
//           |Last expanded type in type alias is non-nullable:
//           |TypeAlias: ${typeAlias.name}, $typeAlias
//           |Expansion list: $expansionList
//        """.trimMargin()
//    }
//}

private fun doCompare(klibPath: File, baseDir1: File, baseDir2: File, printMatches: Boolean) {
    println("BEGIN $klibPath")

    val libs1: Map<String, KFile> =
        baseDir1.resolve(klibPath).listFiles().orEmpty().groupBy { it.name }.mapValues { KFile(it.value.single().absolutePath) }
    val libs2: Map<String, KFile> =
        baseDir2.resolve(klibPath).listFiles().orEmpty().groupBy { it.name }.mapValues { KFile(it.value.single().absolutePath) }

    val allLibs: Set<String> = libs1.keys intersect libs2.keys
    check(allLibs == libs1.keys)
    check(allLibs == libs2.keys)

    val resolver1 = NewResolver(libs1.values)
    val resolver2 = NewResolver(libs2.values)

    for (lib in allLibs.sorted()) {
        val lib1 = libs1.getValue(lib)
        val lib2 = libs2.getValue(lib)

        val klib1 = resolveSingleFileKlib(lib1, strategy = ToolingSingleFileKlibResolveStrategy)
        val klib2 = resolveSingleFileKlib(lib2, strategy = ToolingSingleFileKlibResolveStrategy)

        val metadata1 = KlibModuleMetadata.read(KotlinMetadataLibraryProvider(klib1))
        val metadata2 = KlibModuleMetadata.read(KotlinMetadataLibraryProvider(klib2))

        when (val result = MetadataDeclarationsComparator.compare(metadata1, metadata2)) {
            Result.Success -> if (printMatches) println("- [full match] $lib")
            is Result.Failure -> {
                val mismatches = result.mismatches/*.filter { mismatch ->
                    if (mismatch is Mismatch.MissingEntity) {
                        if (mismatch.kind == "AbbreviatedType") {
                            if (mismatch.missingInA) return@filter false

                            val index = mismatch.path.indexOfFirst {
                                it == "ReturnType" || it.startsWith("ValueParameter ") || it == "SetterValueParameter"
                            }
                            if (index != -1 && mismatch.path.getOrNull(index + 1) != "AbbreviatedType")
                                return@filter false
                            else
                                return@filter false
                        }
                    } else if (mismatch is Mismatch.DifferentValues) {
                        if (mismatch.kind == "Classifier" && "/common/" !in lib1.path) {
                            val oldClassName = (mismatch.valueA as KmClassifier.Class).name
                            val newClassName = (mismatch.valueB as KmClassifier.Class).name

                            val allowedClassNames = setOf(
                                "kotlin/Float",
                                "kotlin/Double",
                                "kotlin/Int",
                                "kotlin/Long",
                                "kotlin/UInt",
                                "kotlin/ULong"
                            )
                            if (oldClassName.startsWith("platform/") && newClassName in allowedClassNames)
                                return@filter false

                            error(oldClassName + " -> " + newClassName)
                        }
                    }

                    true
                }*/

                if (mismatches.isNotEmpty()) {

                    println("- [MISMATCHES] $lib ${mismatches.size}")
                    mismatches
                        .groupingBy { it::class.java.simpleName to it.kind }
                        .eachCount()
                        .entries
                        .sortedBy { it.key.first + "-" + it.key.second }
                        .forEach { (key, value) ->
                            println("\t${key.first} ${key.second} -> $value")
                        }

                }
//                val groupedByPath: MutableMap<List<String>, MutableList<Mismatch>> = result.mismatches.groupByTo(mutableMapOf()) { it.path }
//
//                groupedByPath.values.flatten().forEach { mismatch ->
//                    if (mismatch !is Mismatch.DifferentValues) return@forEach
//                    if (mismatch.kind != "Classifier" || mismatch.path.last() != "UnderlyingType") return@forEach
//                    if ((mismatch.valueB as? KmClassifier.Class)?.name !in arrayOf(
//                            "kotlinx/cinterop/CPointer",
//                            "kotlin/Function0",
//                            "kotlin/Function1",
//                            "kotlin/Function2",
//                            "kotlin/Function3"
//                        )
//                    ) return@forEach
//
//                    var fullTypeAliasName = (mismatch.valueA as? KmClassifier.TypeAlias)?.name ?: return@forEach
//
//                    while (true) {
//                        val typeAlias = resolveTypeAlias(fullTypeAliasName)
//
//                        when (val underlyingTypeClassifier = typeAlias.underlyingType.classifier) {
//                            is KmClassifier.Class -> {
//                                if (underlyingTypeClassifier.name !in arrayOf(
//                                        "kotlinx/cinterop/CPointer",
//                                        "kotlin/Function0",
//                                        "kotlin/Function1",
//                                        "kotlin/Function2",
//                                        "kotlin/Function3"
//                                    )
//                                ) {
//                                    error("Unexpected class found: ${underlyingTypeClassifier.name}")
//                                }
//                            }
//                            is KmClassifier.TypeAlias -> {
//                                if (underlyingTypeClassifier.name !in arrayOf(
//                                        "kotlinx/cinterop/CArrayPointer",
//                                        "kotlinx/cinterop/COpaquePointer"
//                                    )
//                                ) {
//                                    fullTypeAliasName = underlyingTypeClassifier.name
//                                    continue
//                                }
//                            }
//                        }
//
//                        val relevantMismatches = groupedByPath.getValue(mismatch.path)
//                        relevantMismatches -= mismatch
//                        relevantMismatches.removeIf { it is Mismatch.MissingEntity && it.kind == "TypeProjection" }
//                        break
//                    }
//                }
//
//                groupedByPath.values.flatten().forEach { mismatch ->
//                    if (mismatch !is Mismatch.DifferentValues) return@forEach
//                    if (mismatch.kind != "Flag" || mismatch.name != "IS_NULLABLE") return@forEach
//                    if (mismatch.path.last() !in arrayOf("UnderlyingType", "ExpandedType")) return@forEach
//                    if (mismatch.valueA.toString() != "false" || mismatch.valueB.toString() != "true") return
//
//                    groupedByPath.getValue(mismatch.path) -= mismatch
//                }
//
//                if ("linux_x64" in klibPath.path) {
//                    groupedByPath.values.flatten().forEach { mismatch ->
//                        if (mismatch !is Mismatch.MissingEntity) return@forEach
//                        if (mismatch.kind != "TypeAlias") return@forEach
//                        val options = listOf("caddr_t", "sig_t", "va_list")
//                        if (mismatch.name !in options && mismatch.name.removeSuffix("Var") !in options) return@forEach
//
//                        groupedByPath.getValue(mismatch.path) -= mismatch
//                    }
//
//                    groupedByPath.values.flatten().forEach { mismatch ->
//                        if (mismatch !is Mismatch.MissingEntity) return@forEach
//                        if (mismatch.kind != "Function") return@forEach
//                        val function = mismatch.presentValue as? KmFunction ?: return@forEach
//                        if (function.valueParameters.any {
//                                (it.type?.classifier as? KmClassifier.Class)?.name == "kotlinx/cinterop/CPointer"
//                                        && (it.type?.arguments?.singleOrNull()?.type?.classifier as? KmClassifier.Class)?.name?.endsWith("va_list_tag") == true
//                            }) {
//                            groupedByPath.getValue(mismatch.path) -= mismatch
//                        }
//                    }
//                }
//
//                groupedByPath.entries.removeIf { (_, values) -> values.isEmpty() }
//
//                if (groupedByPath.isNotEmpty()) {
//                    println("- [MISMATCHES] $lib")
//                    groupedByPath.values.flatten().forEachIndexed { index, mismatch ->
//                        println("  ${index + 1}. $mismatch")
//                    }
//                } else if (printMatches) println("- [match] $lib")
            }
        }
    }
    println("END $klibPath\n")
}
