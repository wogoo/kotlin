/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer.mergedtree

import com.intellij.util.containers.FactoryMap
import gnu.trove.TIntObjectHashMap
import kotlinx.metadata.*
import kotlinx.metadata.klib.KlibModuleMetadata
import kotlinx.metadata.klib.fqName
import kotlinx.metadata.klib.klibEnumEntries
import org.jetbrains.kotlin.descriptors.commonizer.CommonizerParameters
import org.jetbrains.kotlin.descriptors.commonizer.LeafCommonizerTarget
import org.jetbrains.kotlin.descriptors.commonizer.ModulesProvider.ModuleInfo
import org.jetbrains.kotlin.descriptors.commonizer.TargetProvider
import org.jetbrains.kotlin.descriptors.commonizer.cir.CirEntityId
import org.jetbrains.kotlin.descriptors.commonizer.cir.CirName
import org.jetbrains.kotlin.descriptors.commonizer.cir.CirPackageName
import org.jetbrains.kotlin.descriptors.commonizer.cir.factory.*
import org.jetbrains.kotlin.descriptors.commonizer.mergedtree.ClassesToProcess.ClassEntry
import org.jetbrains.kotlin.descriptors.commonizer.metadata.utils.SerializedMetadataLibraryProvider
import org.jetbrains.kotlin.descriptors.commonizer.prettyName
import org.jetbrains.kotlin.descriptors.commonizer.utils.*
import org.jetbrains.kotlin.storage.StorageManager

