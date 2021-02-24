/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.checkers.FirDeclarationInspector
import org.jetbrains.kotlin.fir.analysis.checkers.FirDeclarationPresenter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.analysis.diagnostics.reportOn
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.resolve.firProvider
import org.jetbrains.kotlin.fir.scopes.PACKAGE_MEMBER
import org.jetbrains.kotlin.fir.scopes.impl.FirPackageMemberScope
import org.jetbrains.kotlin.fir.symbols.AbstractFirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.SmartSet

object FirConflictsChecker : FirBasicDeclarationChecker() {

    private class DeclarationInspector : FirDeclarationInspector() {

        val declarationConflictingSymbols: HashMap<FirDeclaration, SmartSet<AbstractFirBasedSymbol<*>>> = hashMapOf()

        override fun collectNonFunctionDeclaration(key: String, declaration: FirDeclaration): MutableList<FirDeclaration> =
            super.collectNonFunctionDeclaration(key, declaration).also {
                collectLocalConflicts(declaration, it)
            }

        override fun collectFunction(key: String, declaration: FirSimpleFunction): MutableList<FirSimpleFunction> =
            super.collectFunction(key, declaration).also {
                collectLocalConflicts(declaration, it)
            }

        private fun collectLocalConflicts(declaration: FirDeclaration, conflicting: List<FirDeclaration>) {
            val localConflicts = SmartSet.create<AbstractFirBasedSymbol<*>>()
            for (otherDeclaration in conflicting) {
                if (otherDeclaration is FirSymbolOwner<*>) {
                    if (otherDeclaration != declaration && declaration is FirSymbolOwner<*> &&
                        !isExpectAndActual(declaration, otherDeclaration)
                    ) {
                        localConflicts.add(otherDeclaration.symbol)
                        declarationConflictingSymbols.getOrPut(otherDeclaration) { SmartSet.create() }.add(declaration.symbol)
                    }
                }
            }
            declarationConflictingSymbols[declaration] = localConflicts
        }

        private fun isExpectAndActual(declaration1: FirDeclaration, declaration2: FirDeclaration): Boolean {
            if (declaration1 !is FirMemberDeclaration) return false
            if (declaration2 !is FirMemberDeclaration) return false
            return (declaration1.status.isExpect && declaration2.status.isActual) ||
                    (declaration1.status.isActual && declaration2.status.isExpect)
        }

        private fun areCompatibleMainFunctions(
            declaration1: FirDeclaration, file1: FirFile, declaration2: FirDeclaration, file2: FirFile?
        ): Boolean {
            // TODO: proper main function detector
            if (declaration1 !is FirSimpleFunction || declaration2 !is FirSimpleFunction) return false
            if (declaration1.name.asString() != "main" || declaration2.name.asString() != "main") return false
            return file1 != file2
        }

        private fun collectExternalConflict(
            declaration: FirDeclaration,
            declarationPresentation: String,
            containingFile: FirFile,
            conflictingSymbol: AbstractFirBasedSymbol<*>,
            session: FirSession,
            visibilityChecker: FirVisibilityChecker
        ) {
            val conflicting = conflictingSymbol.fir as? FirDeclaration ?: return
            if (conflicting == declaration || presenter.represent(conflicting) != declarationPresentation) return
            val conflictingFile = when (conflictingSymbol) {
                is FirClassLikeSymbol<*> -> session.firProvider.getFirClassifierContainerFileIfAny(conflictingSymbol)
                is FirCallableSymbol<*> -> session.firProvider.getFirCallableContainerFile(conflictingSymbol)
                else -> null
            }
            if (containingFile == conflictingFile) return // TODO: rewrite local decls checker to the same logic and then remove the check
            if (areCompatibleMainFunctions(declaration, containingFile, conflicting, conflictingFile)) return
            if (isExpectAndActual(declaration, conflicting)) return
            if (conflicting is FirMemberDeclaration && !(conflicting is FirSymbolOwner<*> &&
                        visibilityChecker.isVisible(conflicting, session, containingFile, emptyList(), null))
            ) {
                return
            }
            declarationConflictingSymbols.getOrPut(declaration) { SmartSet.create() }.add(conflictingSymbol)
        }

