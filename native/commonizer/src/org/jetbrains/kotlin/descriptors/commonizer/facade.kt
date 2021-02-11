/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer

import kotlinx.metadata.klib.ChunkedKlibModuleFragmentWriteStrategy
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.commonizer.ResultsConsumer.ModuleResult
import org.jetbrains.kotlin.descriptors.commonizer.ResultsConsumer.Status
import org.jetbrains.kotlin.descriptors.commonizer.cir.*
import org.jetbrains.kotlin.descriptors.commonizer.cir.factory.CirTypeFactory
import org.jetbrains.kotlin.descriptors.commonizer.core.CommonizationVisitor
import org.jetbrains.kotlin.descriptors.commonizer.mergedtree.*
import org.jetbrains.kotlin.descriptors.commonizer.mergedtree.CirNode.Companion.dimension
import org.jetbrains.kotlin.descriptors.commonizer.mergedtree.CirTreeMerger.CirTreeMergeResult
import org.jetbrains.kotlin.descriptors.commonizer.metadata.MetadataBuilder
import org.jetbrains.kotlin.library.SerializedMetadata
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.Variance
import kotlin.math.max

fun runCommonization(parameters: CommonizerParameters) {
    if (!parameters.hasAnythingToCommonize()) {
        parameters.resultsConsumer.allConsumed(Status.NOTHING_TO_DO)
        return
    }

    val storageManager = LockBasedStorageManager("Declarations commonization")

    val mergeResult = mergeAndCommonize(storageManager, parameters)
    val mergedTree = mergeResult.root

    // build resulting declarations:
    for (targetIndex in 0 until mergedTree.dimension) {
        serializeTarget(mergeResult, targetIndex, parameters)
    }

    parameters.resultsConsumer.allConsumed(Status.DONE)
}

private fun mergeAndCommonize(storageManager: StorageManager, parameters: CommonizerParameters): CirTreeMergeResult {
    // build merged tree:
    val classifiers = CirKnownClassifiers(
        commonizedNodes = CirCommonizedClassifierNodes.default(),
        forwardDeclarations = CirForwardDeclarations.default(),
        commonDependencies = CirProvidedClassifiers.of(
            CirFictitiousFunctionClassifiers,
            CirProvidedClassifiers.by(parameters.dependencyModulesProvider)
        )
    )
    val mergeResult = CirTreeMerger(storageManager, classifiers, parameters).merge()

    // DEBUG: BEGIN
    val classifiersV2 = CirKnownClassifiers(
        commonizedNodes = CirCommonizedClassifierNodes.default(),
        forwardDeclarations = CirForwardDeclarations.default(),
        commonDependencies = CirProvidedClassifiers.of(
            CirFictitiousFunctionClassifiers,
            CirProvidedClassifiers.by(parameters.dependencyModulesProvider)
        )
    )
    val mergeResultV2 = CirTreeBuilder(storageManager, classifiersV2, parameters).build()
    diffTrees(mergeResult.root, mergeResultV2.root)
    // DEBUG: END

    // commonize:
    val mergedTree = mergeResult.root
    mergedTree.accept(CommonizationVisitor(classifiers, mergedTree), Unit)
    parameters.progressLogger?.invoke("Commonized declarations")

    return mergeResult
}

private fun serializeTarget(mergeResult: CirTreeMergeResult, targetIndex: Int, parameters: CommonizerParameters) {
    val mergedTree = mergeResult.root
    val target = mergedTree.getTarget(targetIndex)

    MetadataBuilder.build(mergedTree, targetIndex, parameters.statsCollector) { metadataModule ->
        val libraryName = metadataModule.name
        val serializedMetadata = with(metadataModule.write(KLIB_FRAGMENT_WRITE_STRATEGY)) {
            SerializedMetadata(header, fragments, fragmentNames)
        }
        val manifestData = parameters.manifestDataProvider.getManifest(target, libraryName)
        parameters.resultsConsumer.consume(target, ModuleResult.Commonized(libraryName, serializedMetadata, manifestData))
    }

    if (target is LeafCommonizerTarget) {
        mergeResult.missingModuleInfos.getValue(target).forEach {
            parameters.resultsConsumer.consume(target, ModuleResult.Missing(it.originalLocation))
        }
    }

    parameters.resultsConsumer.targetConsumed(target)
}