class CirTreeBuilder(
    private val storageManager: StorageManager,
    private val classifiers: CirKnownClassifiers,
    private val parameters: CommonizerParameters
) {
    class CirTreeBuildingResult(
        val root: CirRootNode,
        val missingModuleInfos: Map<LeafCommonizerTarget, Collection<ModuleInfo>>
    )

    private class CirTreeBuildingContext(
        val targetIndex: Int,
        val typeResolver: CirTypeResolver
    )

    private val leafTargetsSize = parameters.targetProviders.size

    fun build(): CirTreeBuildingResult {
        val result = processRoot()
        System.gc()
        return result
    }

    private fun processRoot(): CirTreeBuildingResult {
        val rootNode: CirRootNode = buildRootNode(storageManager, leafTargetsSize)

        // remember any exported forward declarations from common fragments of dependee modules
        parameters.dependencyModulesProvider?.loadModuleInfos()?.forEach(::processCInteropModuleAttributes)

        val commonModuleNames = parameters.getCommonModuleNames()
        val missingModuleInfosByTargets = mutableMapOf<LeafCommonizerTarget, Collection<ModuleInfo>>()

        parameters.targetProviders.forEachIndexed { targetIndex, targetProvider ->
            val allModuleInfos = targetProvider.modulesProvider.loadModuleInfos()

            val (commonModuleInfos, missingModuleInfos) = allModuleInfos.partition { it.name in commonModuleNames }
            processTarget(targetIndex, rootNode, targetProvider, commonModuleInfos)

            missingModuleInfosByTargets[targetProvider.target] = missingModuleInfos

            parameters.progressLogger?.invoke("Loaded declarations for ${targetProvider.target.prettyName}")
            System.gc()
        }

        return CirTreeBuildingResult(
            root = rootNode,
            missingModuleInfos = missingModuleInfosByTargets
        )
    }

    private fun processTarget(
        targetIndex: Int,
        rootNode: CirRootNode,
        targetProvider: TargetProvider,
        commonModuleInfos: Collection<ModuleInfo>
    ) {
        rootNode.targetDeclarations[targetIndex] = CirRootFactory.create(targetProvider.target)

        if (commonModuleInfos.isNotEmpty()) {
            val context = CirTreeBuildingContext(
                targetIndex = targetIndex,
                // all classifiers "visible" for the target:
                typeResolver = CirTypeResolver.create(
                    providedClassifiers = CirProvidedClassifiers.of(
                        classifiers.commonDependencies,
                        CirProvidedClassifiers.by(targetProvider.dependencyModulesProvider),
                        CirProvidedClassifiers.by(targetProvider.modulesProvider)
                    )
                )
            )

            commonModuleInfos.forEach { moduleInfo ->
                val metadata = targetProvider.modulesProvider.loadModuleMetadata(moduleInfo.name)
                val module = KlibModuleMetadata.read(SerializedMetadataLibraryProvider(metadata))
                processModule(context, rootNode, moduleInfo, module)
            }
        }
    }

    private fun processModule(
        context: CirTreeBuildingContext,
        rootNode: CirRootNode,
        moduleInfo: ModuleInfo,
        module: KlibModuleMetadata
    ) {
        processCInteropModuleAttributes(moduleInfo)

        val moduleName: CirName = CirName.create(module.name)
        val moduleNode: CirModuleNode = rootNode.modules.getOrPut(moduleName) {
            buildModuleNode(storageManager, leafTargetsSize)
        }
        moduleNode.targetDeclarations[context.targetIndex] = CirModuleFactory.create(moduleName)

        val groupedFragments: Map<CirPackageName, Collection<KmModuleFragment>> = module.fragments.foldToMap { fragment ->
            fragment.fqName?.let(CirPackageName::create) ?: error("A fragment without FQ name in module $moduleName: $fragment")
        }

        groupedFragments.forEach { (packageName, fragments) ->
            processFragments(context, moduleNode, fragments, packageName)
        }
    }

    private fun processFragments(
        context: CirTreeBuildingContext,
        moduleNode: CirModuleNode,
        fragments: Collection<KmModuleFragment>,
        packageName: CirPackageName
    ) {
        val packageNode: CirPackageNode = moduleNode.packages.getOrPut(packageName) {
            buildPackageNode(storageManager, leafTargetsSize)
        }
        packageNode.targetDeclarations[context.targetIndex] = CirPackageFactory.create(packageName)

        val classesToProcess = ClassesToProcess()
        fragments.forEach { fragment ->
            classesToProcess.addClassesFromFragment(fragment)

            fragment.pkg?.let { pkg ->
                pkg.properties.forEach { property ->
                    processProperty(context, packageNode, property, TypeParameterResolver.create(property))
                }
                pkg.functions.forEach { function ->
                    processFunction(context, packageNode, function, TypeParameterResolver.create(function))
                }
                pkg.typeAliases.forEach { typeAlias -> processTypeAlias(context, packageNode, typeAlias) }
            }
        }

        classesToProcess.forEachClassInScope(parentClassId = null) { classEntry ->
            processClass(context, packageNode, classEntry, classesToProcess, TypeParameterResolver.create(classEntry))
        }
    }

    private fun processProperty(
        context: CirTreeBuildingContext,
        ownerNode: CirNodeWithMembers<*, *>,
        property: KmProperty,
        typeParameterResolver: TypeParameterResolver
    ) {
        if (property.isFakeOverride())
            return

        val maybeClassOwnerNode: CirClassNode? = ownerNode as? CirClassNode

        val approximationKey = PropertyApproximationKey(property, typeParameterResolver)
        val propertyNode: CirPropertyNode = ownerNode.properties.getOrPut(approximationKey) {
            buildPropertyNode(storageManager, leafTargetsSize, classifiers, maybeClassOwnerNode?.commonDeclaration)
        }
        propertyNode.targetDeclarations[context.targetIndex] = CirPropertyFactory.create(
            name = approximationKey.name,
            source = property,
            containingClass = maybeClassOwnerNode?.targetDeclarations?.get(context.targetIndex),
            typeResolver = context.typeResolver
        )
    }

    private fun processFunction(
        context: CirTreeBuildingContext,
        ownerNode: CirNodeWithMembers<*, *>,
        function: KmFunction,
        typeParameterResolver: TypeParameterResolver
    ) {
        if (function.isFakeOverride()
            || function.isKniBridgeFunction()
            || function.isTopLevelDeprecatedFunction(isTopLevel = ownerNode !is CirClassNode)
        ) {
            return
        }

        val maybeClassOwnerNode: CirClassNode? = ownerNode as? CirClassNode

        val approximationKey = FunctionApproximationKey(function, typeParameterResolver)
        val functionNode: CirFunctionNode = ownerNode.functions.getOrPut(approximationKey) {
            buildFunctionNode(storageManager, leafTargetsSize, classifiers, maybeClassOwnerNode?.commonDeclaration)
        }
        functionNode.targetDeclarations[context.targetIndex] = CirFunctionFactory.create(
            name = approximationKey.name,
            source = function,
            containingClass = maybeClassOwnerNode?.targetDeclarations?.get(context.targetIndex),
            typeResolver = context.typeResolver
        )
    }

    private fun processClass(
        context: CirTreeBuildingContext,
        ownerNode: CirNodeWithMembers<*, *>,
        classEntry: ClassEntry,
        classesToProcess: ClassesToProcess,
        typeParameterResolver: TypeParameterResolver
    ) {
        val classId = classEntry.classId
        val className = classId.relativeNameSegments.last()

        val maybeClassOwnerNode: CirClassNode? = ownerNode as? CirClassNode
        val classNode: CirClassNode = ownerNode.classes.getOrPut(className) {
            buildClassNode(storageManager, leafTargetsSize, classifiers, maybeClassOwnerNode?.commonDeclaration, classId)
        }

        val clazz: KmClass?
        val isEnumEntry: Boolean

        classNode.targetDeclarations[context.targetIndex] = when (classEntry) {
            is ClassEntry.RegularClassEntry -> {
                clazz = classEntry.clazz
                isEnumEntry = Flag.Class.IS_ENUM_ENTRY(clazz.flags)

                CirClassFactory.create(className, clazz, context.typeResolver)
            }
            is ClassEntry.EnumEntry -> {
                clazz = null
                isEnumEntry = true

                CirClassFactory.createDefaultEnumEntry(
                    name = className,
                    annotations = classEntry.annotations,
                    enumClassId = classEntry.enumClassId,
                    enumClass = classEntry.enumClass,
                    typeResolver = context.typeResolver
                )
            }
        }

        if (!isEnumEntry) {
            clazz?.constructors?.forEach { constructor ->
                processClassConstructor(context, classNode, constructor, typeParameterResolver)
            }
        }

        clazz?.properties?.forEach { property ->
            processProperty(context, classNode, property, typeParameterResolver.create(property))
        }
        clazz?.functions?.forEach { function ->
            processFunction(context, classNode, function, typeParameterResolver.create(function))
        }

        classesToProcess.forEachClassInScope(parentClassId = classId) { nestedClassEntry ->
            processClass(context, classNode, nestedClassEntry, classesToProcess, typeParameterResolver.create(nestedClassEntry))
        }
    }

    private fun processClassConstructor(
        context: CirTreeBuildingContext,
        classNode: CirClassNode,
        constructor: KmConstructor,
        typeParameterResolver: TypeParameterResolver
    ) {
        val approximationKey = ConstructorApproximationKey(constructor, typeParameterResolver)
        val constructorNode: CirClassConstructorNode = classNode.constructors.getOrPut(approximationKey) {
            buildClassConstructorNode(storageManager, leafTargetsSize, classifiers, classNode.commonDeclaration)
        }
        constructorNode.targetDeclarations[context.targetIndex] = CirClassConstructorFactory.create(
            source = constructor,
            containingClass = classNode.targetDeclarations[context.targetIndex]!!,
            typeResolver = context.typeResolver
        )
    }

    private fun processTypeAlias(
        context: CirTreeBuildingContext,
        packageNode: CirPackageNode,
        typeAliasMetadata: KmTypeAlias
    ) {
        val typeAliasName = CirName.create(typeAliasMetadata.name)
        val typeAliasId = CirEntityId.create(packageNode.packageName, typeAliasName)

        val typeAliasNode: CirTypeAliasNode = packageNode.typeAliases.getOrPut(typeAliasName) {
            buildTypeAliasNode(storageManager, leafTargetsSize, classifiers, typeAliasId)
        }
        typeAliasNode.targetDeclarations[context.targetIndex] = CirTypeAliasFactory.create(
            name = typeAliasName,
            source = typeAliasMetadata,
            typeResolver = context.typeResolver
        )
    }

    private fun processCInteropModuleAttributes(moduleInfo: ModuleInfo) {
        val cInteropAttributes = moduleInfo.cInteropAttributes ?: return
        val exportForwardDeclarations = cInteropAttributes.exportForwardDeclarations.takeIf { it.isNotEmpty() } ?: return

        exportForwardDeclarations.forEach { classFqName ->
            // Class has synthetic package FQ name (cnames/objcnames). Need to transfer it to the main package.
            val packageName = CirPackageName.create(classFqName.substringBeforeLast('.', missingDelimiterValue = ""))
            val className = CirName.create(classFqName.substringAfterLast('.'))

            classifiers.forwardDeclarations.addExportedForwardDeclaration(CirEntityId.create(packageName, className))
        }
    }
}

