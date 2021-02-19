/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.compiler

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PackageScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.ClassInheritorsSearch.SearchParameters
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService.CapabilitiesProvider
import org.jetbrains.kotlin.codegen.JvmCodegenUtil
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleCapability
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.containingPackage
import org.jetbrains.kotlin.idea.caches.project.implementedDescriptors
import org.jetbrains.kotlin.idea.caches.resolve.IdeaResolverForProject.Companion.PLATFORM_ANALYSIS_SETTINGS
import org.jetbrains.kotlin.idea.caches.resolve.PlatformAnalysisSettings
import org.jetbrains.kotlin.idea.caches.resolve.PlatformAnalysisSettingsImpl
import org.jetbrains.kotlin.idea.caches.resolve.util.resolveToDescriptor
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.resolve.SealedClassInheritorsProvider
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.jvm.KotlinJavaPsiFacade
import org.jetbrains.kotlin.types.typeUtil.closure

object IdeSealedClassInheritorsProvider : SealedClassInheritorsProvider() {

    override fun computeSealedSubclasses(
        sealedClass: ClassDescriptor,
        allowSealedInheritorsInDifferentFilesOfSamePackage: Boolean
    ): Collection<ClassDescriptor> {

        val sealedKtClass = sealedClass.findPsi() as? KtClass ?: return emptyList()
        val project = sealedKtClass.project
        val moduleDescriptor = sealedClass.module

        val searchScope: SearchScope = if (allowSealedInheritorsInDifferentFilesOfSamePackage) {
            val module = sealedKtClass.module ?: return emptyList()
            val moduleManager = ModuleManager.getInstance(project)

            val modulesScope = moduleDescriptor.listCommonModulesIfAny().toMutableList()
                .apply { add(moduleDescriptor) }
                .mapNotNull { moduleManager.findModuleByName(JvmCodegenUtil.getModuleName(it))?.moduleScope }

            val mppAwareSearchScope = GlobalSearchScope.union(modulesScope)

            val containingPackage = sealedClass.containingPackage() ?: return emptyList()
            val psiPackage = KotlinJavaPsiFacade.getInstance(project)
                .findPackage(containingPackage.asString(), GlobalSearchScope.moduleScope(module))
                ?: getPackageViaDirectoryService(sealedKtClass)
                ?: return emptyList()
            val packageScope = PackageScope(psiPackage, false, false)

            mppAwareSearchScope.intersectWith(packageScope)
        } else {
            GlobalSearchScope.fileScope(sealedKtClass.containingFile) // Kotlin version prior to 1.5
        }

        val kotlinAsJavaSupport = KotlinAsJavaSupport.getInstance(project)
        val lightClass = sealedKtClass.toLightClass() ?: kotlinAsJavaSupport.getFakeLightClass(sealedKtClass)
        val searchParameters = SearchParameters(lightClass, searchScope, false, true, false)

        val resolutionFacade = getResolutionFacade(moduleDescriptor, project) ?: return emptyList()

        return ClassInheritorsSearch.search(searchParameters)
            .mapNotNull { it.resolveToDescriptor(resolutionFacade) }
            .sortedBy(ClassDescriptor::getName) // order needs to be stable (at least for tests)
    }

    private fun ModuleDescriptor.listCommonModulesIfAny(): Collection<ModuleDescriptor> {
        return implementedDescriptors.closure { it.implementedDescriptors }
    }

    private fun getPackageViaDirectoryService(ktClass: KtClass): PsiPackage? {
        val directory = ktClass.containingFile.containingDirectory ?: return null
        return JavaDirectoryService.getInstance().getPackage(directory)
    }
}

private fun getResolutionFacade(moduleDescriptor: ModuleDescriptor, project: Project): ResolutionFacade? {
    val analysisSettings = moduleDescriptor.getCapability(PLATFORM_ANALYSIS_SETTINGS) ?: return null
    val moduleInfo = moduleDescriptor.getCapability(ModuleInfo.Capability) ?: return null
    return KotlinCacheService.getInstance(project).getResolutionFacadeByModuleInfo(moduleInfo, createProvider(analysisSettings))
}

private fun createProvider(settings: PlatformAnalysisSettings): CapabilitiesProvider {
    return object : CapabilitiesProvider {
        @Suppress("UNCHECKED_CAST")
        override fun <T> getCapability(capability: ModuleCapability<T>): T? {
            if (capability == PLATFORM_ANALYSIS_SETTINGS) return settings as T
            return null
        }
    }
}