@Suppress("DuplicatedCode")
private fun diffTrees(oldRootNode: CirRootNode, newRootNode: CirRootNode) {
    // new -> old
    val replacements = mapOf(
        "objcnames/protocols/MTLTextureProtocol" to "platform/Metal/MTLTextureProtocol",
        "objcnames/protocols/MTLDeviceProtocol" to "platform/Metal/MTLDeviceProtocol",
        "objcnames/protocols/MTLCommandBufferProtocol" to "platform/Metal/MTLCommandBufferProtocol",
        "objcnames/protocols/MTLCommandQueueProtocol" to "platform/Metal/MTLCommandQueueProtocol",
        "objcnames/classes/UNNotification" to "platform/UserNotifications/UNNotification",
        "objcnames/classes/MIDICIProfile" to "platform/CoreMIDI/MIDICIProfile",
        "objcnames/classes/NSURL" to "platform/Foundation/NSURL",
        "objcnames/classes/NSData" to "platform/Foundation/NSData",
        "objcnames/classes/NSDate" to "platform/Foundation/NSDate",
        "objcnames/classes/INIntentResponse" to "platform/Intents/INIntentResponse",
        "objcnames/classes/INShortcut" to "platform/Intents/INShortcut",
        "objcnames/classes/INInteraction" to "platform/Intents/INInteraction",
        "objcnames/classes/INVoiceShortcut" to "platform/Intents/INVoiceShortcut"
    )

    val mismatches = mutableListOf<String>()

    fun Any?.missing() = if (this == null) "missing" else "present"

    fun <T : Any> checkKeys(where: String, oldKeys: Set<T>, newKeys: Set<T>): Collection<Pair<T, T>> {
        val commonKeys = oldKeys intersect newKeys
        val justOldKeys = oldKeys - commonKeys
        val justNewKeys = newKeys - commonKeys

        val keyPairsToCompareFurther = ArrayList<Pair<T, T>>(commonKeys.size + max(justOldKeys.size, justNewKeys.size))
        commonKeys.mapTo(keyPairsToCompareFurther) { commonKey -> commonKey to commonKey }

        val justOldKeysStrToKey = justOldKeys.associateByTo(HashMap(), Any::toString)
        val justNewKeysStrToKey = justNewKeys.associateByTo(HashMap(), Any::toString)

        justNewKeysStrToKey.keys.toList().forEach { newKeyStr ->
            if ("objcnames" in newKeyStr) {
                var maybeOldKeyStr = newKeyStr
                replacements.forEach { (replacementNew, replacementOld) ->
                    maybeOldKeyStr = maybeOldKeyStr.replace(replacementNew, replacementOld)
                }

                val oldKey = justOldKeysStrToKey[maybeOldKeyStr]
                if (oldKey != null) {
                    val newKey = justNewKeysStrToKey.getValue(newKeyStr)
                    keyPairsToCompareFurther += oldKey to newKey
                    justOldKeysStrToKey -= maybeOldKeyStr
                    justNewKeysStrToKey -= newKeyStr
                }
            }
        }

        if (justOldKeysStrToKey.isNotEmpty())
            mismatches.add("JUST_OLD(${justOldKeysStrToKey.size}) $where: " + justOldKeysStrToKey.keys.sorted())

        if (justNewKeysStrToKey.isNotEmpty())
            mismatches.add("JUST_NEW(${justNewKeysStrToKey.size}) $where: " + justNewKeysStrToKey.keys.sorted())

        return keyPairsToCompareFurther
    }

    fun checkBoolean(where: String, what: String, old: Boolean, new: Boolean) {
        if (old != new)
            mismatches.add("$where > $what: $old != $new")
    }

    fun checkType(where: String, what: String, old: CirType?, new: CirType?) {
        if (old == null && new == null)
            return

        if (old == null || new == null) {
            mismatches.add("$where > $what: old=${old.missing()}, new=${new.missing()}")
        } else if (old == new || new == CirTypeFactory.StandardTypes.NON_EXISTING_TYPE) {
            return
        } else {
            mismatches.add("$where > $what: $old != $new")
        }
    }

    fun checkTypes(where: String, what: String, olds: List<CirType>, news: List<CirType>) {
        if (olds.size != news.size) {
            mismatches.add("$where > $what.size: ${olds.size} != ${news.size}")
        } else {
            for (i in olds.indices) {
                val old = olds[i]
                val new = news[i]
                checkType(where, "$what[$i]", old, new)
            }
        }
    }

    fun checkAnnotation(where: String, old: CirAnnotation, new: CirAnnotation) {
        checkType(where, "type", old.type, new.type)

        if (old.constantValueArguments != new.constantValueArguments)
            mismatches.add("$where > constantValueArguments: ${old.constantValueArguments} != ${new.constantValueArguments}")

        if (old.annotationValueArguments.keys != new.annotationValueArguments.keys)
            mismatches.add("$where > annotationValueArguments.keys: ${old.annotationValueArguments.keys} != ${new.annotationValueArguments.keys}")

        old.annotationValueArguments.keys.forEach { key ->
            val oldAnnotation = old.annotationValueArguments.getValue(key)
            val newAnnotation = new.annotationValueArguments.getValue(key)
            checkAnnotation("$where > annotations[$key]", oldAnnotation, newAnnotation)
        }
    }

    fun checkAnnotations(
        where: String,
        what: String,
        olds: List<CirAnnotation>?,
        news: List<CirAnnotation>?,
        newsMayBeEmpty: Boolean = false
    ) {
        if (olds == null && news == null)
            return

        when {
            olds == null || news == null -> mismatches.add("$where > $what: old=${olds.missing()}, new=${news.missing()}")
            olds.size != news.size -> when {
                olds.isNotEmpty() && news.isEmpty() && newsMayBeEmpty -> Unit
                else -> mismatches.add("$where > $what.size: ${olds.size} != ${news.size}")
            }
            else -> for (i in olds.indices) {
                val old = olds[i]
                val new = news[i]
                checkAnnotation("$where > $what[$i]", old, new)
            }
        }
    }

    fun checkName(where: String, what: String, old: CirName?, new: CirName?) {
        if (old == null && new == null)
            return

        if (old == null || new == null) {
            mismatches.add("$where > $what: old=${old.missing()}, new=${new.missing()}")
        } else if (old != new) {
            mismatches.add("$where > $what: $old != $new")
        }
    }

    fun checkVariance(where: String, old: Variance, new: Variance) {
        if (old != new)
            mismatches.add("$where > variance: $old != $new")
    }

    fun checkTypeParameters(where: String, olds: List<CirTypeParameter>, news: List<CirTypeParameter>) {
        if (olds.size != news.size) {
            mismatches.add("$where > typeParameters.size: ${olds.size} != ${news.size}")
        } else {
            for (i in olds.indices) {
                val old = olds[i]
                val new = news[i]

                checkAnnotations("$where > typeParameters[$i]", "annotations", old.annotations, new.annotations)
                checkName("$where > typeParameters[$i]", "name", old.name, new.name)
                checkBoolean("$where > typeParameters[$i]", "isReified", old.isReified, new.isReified)
                checkVariance("$where > typeParameters[$i]", old.variance, new.variance)
                checkTypes("$where > typeParameters[$i]", "upperBounds", old.upperBounds, new.upperBounds)
            }
        }
    }

    fun checkVisibility(where: String, old: DescriptorVisibility, new: DescriptorVisibility) {
        if (old != new)
            mismatches.add("$where > visibility: $old != $new")
    }

    fun checkModality(where: String, old: Modality, new: Modality) {
        if (old != new)
            mismatches.add("$where > modality: $old != $new")
    }

    fun checkKind(where: String, old: CallableMemberDescriptor.Kind, new: CallableMemberDescriptor.Kind) {
        if (old != new)
            mismatches.add("$where > kind: $old != $new")
    }

    fun checkKind(where: String, old: ClassKind, new: ClassKind) {
        if (old != new)
            mismatches.add("$where > kind: $old != $new")
    }

    fun checkModifiers(where: String, old: CirFunctionModifiers, new: CirFunctionModifiers) {
        if (old != new)
            mismatches.add("$where > modifiers: $old != $new")
    }

    fun checkContainingClass(where: String, old: CirContainingClass?, new: CirContainingClass?) {
        if (old == null && new == null)
            return

        if (old == null || new == null) {
            mismatches.add("$where > containingClass: old=${old.missing()}, new=${new.missing()}")
        } else {
            checkBoolean("$where > containingClass", "isData", old.isData, new.isData)
            checkKind("$where > containingClass", old.kind, new.kind)
            checkModality("$where > containingClass", old.modality, new.modality)
        }
    }

    fun checkReceiver(where: String, old: CirExtensionReceiver?, new: CirExtensionReceiver?) {
        if (old == null && new == null)
            return

        if (old == null || new == null) {
            mismatches.add("$where > receiver: old=${old.missing()}, new=${new.missing()}")
        } else {
            checkAnnotations("$where > receiver", "annotations", old.annotations, new.annotations, newsMayBeEmpty = true)
            checkType("$where > receiver", "type", old.type, new.type)
        }
    }

    fun checkValueParameters(where: String, olds: List<CirValueParameter>, news: List<CirValueParameter>) {
        if (olds.size != news.size) {
            mismatches.add("$where > valueParameters.size: ${olds.size} != ${news.size}")
        } else {
            for (i in olds.indices) {
                val old = olds[i]
                val new = news[i]

                checkAnnotations("$where > valueParameters[$i]", "annotations", old.annotations, new.annotations)
                checkName("$where > valueParameters[$i]", "name", old.name, new.name)
                checkType("$where > valueParameters[$i]", "returnType", old.returnType, new.returnType)
                checkType("$where > valueParameters[$i]", "varargElementType", old.varargElementType, new.varargElementType)
                checkBoolean("$where > valueParameters[$i]", "declaresDefaultValue", old.declaresDefaultValue, new.declaresDefaultValue)
                checkBoolean("$where > valueParameters[$i]", "isCrossinline", old.isCrossinline, new.isCrossinline)
                checkBoolean("$where > valueParameters[$i]", "isNoinline", old.isNoinline, new.isNoinline)
            }
        }
    }

    fun checkFunctions(where: String, old: CirFunction?, new: CirFunction?) {
        if (old == null && new == null)
            return

        if (old == null || new == null) {
            mismatches.add("$where: old=${old.missing()}, new=${new.missing()}")
        } else {
            checkAnnotations(where, "annotations", old.annotations, new.annotations)
            checkName(where, "name", old.name, new.name)
            checkTypeParameters(where, old.typeParameters, new.typeParameters)
            checkVisibility(where, old.visibility, new.visibility)
            checkModality(where, old.modality, new.modality)
            checkContainingClass(where, old.containingClass, new.containingClass)
            checkValueParameters(where, old.valueParameters, new.valueParameters)
            checkBoolean(where, "stableParameterNames", old.hasStableParameterNames, new.hasStableParameterNames)
            checkReceiver(where, old.extensionReceiver, new.extensionReceiver)
            checkType(where, "returnType", old.returnType, new.returnType)
            checkKind(where, old.kind, new.kind)
            checkModifiers(where, old.modifiers, new.modifiers)
        }
    }

    fun checkConstant(where: String, old: CirConstantValue<*>?, new: CirConstantValue<*>?) {
        if (old == null && new == null)
            return

        if (old == null || new == null) {
            mismatches.add("$where > constant: old=${old.missing()}, new=${new.missing()}")
        } else if (old != new) {
            mismatches.add("$where > constant: $old != $new")
        }
    }

    fun checkGetter(where: String, old: CirPropertyGetter?, new: CirPropertyGetter?) {
        if (old == null && new == null)
            return

        if (old == null || new == null) {
            mismatches.add("$where > getter: old=${old.missing()}, new=${new.missing()}")
        } else {
            checkAnnotations("$where > getter", "annotations", old.annotations, new.annotations)
            checkBoolean("$where > getter", "isDefault", old.isDefault, new.isDefault)
            checkBoolean("$where > getter", "isExternal", old.isExternal, new.isExternal)
            checkBoolean("$where > getter", "isInline", old.isInline, new.isInline)
        }
    }

    fun checkSetter(where: String, old: CirPropertySetter?, new: CirPropertySetter?) {
        if (old == null && new == null)
            return

        if (old == null || new == null) {
            mismatches.add("$where > setter: old=${old.missing()}, new=${new.missing()}")
        } else {
            checkAnnotations("$where > setter", "annotations", old.annotations, new.annotations)
            checkAnnotations("$where > setter", "parameterAnnotations", old.parameterAnnotations, new.parameterAnnotations)
            checkVisibility("$where > setter", old.visibility, new.visibility)
            checkBoolean("$where > setter", "isDefault", old.isDefault, new.isDefault)
            checkBoolean("$where > setter", "isExternal", old.isExternal, new.isExternal)
            checkBoolean("$where > setter", "isInline", old.isInline, new.isInline)
        }
    }

    fun checkProperties(where: String, old: CirProperty?, new: CirProperty?) {
        if (old == null && new == null)
            return

        if (old == null || new == null) {
            mismatches.add("$where: old=${old.missing()}, new=${new.missing()}")
        } else {
            checkAnnotations(where, "annotations", old.annotations, new.annotations)
            checkName(where, "name", old.name, new.name)
            checkTypeParameters(where, old.typeParameters, new.typeParameters)
            checkVisibility(where, old.visibility, new.visibility)
            checkModality(where, old.modality, new.modality)
            checkContainingClass(where, old.containingClass, new.containingClass)
            checkBoolean(where, "isExternal", old.isExternal, new.isExternal)
            checkReceiver(where, old.extensionReceiver, new.extensionReceiver)
            checkType(where, "returnType", old.returnType, new.returnType)
            checkKind(where, old.kind, new.kind)
            checkBoolean(where, "isVar", old.isVar, new.isVar)
            checkBoolean(where, "isLateInit", old.isLateInit, new.isLateInit)
            checkBoolean(where, "isConst", old.isConst, new.isConst)
            checkBoolean(where, "isDelegate", old.isDelegate, new.isDelegate)
            checkGetter(where, old.getter, new.getter)
            checkSetter(where, old.setter, new.setter)
            checkAnnotations(where, "backingFieldAnnotations", old.backingFieldAnnotations, new.backingFieldAnnotations, newsMayBeEmpty = true)
            checkAnnotations(where, "delegateFieldAnnotations", old.delegateFieldAnnotations, new.delegateFieldAnnotations, newsMayBeEmpty = true)
            checkConstant(where, old.compileTimeInitializer, new.compileTimeInitializer)
        }
    }

    fun checkConstructors(where: String, old: CirClassConstructor?, new: CirClassConstructor?) {
        if (old == null && new == null)
            return

        if (old == null || new == null) {
            mismatches.add("$where: old=${old.missing()}, new=${new.missing()}")
        } else {
            checkAnnotations(where, "annotations", old.annotations, new.annotations)
            checkTypeParameters(where, old.typeParameters, new.typeParameters)
            checkVisibility(where, old.visibility, new.visibility)
            checkContainingClass(where, old.containingClass, new.containingClass)
            checkValueParameters(where, old.valueParameters, new.valueParameters)
            checkBoolean(where, "stableParameterNames", old.hasStableParameterNames, new.hasStableParameterNames)
            checkBoolean(where, "isPrimary", old.isPrimary, new.isPrimary)
        }
    }

    fun checkClasses(where: String, old: CirClass?, new: CirClass?) {
        if (old == null && new == null)
            return

        if (old == null || new == null) {
            mismatches.add("$where: old=${old.missing()}, new=${new.missing()}")
        } else {
            checkAnnotations(where, "annotations", old.annotations, new.annotations)
            checkName(where, "name", old.name, new.name)
            checkTypeParameters(where, old.typeParameters, new.typeParameters)
            checkVisibility(where, old.visibility, new.visibility)
            checkModality(where, old.modality, new.modality)
            checkKind(where, old.kind, new.kind)
            checkName(where, "companion", old.companion, new.companion)
            checkBoolean(where, "isCompanion", old.isCompanion, new.isCompanion)
            checkBoolean(where, "isData", old.isData, new.isData)
            checkBoolean(where, "isInline", old.isInline, new.isInline)
            checkBoolean(where, "isInner", old.isInner, new.isInner)
            checkBoolean(where, "isExternal", old.isExternal, new.isExternal)
            checkTypes(where, "supertypes", old.supertypes.toList(), new.supertypes.toList())
        }
    }

    fun checkTypeAliases(where: String, old: CirTypeAlias?, new: CirTypeAlias?) {
        if (old == null && new == null)
            return

        if (old == null || new == null) {
            mismatches.add("$where: old=${old.missing()}, new=${new.missing()}")
        } else {
            checkAnnotations(where, "annotations", old.annotations, new.annotations)
            checkName(where, "name", old.name, new.name)
            checkTypeParameters(where, old.typeParameters, new.typeParameters)
            checkVisibility(where, old.visibility, new.visibility)
            checkType(where, "underlyingType", old.underlyingType, new.underlyingType)
            checkType(where, "expandedType", old.expandedType, new.expandedType)
        }
    }

    fun checkMembers(where: String, oldNode: CirNodeWithMembers<*, *>, newNode: CirNodeWithMembers<*, *>) {
        checkKeys(
            "$where > properties",
            oldNode.properties.keys,
            newNode.properties.keys
        ).forEach { (oldPropertyKey, newPropertyKey) ->
            val oldPropertyNode = oldNode.properties.getValue(oldPropertyKey)
            val newPropertyNode = newNode.properties.getValue(newPropertyKey)

            for (i in 0 until oldPropertyNode.targetDeclarations.size) {
                checkProperties(
                    "$where > properties > $oldPropertyKey[$i]",
                    oldPropertyNode.targetDeclarations[i],
                    newPropertyNode.targetDeclarations[i]
                )
            }
        }

        checkKeys(
            "$where > functions",
            oldNode.functions.keys,
            newNode.functions.keys
        ).forEach { (oldFunctionKey, newFunctionKey) ->
            val oldFunctionNode = oldNode.functions.getValue(oldFunctionKey)
            val newFunctionNode = newNode.functions.getValue(newFunctionKey)

            for (i in 0 until oldFunctionNode.targetDeclarations.size) {
                checkFunctions(
                    "$where > functions > $oldFunctionKey[$i]",
                    oldFunctionNode.targetDeclarations[i],
                    newFunctionNode.targetDeclarations[i]
                )
            }
        }

        if (oldNode is CirClassNode && newNode is CirClassNode) {
            checkKeys(
                "$where > constructors",
                oldNode.constructors.keys,
                newNode.constructors.keys
            ).forEach { (oldConstructorKey, newConstructorKey) ->
                val oldConstructorNode = oldNode.constructors.getValue(oldConstructorKey)
                val newConstructorNode = newNode.constructors.getValue(newConstructorKey)

                for (i in 0 until oldConstructorNode.targetDeclarations.size) {
                    checkConstructors(
                        "$where > constructors > $oldConstructorKey[$i]",
                        oldConstructorNode.targetDeclarations[i],
                        newConstructorNode.targetDeclarations[i]
                    )
                }
            }
        }

        checkKeys(
            "$where > classes",
            oldNode.classes.keys,
            newNode.classes.keys
        ).forEach { (className, _) ->
            val oldClassNode = oldNode.classes.getValue(className)
            val newClassNode = newNode.classes.getValue(className)

            for (i in 0 until oldClassNode.targetDeclarations.size) {
                checkClasses(
                    "$where > classes > $className[$i]",
                    oldClassNode.targetDeclarations[i],
                    newClassNode.targetDeclarations[i]
                )
            }

            checkMembers("$where > $className", oldClassNode, newClassNode)
        }
    }

    checkKeys(
        "modules",
        oldRootNode.modules.keys,
        newRootNode.modules.keys
    ).forEach { (moduleName, _) ->
        val oldModuleNode = oldRootNode.modules.getValue(moduleName)
        val newModuleNode = newRootNode.modules.getValue(moduleName)

        val shortModuleName = moduleName.toStrippedString().replace("org.jetbrains.kotlin.native.platform.", "")

        checkKeys(
            "$shortModuleName > packages",
            oldModuleNode.packages.keys,
            newModuleNode.packages.keys
        ).forEach { (packageName, _) ->
            val oldPackageNode = oldModuleNode.packages.getValue(packageName)
            val newPackageNode = newModuleNode.packages.getValue(packageName)

            val shortPackageName = if (packageName == CirPackageName.ROOT) "ROOT" else packageName.toString()

            checkMembers("$shortModuleName > $shortPackageName", oldPackageNode, newPackageNode)

            checkKeys(
                "$shortModuleName > $shortPackageName > TAs",
                oldPackageNode.typeAliases.keys,
                newPackageNode.typeAliases.keys
            ).forEach { (typeAliasName, _) ->
                val oldTypeAliasNode = oldPackageNode.typeAliases.getValue(typeAliasName)
                val newTypeAliasNode = newPackageNode.typeAliases.getValue(typeAliasName)

                for (i in 0 until oldTypeAliasNode.targetDeclarations.size) {
                    checkTypeAliases(
                        "$shortModuleName > $shortPackageName > TAs > $typeAliasName[$i]",
                        oldTypeAliasNode.targetDeclarations[i],
                        newTypeAliasNode.targetDeclarations[i]
                    )
                }
            }
        }
    }

    if (mismatches.isNotEmpty()) {
        val isTest = Throwable().stackTrace.any { it.className == "junit.framework.TestCase" }
        if (isTest) {
            error(
                buildString {
                    appendLine("${mismatches.size} mismatches found!")
                    mismatches.forEachIndexed { index, mismatch ->
                        appendLine("${index + 1}. $mismatch")
                    }
                }
            )
        } else {
            mismatches.forEach(System.err::println)
            error("${mismatches.size} mismatches found!")
        }
    }
}

private val KLIB_FRAGMENT_WRITE_STRATEGY = ChunkedKlibModuleFragmentWriteStrategy()