        fun collectWithExternalConflicts(
            declaration: FirDeclaration,
            containingFile: FirFile,
            session: FirSession,
            packageMemberScope: FirPackageMemberScope
        ) {
            collect(declaration)
            val visibilityChecker = session.visibilityChecker
            var declarationName: Name? = null
            val declarationPresentation = presenter.represent(declaration) ?: return

            when (declaration) {
                is FirSimpleFunction -> {
                    declarationName = declaration.name
                    if (!declarationName.isSpecial) {
                        packageMemberScope.processFunctionsByName(declarationName) {
                            collectExternalConflict(declaration, declarationPresentation, containingFile, it, session, visibilityChecker)
                        }
                    }
                }
                is FirVariable<*> -> {
                    declarationName = declaration.name
                    if (!declarationName.isSpecial) {
                        packageMemberScope.processPropertiesByName(declarationName) {
                            collectExternalConflict(declaration, declarationPresentation, containingFile, it, session, visibilityChecker)
                        }
                    }
                }
                is FirRegularClass -> {
                    declarationName = declaration.name

                    class ClassConflictsCheckingVisitor(val className: Name) : FirVisitorVoid() {
                        override fun visitElement(element: FirElement) {}

                        override fun visitConstructor(constructor: FirConstructor) {
                            packageMemberScope.processFunctionsByName(className) {
                                collectExternalConflict(
                                    constructor, presenter.represent(constructor, declaration), containingFile, it, session, visibilityChecker
                                )
                            }
                        }

                        override fun visitDeclarationStatus(declarationStatus: FirDeclarationStatus) {}
                        override fun visitRegularClass(regularClass: FirRegularClass) {}
                        override fun visitProperty(property: FirProperty) {}
                        override fun visitSimpleFunction(simpleFunction: FirSimpleFunction) {}
                    }

                    if (!declarationName.isSpecial) {
                        packageMemberScope.processClassifiersByNameWithSubstitution(declarationName) { symbol, _ ->
                            collectExternalConflict(
                                declaration, declarationPresentation, containingFile, symbol, session, visibilityChecker
                            )
                        }
                        declaration.acceptChildren(ClassConflictsCheckingVisitor(declarationName))
                    }
                }
                is FirTypeAlias -> {
                    declarationName = declaration.name
                    if (!declarationName.isSpecial) {
                        packageMemberScope.processClassifiersByNameWithSubstitution(declarationName) { symbol, _ ->
                            collectExternalConflict(
                                declaration, declarationPresentation, containingFile, symbol, session, visibilityChecker
                            )
                        }
                    }
                }
            }
            if (declarationName != null) {
                session.firLookupTracker?.recordLookup(
                    declarationName, declaration.source, containingFile.source, containingFile.packageFqName.asString()
                )
            }
        }
    }

    override fun check(declaration: FirDeclaration, context: CheckerContext, reporter: DiagnosticReporter) {
        val inspector = DeclarationInspector()

        when (declaration) {
            is FirFile -> checkFile(declaration, inspector, context)
            is FirRegularClass -> checkRegularClass(declaration, inspector)
            else -> return
        }

        inspector.declarationConflictingSymbols.forEach { (declaration, symbols) ->
            when {
                symbols.isEmpty() -> {}
                declaration is FirSimpleFunction -> {
                    reporter.reportOn(declaration.source, FirErrors.CONFLICTING_OVERLOADS, symbols, context)
                }
                else -> {
                    reporter.reportOn(declaration.source, FirErrors.REDECLARATION, symbols, context)
                }
            }
        }
    }

    private fun checkFile(file: FirFile, inspector: DeclarationInspector, context: CheckerContext) {
        val packageMemberScope: FirPackageMemberScope = context.sessionHolder.scopeSession.getOrBuild(file.packageFqName, PACKAGE_MEMBER) {
            FirPackageMemberScope(file.packageFqName, context.sessionHolder.session)
        }
        for (topLevelDeclaration in file.declarations) {
            inspector.collectWithExternalConflicts(topLevelDeclaration, file, context.session, packageMemberScope)
        }
    }

    private fun checkRegularClass(declaration: FirRegularClass, inspector: DeclarationInspector) {
        for (it in declaration.declarations) {
            inspector.collect(it)
        }
    }
}

private fun FirDeclarationPresenter.represent(declaration: FirDeclaration): String? =
    when (declaration) {
        is FirSimpleFunction -> represent(declaration)
        is FirRegularClass -> represent(declaration)
        is FirTypeAlias -> represent(declaration)
        is FirProperty -> represent(declaration)
        else -> null
    }