private class TypeParameterResolverImpl private constructor(
    private val parent: TypeParameterResolver?,
    private val typeParameters: TIntObjectHashMap<KmTypeParameter>
) : TypeParameterResolver {
    override fun resolveTypeParameter(id: Int): KmTypeParameter? =
        typeParameters.get(id) ?: parent?.resolveTypeParameter(id)

    companion object {
        @Suppress("NOTHING_TO_INLINE")
        inline fun create(parent: TypeParameterResolver?, typeParameters: List<KmTypeParameter>): TypeParameterResolver =
            if (typeParameters.isEmpty()) {
                if (parent == null || parent === TypeParameterResolver.EMPTY)
                    TypeParameterResolver.EMPTY
                else
                    parent
            } else
                TypeParameterResolverImpl(parent, typeParameters.groupById())

        @Suppress("NOTHING_TO_INLINE")
        private inline fun List<KmTypeParameter>.groupById(): TIntObjectHashMap<KmTypeParameter> =
            TIntObjectHashMap<KmTypeParameter>(size * 2).also { result ->
                forEach { typeParameter -> result.put(typeParameter.id, typeParameter) }
            }
    }
}

private fun TypeParameterResolver.Companion.create(topLevelClassEntry: ClassEntry): TypeParameterResolver = when (topLevelClassEntry) {
    is ClassEntry.RegularClassEntry -> TypeParameterResolverImpl.create(null, topLevelClassEntry.clazz.typeParameters)
    is ClassEntry.EnumEntry -> EMPTY
}

