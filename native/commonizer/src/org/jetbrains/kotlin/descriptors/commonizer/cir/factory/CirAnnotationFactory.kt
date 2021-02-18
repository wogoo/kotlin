/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer.cir.factory

import gnu.trove.THashMap
import kotlinx.metadata.Flag
import kotlinx.metadata.Flags
import kotlinx.metadata.KmAnnotation
import kotlinx.metadata.KmAnnotationArgument
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.commonizer.cir.*
import org.jetbrains.kotlin.descriptors.commonizer.cir.impl.CirAnnotationImpl
import org.jetbrains.kotlin.descriptors.commonizer.mergedtree.CirProvided
import org.jetbrains.kotlin.descriptors.commonizer.mergedtree.CirProvidedClassifiers
import org.jetbrains.kotlin.descriptors.commonizer.utils.*
import org.jetbrains.kotlin.descriptors.commonizer.utils.compact
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.constants.AnnotationValue
import org.jetbrains.kotlin.resolve.constants.ConstantValue

object CirAnnotationFactory {
    private val interner = Interner<CirAnnotation>()

    fun create(source: AnnotationDescriptor): CirAnnotation {
        val type = CirTypeFactory.create(source.type, useAbbreviation = false) as CirClassType

        val allValueArguments: Map<Name, ConstantValue<*>> = source.allValueArguments
        if (allValueArguments.isEmpty())
            return create(type = type, constantValueArguments = emptyMap(), annotationValueArguments = emptyMap())

        val constantValueArguments: MutableMap<CirName, CirConstantValue<*>> = THashMap(allValueArguments.size)
        val annotationValueArguments: MutableMap<CirName, CirAnnotation> = THashMap(allValueArguments.size)

        allValueArguments.forEach { (name, constantValue) ->
            val cirName = CirName.create(name)
            if (constantValue is AnnotationValue)
                annotationValueArguments[cirName] = create(source = constantValue.value)
            else
                constantValueArguments[cirName] = CirConstantValueFactory.createSafely(
                    constantValue = constantValue,
                    constantName = cirName,
                    owner = source,
                )
        }

        return create(
            type = type,
            constantValueArguments = constantValueArguments.compact(),
            annotationValueArguments = annotationValueArguments.compact()
        )
    }

    fun createAnnotations(
        flags: Flags,
        providedClassifiers: CirProvidedClassifiers,
        annotations: () -> List<KmAnnotation>
    ): List<CirAnnotation> {
        return if (!Flag.Common.HAS_ANNOTATIONS(flags))
            emptyList()
        else
            annotations().compactMap { create(it, providedClassifiers) }
    }

    fun create(source: KmAnnotation, providedClassifiers: CirProvidedClassifiers): CirAnnotation {
        val classifierId = CirEntityId.create(source.className)
        val classifier = providedClassifiers.classifier(classifierId)
            ?: error("Unresolved annotation class: $classifierId")

        check(classifier is CirProvided.Class) { "Unexpectedly resolved type alias instead of annotation class: $classifierId" }

        val type = CirTypeFactory.createClassType(
            classId = classifierId,
            outerType = null, // annotation class can't be inner class
            visibility = classifier.visibility,
            arguments = classifier.typeParameters.compactMap { typeParameter ->
                CirTypeProjectionImpl(
                    projectionKind = typeParameter.variance,
                    type = CirTypeFactory.createTypeParameterType(
                        index = typeParameter.id,
                        isMarkedNullable = false
                    )
                )
            },
            isMarkedNullable = false
        )

        val allValueArguments: Map<String, KmAnnotationArgument<*>> = source.arguments
        if (allValueArguments.isEmpty())
            return create(type = type, constantValueArguments = emptyMap(), annotationValueArguments = emptyMap())

        val constantValueArguments: MutableMap<CirName, CirConstantValue<*>> = THashMap(allValueArguments.size)
        val annotationValueArguments: MutableMap<CirName, CirAnnotation> = THashMap(allValueArguments.size)

        allValueArguments.forEach { (name, constantValue) ->
            val cirName = CirName.create(name)
            if (constantValue is KmAnnotationArgument.AnnotationValue)
                annotationValueArguments[cirName] = create(source = constantValue.value, providedClassifiers)
            else
                constantValueArguments[cirName] = CirConstantValueFactory.createSafely(
                    constantValue = constantValue,
                    constantName = cirName,
                    owner = source,
                )
        }

        return create(
            type = type,
            constantValueArguments = constantValueArguments.compact(),
            annotationValueArguments = annotationValueArguments.compact()
        )
    }

    fun create(
        type: CirClassType,
        constantValueArguments: Map<CirName, CirConstantValue<*>>,
        annotationValueArguments: Map<CirName, CirAnnotation>
    ): CirAnnotation {
        return interner.intern(
            CirAnnotationImpl(
                type = type,
                constantValueArguments = constantValueArguments,
                annotationValueArguments = annotationValueArguments
            )
        )
    }
}
