/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir.low.level.api

import com.intellij.openapi.module.Module
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PackageScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.transformers.FirSealedClassInheritorsProcessor
import org.jetbrains.kotlin.fir.resolve.transformers.FirTransformerBasedResolveProcessor
import org.jetbrains.kotlin.fir.visitors.CompositeTransformResult
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitor
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.compose
import org.jetbrains.kotlin.idea.caches.project.ModuleSourceInfo
import org.jetbrains.kotlin.idea.caches.project.PlatformModuleInfo
import org.jetbrains.kotlin.idea.util.classIdIfNonLocal
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.resolve.jvm.KotlinJavaPsiFacade
import org.jetbrains.kotlin.types.typeUtil.closure


class FirIdeSealedHierarchyProcessor(
    session: FirSession,
    scopeSession: ScopeSession
) : FirTransformerBasedResolveProcessor(session, scopeSession) {

    override val transformer: FirTransformer<Nothing?> = SealedClassInheritorsTransformer()


    private class SealedClassInheritorsTransformer : FirTransformer<Nothing?>() {
        override fun <E : FirElement> transformElement(element: E, data: Nothing?): CompositeTransformResult<E> {
            throw IllegalStateException("Should not be there")
        }

        override fun transformFile(file: FirFile, data: Nothing?): CompositeTransformResult<FirDeclaration> {
            val sealedClassInheritorsMap = mutableMapOf<FirRegularClass, MutableList<ClassId>>()
            file.accept(SealedInheritorsCollector, sealedClassInheritorsMap)
            if (sealedClassInheritorsMap.isEmpty()) return file.compose()
            return file.transform(FirSealedClassInheritorsProcessor.InheritorsTransformer(sealedClassInheritorsMap), null)
        }
    }
//************************************
// TODO
//************************************

    private object SealedInheritorsCollector : FirDefaultVisitor<Unit, MutableMap<FirRegularClass, MutableList<ClassId>>>() {

        override fun visitElement(element: FirElement, data: MutableMap<FirRegularClass, MutableList<ClassId>>) {}

        override fun visitFile(file: FirFile, data: MutableMap<FirRegularClass, MutableList<ClassId>>) {
            file.declarations.forEach {
                it.accept(this, data)
            } // todo: why is visitRegularClass not called directly?
        }

        override fun visitRegularClass(regularClass: FirRegularClass, data: MutableMap<FirRegularClass, MutableList<ClassId>>) {
            if (!regularClass.isSealed) return

            val sealedKtClass = regularClass.psi as? KtClass ?: return

            val module = sealedKtClass.module ?: return
            val moduleInfo = regularClass.session.moduleInfo ?: return

            val modulesScope = moduleInfo.listCommonModulesIfAny().toMutableList()
                .apply { add(module) }
                .map { it.moduleScope }

            val mppAwareSearchScope = GlobalSearchScope.union(modulesScope)

            val containingPackage =
                regularClass.classId.packageFqName // todo they say local classes don't have package (can they be sealed?)

            val psiPackage = KotlinJavaPsiFacade.getInstance(sealedKtClass.project)
                .findPackage(containingPackage.asString(), GlobalSearchScope.moduleScope(module))
                ?: getPackageViaDirectoryService(sealedKtClass)
                ?: return

            val packageScope = PackageScope(psiPackage, false, false)

            val searchScope: SearchScope = mppAwareSearchScope.intersectWith(packageScope)

            val kotlinAsJavaSupport = KotlinAsJavaSupport.getInstance(sealedKtClass.project)
            val lightClass = sealedKtClass.toLightClass() ?: kotlinAsJavaSupport.getFakeLightClass(sealedKtClass)

            val searchParameters = ClassInheritorsSearch.SearchParameters(lightClass, searchScope, false, true, false)

            val query = ClassInheritorsSearch.search(searchParameters)
            val subclasses = query.mapNotNull { it -> (it as KtClass).classIdIfNonLocal() }
                .toMutableList()
            data[regularClass] = subclasses
        }
    }
}

val ModuleInfo.implementedDescriptors: List<ModuleInfo>
    get() {
        val moduleInfo = this
        if (moduleInfo is PlatformModuleInfo) return listOf(this)

        val moduleSourceInfo = moduleInfo as? ModuleSourceInfo ?: return emptyList()
        return moduleSourceInfo.expectedBy
    }

private fun ModuleInfo.listCommonModulesIfAny(): Collection<Module> {
    return implementedDescriptors.closure { it.implementedDescriptors }
        .mapNotNull { (it as? ModuleSourceInfo)?.module }
}

private fun getPackageViaDirectoryService(ktClass: KtClass): PsiPackage? {
    val directory = ktClass.containingFile.containingDirectory ?: return null
    return JavaDirectoryService.getInstance().getPackage(directory)
}
