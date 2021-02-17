/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer.mergedtree

import org.jetbrains.kotlin.descriptors.commonizer.ModulesProvider
import org.jetbrains.kotlin.descriptors.commonizer.cir.CirEntityId
import org.jetbrains.kotlin.types.Variance

/** A set of classes and type aliases provided by libraries (either the libraries to commonize, or their dependency libraries)/ */
interface CirProvidedClassifiers {
    fun hasClassifier(classifierId: CirEntityId): Boolean
    fun classifier(classifierId: CirEntityId): CirProvided.Classifier?

    companion object {
        val EMPTY: CirProvidedClassifiers = object : CirProvidedClassifiers {
            override fun hasClassifier(classifierId: CirEntityId) = false
            override fun classifier(classifierId: CirEntityId): CirProvided.Classifier? = null
        }

        fun of(vararg delegates: CirProvidedClassifiers): CirProvidedClassifiers = object : CirProvidedClassifiers {
            override fun hasClassifier(classifierId: CirEntityId) = delegates.any { it.hasClassifier(classifierId) }

            override fun classifier(classifierId: CirEntityId): CirProvided.Classifier? {
                for (delegate in delegates) {
                    delegate.classifier(classifierId)?.let { return it }
                }
                return null
            }
        }

        fun by(modulesProvider: ModulesProvider?): CirProvidedClassifiers =
            if (modulesProvider != null) CirProvidedClassifiersByModules(modulesProvider) else EMPTY
    }
}

object CirProvided {
    /* Classifiers */
    sealed interface Classifier {
        val typeParameters: Collection<TypeParameter>
    }

    class Class(override val typeParameters: Collection<TypeParameter>) : Classifier

    class TypeAlias(
        override val typeParameters: Collection<TypeParameter>,
        val underlyingType: Type
    ) : Classifier

    /* Type parameter */
    class TypeParameter(val id: Int, val variance: Variance)

    /* Types */
    sealed interface Type {
        val isMarkedNullable: Boolean
    }

    class TypeParameterType(val id: Int, override val isMarkedNullable: Boolean) : Type
    class ClassType(val classId: CirEntityId, val arguments: List<TypeProjection>, override val isMarkedNullable: Boolean) : Type
    class TypeAliasType(val typeAliasId: CirEntityId, val arguments: List<TypeProjection>, override val isMarkedNullable: Boolean) : Type

    /* Type projections */
    sealed interface TypeProjection
    object StarTypeProjection : TypeProjection
    class RegularTypeProjection(val variance: Variance, val type: Type) : TypeProjection
}

