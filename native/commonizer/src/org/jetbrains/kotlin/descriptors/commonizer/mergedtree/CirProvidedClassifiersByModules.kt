/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer.mergedtree

import gnu.trove.THashMap
import org.jetbrains.kotlin.descriptors.commonizer.ModulesProvider
import org.jetbrains.kotlin.descriptors.commonizer.cir.CirEntityId
import org.jetbrains.kotlin.descriptors.commonizer.cir.CirName
import org.jetbrains.kotlin.descriptors.commonizer.cir.CirPackageName
import org.jetbrains.kotlin.descriptors.commonizer.utils.compactMap
import org.jetbrains.kotlin.library.SerializedMetadata
import org.jetbrains.kotlin.library.metadata.parsePackageFragment
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.deserialization.*
import org.jetbrains.kotlin.types.Variance

internal class CirProvidedClassifiersByModules(modulesProvider: ModulesProvider) : CirProvidedClassifiers {
    private val classifiers = readClassifiers(modulesProvider)

    override fun hasClassifier(classifierId: CirEntityId) = classifierId in classifiers
    override fun classifier(classifierId: CirEntityId) = classifiers[classifierId]
}

private fun readClassifiers(modulesProvider: ModulesProvider): Map<CirEntityId, CirProvided.Classifier> {
    val result = THashMap<CirEntityId, CirProvided.Classifier>()

    modulesProvider.loadModuleInfos().forEach { moduleInfo ->
        val metadata = modulesProvider.loadModuleMetadata(moduleInfo.name)
        readModule(metadata, result::set)
    }

    return result
}

private inline fun readModule(metadata: SerializedMetadata, consumer: (CirEntityId, CirProvided.Classifier) -> Unit) {
    for (i in metadata.fragmentNames.indices) {
        val packageFqName = metadata.fragmentNames[i]
        val packageFragments = metadata.fragments[i]

        for (j in packageFragments.indices) {
            val packageFragmentProto = parsePackageFragment(packageFragments[j])

            val classProtos: List<ProtoBuf.Class> = packageFragmentProto.class_List
            val typeAliasProtos: List<ProtoBuf.TypeAlias> = packageFragmentProto.`package`?.typeAliasList.orEmpty()

            if (classProtos.isEmpty() && typeAliasProtos.isEmpty())
                break // this and next package fragments do not contain classifiers and can be skipped

            val packageName = CirPackageName.create(packageFqName)
            val strings = NameResolverImpl(packageFragmentProto.strings, packageFragmentProto.qualifiedNames)

            for (classProto in classProtos) {
                readClass(classProto, packageName, strings, consumer)
            }

            if (typeAliasProtos.isNotEmpty()) {
                val types = TypeTable(packageFragmentProto.`package`.typeTable)
                for (typeAliasProto in typeAliasProtos) {
                    readTypeAlias(typeAliasProto, packageName, strings, types, consumer)
                }
            }
        }
    }
}

private inline fun readClass(
    classProto: ProtoBuf.Class,
    packageName: CirPackageName,
    strings: NameResolver,
    consumer: (CirEntityId, CirProvided.Classifier) -> Unit
) {
    if (strings.isLocalClassName(classProto.fqName))
        return

    val classId = CirEntityId.create(strings.getQualifiedClassName(classProto.fqName))
    check(classId.packageName == packageName)

    val typeParameters = readTypeParameters(classProto.typeParameterList)
    val clazz = CirProvided.Class(typeParameters)

    consumer(classId, clazz)
}