private fun TypeParameterResolver.create(nestedClassEntry: ClassEntry): TypeParameterResolver = when (nestedClassEntry) {
    is ClassEntry.RegularClassEntry -> TypeParameterResolverImpl.create(this, nestedClassEntry.clazz.typeParameters)
    is ClassEntry.EnumEntry -> this
}

private fun TypeParameterResolver.Companion.create(topLevelFunction: KmFunction): TypeParameterResolver =
    TypeParameterResolverImpl.create(null, topLevelFunction.typeParameters)

private fun TypeParameterResolver.create(nestedFunction: KmFunction): TypeParameterResolver =
    TypeParameterResolverImpl.create(this, nestedFunction.typeParameters)

private fun TypeParameterResolver.Companion.create(topLevelProperty: KmProperty): TypeParameterResolver =
    TypeParameterResolverImpl.create(null, topLevelProperty.typeParameters)

private fun TypeParameterResolver.create(nestedProperty: KmProperty): TypeParameterResolver =
    TypeParameterResolverImpl.create(this, nestedProperty.typeParameters)

private class ClassesToProcess {
    sealed class ClassEntry {
        abstract val classId: CirEntityId

        data class RegularClassEntry(
            override val classId: CirEntityId,
            val clazz: KmClass
        ) : ClassEntry()

        data class EnumEntry(
            override val classId: CirEntityId,
            val annotations: List<KmAnnotation>,
            val enumClassId: CirEntityId,
            val enumClass: KmClass
        ) : ClassEntry()
    }

    // key = parent class ID (or kotlin/Nothing for top-level classes)
    // value = classes under this parent class (MutableList to preserve order of classes)
    private val groupedByParentClassId = FactoryMap.create<CirEntityId, MutableList<ClassEntry>> { ArrayList() }

    fun addClassesFromFragment(fragment: KmModuleFragment) {
        val klibEnumEntries = LinkedHashMap<CirEntityId, ClassEntry.EnumEntry>() // linked hash map to preserve order
        val regularClassIds = HashSet<CirEntityId>()

        fragment.classes.forEach { clazz ->
            val classId: CirEntityId = CirEntityId.create(clazz.name)
            val parentClassId: CirEntityId = classId.getParentEntityId() ?: NOTHING_CLASS_ID

            if (Flag.Class.IS_ENUM_CLASS(clazz.flags)) {
                clazz.klibEnumEntries.forEach { entry ->
                    val enumEntryId = classId.createNestedEntityId(CirName.create(entry.name))
                    klibEnumEntries[enumEntryId] = ClassEntry.EnumEntry(enumEntryId, entry.annotations, classId, clazz)
                }
            }

            groupedByParentClassId.getValue(parentClassId) += ClassEntry.RegularClassEntry(classId, clazz)
            regularClassIds += classId
        }

        // add enum entries that are not stored in module as KmClass records
        klibEnumEntries.forEach { (enumEntryId, enumEntry) ->
            if (enumEntryId !in regularClassIds) {
                groupedByParentClassId.getValue(enumEntry.enumClassId) += enumEntry
            }
        }
    }

    fun forEachClassInScope(parentClassId: CirEntityId?, block: (ClassEntry) -> Unit) {
        groupedByParentClassId[parentClassId ?: NOTHING_CLASS_ID]?.forEach { classEntry -> block(classEntry) }
    }
}
