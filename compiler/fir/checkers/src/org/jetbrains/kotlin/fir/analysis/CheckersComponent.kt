/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSessionComponent
import org.jetbrains.kotlin.fir.NoMutableState
import org.jetbrains.kotlin.fir.SessionConfiguration
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.ComposedDeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ComposedExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

@RequiresOptIn
annotation class CheckersComponentInternal

abstract class CheckersComponent {
    abstract val declarationCheckers: DeclarationCheckers
    abstract val expressionCheckers: ExpressionCheckers

    @SessionConfiguration
    @OptIn(CheckersComponentInternal::class)
    abstract fun register(checkers: DeclarationCheckers)

    @SessionConfiguration
    @OptIn(CheckersComponentInternal::class)
    abstract fun register(checkers: ExpressionCheckers)

    @SessionConfiguration
    abstract fun register(checkers: FirAdditionalCheckersExtension)
}

@NoMutableState
class CheckersComponentImpl : FirSessionComponent, CheckersComponent() {
    override val declarationCheckers: DeclarationCheckers get() = _declarationCheckers
    private val _declarationCheckers = ComposedDeclarationCheckers()

    override val expressionCheckers: ExpressionCheckers get() = _expressionCheckers
    private val _expressionCheckers = ComposedExpressionCheckers()

    @SessionConfiguration
    @OptIn(CheckersComponentInternal::class)
    override fun register(checkers: DeclarationCheckers) {
        _declarationCheckers.register(checkers)
    }

    @SessionConfiguration
    @OptIn(CheckersComponentInternal::class)
    override fun register(checkers: ExpressionCheckers) {
        _expressionCheckers.register(checkers)
    }

    @SessionConfiguration
    override fun register(checkers: FirAdditionalCheckersExtension) {
        register(checkers.declarationCheckers)
        register(checkers.expressionCheckers)
    }
}

val FirSession.checkersComponent: CheckersComponent by FirSession.sessionComponentAccessor()
