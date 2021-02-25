/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.types

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.resolve.sam.getAbstractMembers
import org.jetbrains.kotlin.types.typeUtil.replaceArgumentsWithNothing

class SamType constructor(val type: KotlinType) {

    val classDescriptor: ClassDescriptor
        get() {
            val classifier = type.constructor.declarationDescriptor
            assert(
                classifier is ClassDescriptor
            ) { "Sam/Fun interface not a class descriptor: $classifier" }
            return classifier as ClassDescriptor
        }

    val kotlinFunctionType: KotlinType
        get() {
            val descriptor = classDescriptor
            return descriptor.defaultFunctionTypeForSamInterface!!
        }

    val originalAbstractMethod: SimpleFunctionDescriptor
        get() = getAbstractMembers(classDescriptor)[0] as SimpleFunctionDescriptor

    override fun equals(other: Any?): Boolean {
        return other is SamType && type == other.type
    }

    override fun hashCode(): Int {
        return type.hashCode()
    }

    override fun toString(): String {
        return "SamType($type)"
    }
}

open class SamTypeFactory {
    fun createByValueParameter(valueParameter: ValueParameterDescriptor): SamType? {
        val singleArgumentType: KotlinType
        val originalSingleArgumentType: KotlinType?
        val varargElementType = valueParameter.varargElementType
        if (varargElementType != null) {
            singleArgumentType = varargElementType
            originalSingleArgumentType = valueParameter.original.varargElementType
            assert(originalSingleArgumentType != null) {
                "Value parameter and original value parameter have inconsistent varargs: " +
                        valueParameter + "; " + valueParameter.original
            }
        } else {
            singleArgumentType = valueParameter.type
            originalSingleArgumentType = valueParameter.original.type
        }
        if (singleArgumentType.isError || originalSingleArgumentType!!.isError) {
            return null
        }
        val originalTypeToUse =
        // This can be true in case when the value parameter is in the method of a generic type with out-projection.
        // We approximate Inv<Captured#1> to Nothing, while Inv itself can be a SAM interface safe to call here
            // (see testData genericSamProjectedOut.kt for details)
            if (KotlinBuiltIns.isNothing(singleArgumentType) // In such a case we can't have a proper supertype since wildcards are not allowed there,
            // so we use Nothing arguments instead that leads to a raw type used for a SAM wrapper.
            // See org.jetbrains.kotlin.codegen.state.KotlinTypeMapper#writeGenericType to understand how
            // raw types and Nothing arguments relate.
            ) originalSingleArgumentType.replaceArgumentsWithNothing() else singleArgumentType
        return create(originalTypeToUse.removeExternalProjections())
    }

    open fun isSamType(type: KotlinType): Boolean {
        val descriptor = type.constructor.declarationDescriptor
        return descriptor is ClassDescriptor && descriptor.isFun
    }

    fun create(originalType: KotlinType): SamType? {
        return if (isSamType(originalType)) SamType(originalType) else null
    }

    companion object {
        val INSTANCE = SamTypeFactory()
    }
}