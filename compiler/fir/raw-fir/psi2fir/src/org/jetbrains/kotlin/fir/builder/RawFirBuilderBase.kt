/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.builder

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

abstract class RawFirBuilderBase(
    session: FirSession,
    val mode: RawFirBuilderMode = RawFirBuilderMode.NORMAL,
    context: Context<PsiElement> = Context(),
) : BaseFirBuilder<PsiElement>(session, context) {

    protected val stubMode get() = mode == RawFirBuilderMode.STUBS

    override fun PsiElement.toFirSourceElement(kind: FirFakeSourceElementKind?): FirPsiSourceElement<*> {
        val actualKind = kind ?: this@RawFirBuilderBase.context.forcedElementSourceKind ?: FirRealSourceElementKind
        return this.toFirPsiSourceElement(actualKind)
    }

    override val PsiElement.elementType: IElementType
        get() = node.elementType

    override val PsiElement.asText: String
        get() = text

    override val PsiElement.unescapedValue: String
        get() = (this as KtEscapeStringTemplateEntry).unescapedValue

    override fun PsiElement.getChildNodeByType(type: IElementType): PsiElement? {
        return children.firstOrNull { it.node.elementType == type }
    }

    override fun PsiElement.getReferencedNameAsName(): Name {
        return (this as KtSimpleNameExpression).getReferencedNameAsName()
    }

    override fun PsiElement.getLabelName(): String? {
        return (this as? KtExpressionWithLabel)?.getLabelName()
    }

    override fun PsiElement.getExpressionInParentheses(): PsiElement? {
        return (this as KtParenthesizedExpression).expression
    }

    override fun PsiElement.getAnnotatedExpression(): PsiElement? {
        return (this as KtAnnotatedExpression).baseExpression
    }

    override fun PsiElement.getLabeledExpression(): PsiElement? {
        return (this as KtLabeledExpression).baseExpression
    }

    override val PsiElement?.receiverExpression: PsiElement?
        get() = (this as? KtQualifiedExpression)?.receiverExpression

    override val PsiElement?.selectorExpression: PsiElement?
        get() = (this as? KtQualifiedExpression)?.selectorExpression

    override val PsiElement?.arrayExpression: PsiElement?
        get() = (this as? KtArrayAccessExpression)?.arrayExpression

    override val PsiElement?.indexExpressions: List<PsiElement>?
        get() = (this as? KtArrayAccessExpression)?.indexExpressions


}