private inline fun readTypeAlias(
    typeAliasProto: ProtoBuf.TypeAlias,
    packageName: CirPackageName,
    strings: NameResolver,
    types: TypeTable,
    consumer: (CirEntityId, CirProvided.Classifier) -> Unit
) {
    val typeAliasId = CirEntityId.create(packageName, CirName.create(strings.getString(typeAliasProto.name)))

    val typeParameterNameToId = mutableMapOf<Int, Int>()
    val typeParameters = readTypeParameters(typeAliasProto.typeParameterList, typeParameterNameToId::set)
    val underlyingType = readType(typeAliasProto.underlyingType(types), TypeReadContext(strings, types, typeParameterNameToId))
    val typeAlias = CirProvided.TypeAlias(typeParameters, underlyingType)

    consumer(typeAliasId, typeAlias)
}

private inline fun readTypeParameters(
    typeParameterProtos: List<ProtoBuf.TypeParameter>,
    nameToIdMapper: (name: Int, id: Int) -> Unit = { _, _ -> }
): List<CirProvided.TypeParameter> =
    typeParameterProtos.compactMap { typeParameterProto ->
        val typeParameter = CirProvided.TypeParameter(
            id = typeParameterProto.id,
            variance = readVariance(typeParameterProto.variance)
        )
        nameToIdMapper(typeParameterProto.name, typeParameter.id)
        typeParameter
    }

private class TypeReadContext(
    val strings: NameResolver,
    val types: TypeTable,
    val typeParameterNameToId: Map<Int, Int>
) {
    operator fun get(index: Int): String = strings.getString(index)
}

private fun readType(typeProto: ProtoBuf.Type, context: TypeReadContext): CirProvided.Type =
    with(typeProto.abbreviatedType(context.types) ?: typeProto) {
        when {
            hasClassName() -> CirProvided.ClassType(
                classId = CirEntityId.create(context.strings.getQualifiedClassName(className)),
                arguments = readTypeArguments(argumentList, context),
                isMarkedNullable = nullable
            )
            hasTypeAliasName() -> CirProvided.TypeAliasType(
                typeAliasId = CirEntityId.create(context.strings.getQualifiedClassName(typeAliasName)),
                arguments = readTypeArguments(argumentList, context),
                isMarkedNullable = nullable
            )
            hasTypeParameter() -> CirProvided.TypeParameterType(
                id = typeParameter,
                isMarkedNullable = nullable
            )
            hasTypeParameterName() -> CirProvided.TypeParameterType(
                id = context.typeParameterNameToId[typeParameterName] ?: error("No type parameter id for ${context[typeParameterName]}"),
                isMarkedNullable = nullable
            )
            else -> error("No classifier (class, type alias or type parameter) recorded for Type")
        }
    }

private fun readTypeArguments(argumentProtos: List<ProtoBuf.Type.Argument>, context: TypeReadContext): List<CirProvided.TypeProjection> =
    argumentProtos.compactMap { argumentProto ->
        val variance = readVariance(argumentProto.projection!!) ?: return@compactMap CirProvided.StarTypeProjection
        val typeProto = argumentProto.type(context.types) ?: error("No type argument for non-STAR projection in Type")

        CirProvided.RegularTypeProjection(
            variance = variance,
            type = readType(typeProto, context)
        )
    }

@Suppress("NOTHING_TO_INLINE")
private inline fun readVariance(varianceProto: ProtoBuf.TypeParameter.Variance): Variance =
    when (varianceProto) {
        ProtoBuf.TypeParameter.Variance.IN -> Variance.IN_VARIANCE
        ProtoBuf.TypeParameter.Variance.OUT -> Variance.OUT_VARIANCE
        ProtoBuf.TypeParameter.Variance.INV -> Variance.INVARIANT
    }

@Suppress("NOTHING_TO_INLINE")
private inline fun readVariance(varianceProto: ProtoBuf.Type.Argument.Projection): Variance? =
    when (varianceProto) {
        ProtoBuf.Type.Argument.Projection.IN -> Variance.IN_VARIANCE
        ProtoBuf.Type.Argument.Projection.OUT -> Variance.OUT_VARIANCE
        ProtoBuf.Type.Argument.Projection.INV -> Variance.INVARIANT
        ProtoBuf.Type.Argument.Projection.STAR -> null
    }
