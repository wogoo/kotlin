/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.builder

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.builder.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.builder.*
import org.jetbrains.kotlin.fir.references.builder.*
import org.jetbrains.kotlin.fir.scopes.FirScopeProvider
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.builder.*
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.runIf

fun interface PsiToFirElementMapper {
    fun mapToFirElement(ktElement: KtElement): FirElement?
}

private interface RawFirContextInterceptor {
    fun intercept(context: Context<PsiElement>, element: PsiElement)
    fun allowToVisit(psiElement: PsiElement): Boolean
}

/**
 * Checks if fragment expression is valid for FIR fragment building
 */
fun isAcceptableTarget(psiElement: PsiElement): Boolean =
    when (psiElement) {
        is KtFile,
        is KtClassOrObject,
        is KtTypeAlias,
        is KtNamedFunction,
        is KtLambdaExpression,
        is KtAnonymousInitializer,
        is KtProperty,
        is KtTypeReference,
        is KtAnnotationEntry,
        is KtTypeParameter,
        is KtTypeProjection,
        is KtParameter,
        is KtSimpleNameExpression,
        is KtConstantExpression,
        is KtBlockExpression,
        is KtStringTemplateExpression,
        is KtReturnExpression,
        is KtTryExpression,
        is KtIfExpression,
        is KtWhenExpression,
        is KtDoWhileExpression,
        is KtWhileExpression,
        is KtForExpression,
        is KtBreakExpression,
        is KtContinueExpression,
        is KtBinaryExpression,
        is KtBinaryExpressionWithTypeRHS,
        is KtIsExpression,
        is KtUnaryExpression,
        is KtCallExpression,
        is KtArrayAccessExpression,
        is KtQualifiedExpression,
        is KtThisExpression,
        is KtSuperExpression,
        is KtParenthesizedExpression,
        is KtLabeledExpression,
        is KtAnnotatedExpression,
        is KtThrowExpression,
        is KtDestructuringDeclaration,
        is KtClassLiteralExpression,
        is KtCallableReferenceExpression,
        is KtCollectionLiteralExpression -> true
        else -> false
    }

/**
 * Build FIR node from fragment of PSI node (only for fragments that valid for that @see isAcceptableTarget)
 * @param session Fir session for FIR context
 * @param baseScopeProvider Fir scope provider that delivers a scopes
 * @param psiToFirElementMapper Mapper from PSI to FIR node of the FirFile that is the context of fragment
 * @param targetExpression PSI node fragment to translate into the FIR
 * @param anchorExpression PSI node that points to the place of code where the fragment should be translated
 * @param mode builder mode (should not be RawFirBuilderMode.LAZY_BODIES)
 * @param moveAnchorToAcceptable elevate the anchor node on the acceptable node (nearest parent node where target node can be translated)
 */
fun tryBuildFirFragment(
    session: FirSession,
    baseScopeProvider: FirScopeProvider,
    psiToFirElementMapper: PsiToFirElementMapper,
    targetExpression: KtExpression,
    anchorExpression: KtExpression,
    mode: RawFirBuilderMode = RawFirBuilderMode.NORMAL,
    moveAnchorToAcceptable: Boolean = true
): FirElement? {

    if (!isAcceptableTarget(targetExpression)) return null

    val patchedAnchorExpression =
        if (moveAnchorToAcceptable) PsiTreeUtil.findFirstParent(anchorExpression, false, ::isAcceptableTarget) as KtElement
        else anchorExpression

    val interceptor = object : RawFirContextInterceptor {

        var result: FirElement? = null

        override fun intercept(context: Context<PsiElement>, element: PsiElement) {
            if (element != patchedAnchorExpression) return
            if (result != null) return

            val fragmentBuilder = RawFirBuilder(session, baseScopeProvider, mode, context)
            result = fragmentBuilder.buildFirElement(targetExpression)
        }

        override fun allowToVisit(psiElement: PsiElement): Boolean =
            result == null && psiElement.isAncestor(patchedAnchorExpression, strict = false)
    }

    val contextBuilder = RawFirFragmentBuilder(
        session,
        mode,
        psiToFirElementMapper,
        interceptor
    )

    contextBuilder.execute(anchorExpression.containingKtFile)

    return interceptor.result
}


private class RawFirFragmentBuilder(
    session: FirSession,
    mode: RawFirBuilderMode,
    private val psiToFirElementMapper: PsiToFirElementMapper,
    private val contextInterceptor: RawFirContextInterceptor,
) : RawFirBuilderBase(session, mode, Context()) {

    fun execute(file: KtFile) {
        require(mode != RawFirBuilderMode.LAZY_BODIES) { "Fragment builder does not support LazyBodies mode" }
        file.accept(Visitor(), Unit)
    }

    private inline fun <reified T> KtElement.withFir(body: T.() -> Unit) where T : FirElement {
        val firMappedElement = psiToFirElementMapper.mapToFirElement(this) as? T ?: return
        if (firMappedElement.source.psi == this) {
            firMappedElement.body()
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun <T> T.intercept() where T : KtElement =
        contextInterceptor.intercept(this@RawFirFragmentBuilder.context, this)

    override fun PsiElement.toFirSourceElement(kind: FirFakeSourceElementKind?): FirPsiSourceElement<*> {
        val actualKind = kind ?: this@RawFirFragmentBuilder.context.forcedElementSourceKind ?: FirRealSourceElementKind
        return this.toFirPsiSourceElement(actualKind)
    }


    private inner class Visitor : KtVisitor<Unit, Unit>() {

        private fun KtElement?.convertSafe() =
            this?.convert()

        private fun KtElement.convert() {
            if (contextInterceptor.allowToVisit(this)) {
                this.accept(this@Visitor, Unit)
            }
        }

        private fun KtExpression?.toFirExpression() {
            if (!stubMode) convertSafe()
        }

        private fun KtDeclaration.toFirDeclaration(owner: KtClassOrObject) {
            when (this) {
                is KtSecondaryConstructor -> toFirConstructor()
                is KtEnumEntry -> {
                    val primaryConstructor = owner.primaryConstructor
                    val ownerClassHasDefaultConstructor =
                        primaryConstructor?.valueParameters?.isEmpty() ?: owner.secondaryConstructors.let { constructors ->
                            constructors.isEmpty() || constructors.any { it.valueParameters.isEmpty() }
                        }
                    toFirEnumEntry(ownerClassHasDefaultConstructor)
                }
                is KtProperty -> {
                    toFirProperty()
                }
                else -> convert()
            }
        }

        private fun KtExpression?.toFirBlock() {
            if (this is KtBlockExpression) {
                convert()
            } else {
                convertSafe()
            }
        }

        private fun KtDeclarationWithBody.buildFirBody() {
            if (!hasBody()) return

            if (hasBlockBody()) {
                if (!stubMode) {
                    bodyBlockExpression.convertSafe()
                }
            } else {
                bodyExpression.toFirExpression()
            }
        }

        private fun ValueArgument?.toFirExpression() {
            if (this == null) return

            when (val expression = getArgumentExpression()) {
                is KtConstantExpression, is KtStringTemplateExpression -> {
                    expression.convert()
                }
                else -> {
                    expression.toFirExpression()
                }
            }
        }

        private fun KtPropertyAccessor?.toFirPropertyAccessor(isGetter: Boolean) {
            if (this == null || !hasBody()) {
                this?.extractAnnotations()
                return
            }

            if (isGetter) {
                returnTypeReference?.convertSafe()
            } else {
                returnTypeReference.convertSafe()
            }

            extractAnnotations()

            val accessorTarget = FirFunctionTarget(labelName = null, isLambda = false)
            withFir<FirFunction<*>> {
                accessorTarget.bind(this)
            }
            this@RawFirFragmentBuilder.context.firFunctionTargets += accessorTarget

            extractValueParameters()
            this@toFirPropertyAccessor.obtainContractDescription()
            this@toFirPropertyAccessor.buildFirBody()

            this@RawFirFragmentBuilder.context.firFunctionTargets.removeLast()
        }

        private fun KtParameter.toFirValueParameter() {
            typeReference?.convertSafe()
            if (hasDefaultValue()) {
                this@toFirValueParameter.defaultValue.toFirExpression()
            }
            extractAnnotations()
        }

        private fun KtAnnotated.extractAnnotations() {
            for (annotationEntry in annotationEntries) {
                annotationEntry.convert()
            }
        }

        private fun KtTypeParameterListOwner.extractTypeParameters() {
            for (typeParameter in typeParameters) {
                typeParameter.convert()
            }
        }

        private fun KtDeclarationWithBody.extractValueParameters() {
            for (valueParameter in valueParameters) {
                valueParameter.toFirValueParameter()
            }
        }

        private fun KtCallElement.extractArguments() {
            for (argument in valueArguments) {
                argument.toFirExpression()
            }
        }


        private fun generateDestructuringBlock(multiDeclaration: KtDestructuringDeclaration) {
            for (entry in multiDeclaration.entries) {
                if (entry.nameIdentifier?.text == "_") continue
                entry.typeReference.convertSafe()
                entry.extractAnnotations()
            }
        }


        private fun KtClassOrObject.extractSuperTypeListEntriesTo() {
            var superTypeCallEntry: KtSuperTypeCallEntry? = null
            for (superTypeListEntry in superTypeListEntries) {
                when (superTypeListEntry) {
                    is KtSuperTypeEntry -> {
                        superTypeListEntry.typeReference.convertSafe()
                    }
                    is KtSuperTypeCallEntry -> {
                        superTypeListEntry.calleeExpression.typeReference.convertSafe()
                        superTypeCallEntry = superTypeListEntry
                    }
                    is KtDelegatedSuperTypeEntry -> {
                        superTypeListEntry.typeReference.convertSafe()
                        superTypeListEntry.delegateExpression.toFirExpression()
                    }
                }
            }

            primaryConstructor.toFirConstructor(superTypeCallEntry)
        }

        private fun KtPrimaryConstructor?.toFirConstructor(superTypeCallEntry: KtSuperTypeCallEntry?) {
            if (!stubMode) {
                superTypeCallEntry?.extractArguments()
            }
            this?.extractAnnotations()
            this?.extractValueParameters()
        }

        override fun visitKtFile(file: KtFile, data: Unit) {
            context.packageFqName = file.packageFqName
            file.intercept()

            for (annotationEntry in file.annotationEntries) {
                annotationEntry.convert()
            }

            for (declaration in file.declarations) {
                declaration.convert()
            }
        }

        private fun KtEnumEntry.toFirEnumEntry(ownerClassHasDefaultConstructor: Boolean) {
            if (ownerClassHasDefaultConstructor && initializerList == null &&
                annotationEntries.isEmpty() && body == null
            ) {
                return
            }

            withChildClassName(nameAsSafeName) {

                extractAnnotations()

                withFir<FirEnumEntry> {
                    val type = initializer?.typeRef as? FirResolvedTypeRef
                    if (type != null) {
                        registerSelfType(type)
                    }
                }

                val superTypeCallEntry = superTypeListEntries.firstIsInstanceOrNull<KtSuperTypeCallEntry>()
                primaryConstructor.toFirConstructor(superTypeCallEntry)

                withChildClassName(ANONYMOUS_OBJECT_NAME, isLocal = true) {
                    for (declaration in this@toFirEnumEntry.declarations) {
                        declaration.toFirDeclaration(this@toFirEnumEntry)
                    }
                }
            }
        }

        override fun visitClassOrObject(classOrObject: KtClassOrObject, data: Unit) {
            classOrObject.intercept()

            withChildClassName(
                classOrObject.nameAsSafeName,
                classOrObject.isLocal || classOrObject.getStrictParentOfType<KtEnumEntry>() != null
            ) {

                val isInner = classOrObject.hasModifier(INNER_KEYWORD)

                withCapturedTypeParameters {
                    if (!isInner) context.capturedTypeParameters = context.capturedTypeParameters.clear()

                    classOrObject.extractAnnotations()
                    classOrObject.extractTypeParameters()

                    classOrObject.withFir<FirRegularClass> {
                        addCapturedTypeParameters(typeParameters.take(classOrObject.typeParameters.size))

                        val resolvedTypeRef = classOrObject.toDelegatedSelfType(this)
                        registerSelfType(resolvedTypeRef)
                    }

                    classOrObject.extractSuperTypeListEntriesTo()

                    val primaryConstructor = classOrObject.primaryConstructor
                    if (primaryConstructor != null) {
                        for (valueParameter in primaryConstructor.valueParameters) {
                            if (valueParameter.hasValOrVar()) {
                                valueParameter.convertSafe()
                                valueParameter.extractAnnotations()
                            }
                        }
                    }

                    for (declaration in classOrObject.declarations) {
                        declaration.toFirDeclaration(classOrObject)
                    }

                    if (classOrObject.hasModifier(DATA_KEYWORD) && primaryConstructor != null) {
                        for (primaryConstructorParameter in classOrObject.primaryConstructorParameters) {
                            primaryConstructorParameter.toFirValueParameter()
                        }
                    }
                }
            }
        }

        override fun visitObjectLiteralExpression(expression: KtObjectLiteralExpression, data: Unit) {
            expression.intercept()

            withChildClassName(ANONYMOUS_OBJECT_NAME) {

                val objectDeclaration = expression.objectDeclaration

                objectDeclaration.withFir<FirAnonymousObject> {
                    val delegatedSelfType = objectDeclaration.toDelegatedSelfType(this)
                    registerSelfType(delegatedSelfType)
                }

                objectDeclaration.extractAnnotations()
                objectDeclaration.extractSuperTypeListEntriesTo()

                for (declaration in objectDeclaration.declarations) {
                    declaration.toFirDeclaration(owner = objectDeclaration)
                }
            }
        }

        override fun visitTypeAlias(typeAlias: KtTypeAlias, data: Unit) {
            typeAlias.intercept()

            withChildClassName(typeAlias.nameAsSafeName) {
                typeAlias.getTypeReference().convertSafe()
                typeAlias.extractAnnotations()
                typeAlias.extractTypeParameters()
            }
        }

        override fun visitNamedFunction(function: KtNamedFunction, data: Unit) {
            function.intercept()

            val typeReference = function.typeReference
            typeReference.convertSafe()

            function.receiverTypeReference.convertSafe()

            val functionIsAnonymousFunction = function.name == null && !function.parent.let { it is KtFile || it is KtClassBody }
            val labelName = if (functionIsAnonymousFunction) {
                function.getLabelName()
            } else {
                val name = function.nameAsSafeName
                runIf(!name.isSpecial) { name.identifier }
            }

            val target = FirFunctionTarget(labelName, isLambda = false)
            function.withFir<FirFunction<*>> {
                target.bind(this)
            }
            context.firFunctionTargets += target

            function.extractAnnotations()

            if (!functionIsAnonymousFunction) {
                function.extractTypeParameters()
            }

            for (valueParameter in function.valueParameters) {
                valueParameter.convert()
            }

            withCapturedTypeParameters {
                if (!functionIsAnonymousFunction) {
                    function.withFir<FirSimpleFunction> {
                        addCapturedTypeParameters(typeParameters)
                    }
                }
                function.obtainContractDescription()
                function.buildFirBody()
            }

            context.firFunctionTargets.removeLast()
        }

        private fun KtDeclarationWithBody.obtainContractDescription() {
            contractDescription?.extractRawEffects()
        }

        private fun KtContractEffectList.extractRawEffects() {
            getExpressions().forEach { it.convert() }
        }

        override fun visitLambdaExpression(expression: KtLambdaExpression, data: Unit) {
            expression.intercept()

            val literal = expression.functionLiteral

            for (valueParameter in literal.valueParameters) {
                val multiDeclaration = valueParameter.destructuringDeclaration
                if (multiDeclaration != null) {
                    valueParameter.typeReference?.convertSafe()
                    generateDestructuringBlock(multiDeclaration)
                } else {
                    valueParameter.typeReference?.convertSafe()
                    valueParameter.toFirValueParameter()
                }
            }

            //TODO Maybe it worth to take it from Fir node
            val expressionSource = expression.toFirSourceElement()
            val label = context.firLabels.pop() ?: context.calleeNamesForLambda.lastOrNull()?.let {
                buildLabel {
                    source = expressionSource.fakeElement(FirFakeSourceElementKind.GeneratedLambdaLabel)
                    name = it.asString()
                }
            }

            val target = FirFunctionTarget(label?.name, isLambda = true)
            literal.withFir<FirAnonymousFunction> {
                target.bind(this)
            }
            context.firFunctionTargets += target

            val ktBody = literal.bodyExpression
            if (ktBody != null) {
                configureBlockWithoutBuilding(ktBody)
            }

            context.firFunctionTargets.removeLast()
        }

        private fun KtSecondaryConstructor.toFirConstructor() {
            getDelegationCall().convert()

            val target = FirFunctionTarget(labelName = null, isLambda = false)
            this@toFirConstructor.withFir<FirFunction<*>> {
                target.bind(this)
            }
            this@RawFirFragmentBuilder.context.firFunctionTargets += target

            extractAnnotations()
            extractValueParameters()
            buildFirBody()

            this@RawFirFragmentBuilder.context.firFunctionTargets.removeLast()
        }

        private fun KtConstructorDelegationCall.convert() {
            if (!stubMode) {
                extractArguments()
            }
        }

        private fun KtProperty.toFirProperty() {
            typeReference.convertSafe()

            if (hasInitializer()) {
                if (!stubMode) initializer.toFirExpression()
            }

            val delegateExpression = delegate?.expression

            if (isLocal) {
                delegateExpression.toFirExpression()
            } else {
                receiverTypeReference.convertSafe()
                extractTypeParameters()
                withCapturedTypeParameters {

                    withFir<FirProperty> {
                        addCapturedTypeParameters(typeParameters)
                    }

                    if (hasDelegate() && !stubMode) {
                        delegateExpression.toFirExpression()
                    }

                    getter.toFirPropertyAccessor(isGetter = true)
                    if (isVar) {
                        setter.toFirPropertyAccessor(isGetter = false)
                    }

                    delegateExpression?.toFirExpression()
                }
            }

            extractAnnotations()
        }

        override fun visitAnonymousInitializer(initializer: KtAnonymousInitializer, data: Unit) {
            initializer.intercept()
            if (!stubMode) initializer.body.toFirBlock()
        }

        override fun visitProperty(property: KtProperty, data: Unit) {
            property.intercept()
            property.toFirProperty()
        }

        override fun visitTypeReference(typeReference: KtTypeReference, data: Unit) {
            typeReference.intercept()

            val typeElement = typeReference.typeElement

            fun KtTypeElement?.unwrapNullable(): KtTypeElement? =
                if (this is KtNullableType) this.innerType.unwrapNullable() else this

            when (val unwrappedElement = typeElement.unwrapNullable()) {
                is KtUserType -> {
                    var referenceExpression = unwrappedElement.referenceExpression
                    if (referenceExpression != null) {
                        var ktQualifier: KtUserType? = unwrappedElement
                        do {
                            for (typeArgument in ktQualifier!!.typeArguments) {
                                typeArgument.convert()
                            }
                            ktQualifier = ktQualifier.qualifier
                            referenceExpression = ktQualifier?.referenceExpression
                        } while (referenceExpression != null)

                    }
                }
                is KtFunctionType -> {
                    unwrappedElement.receiverTypeReference.convertSafe()
                    unwrappedElement.returnTypeReference.convertSafe()
                    for (valueParameter in unwrappedElement.parameters) {
                        valueParameter.convert()
                    }
                }
            }

            typeReference.extractAnnotations()
        }

        override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry, data: Unit) {
            annotationEntry.intercept()

            annotationEntry.typeReference.convertSafe()
            annotationEntry.extractArguments()
        }

        override fun visitTypeParameter(parameter: KtTypeParameter, data: Unit) {
            parameter.intercept()

            parameter.extractAnnotations()
            val extendsBound = parameter.extendsBound
            extendsBound?.convert()

            val owner = parameter.getStrictParentOfType<KtTypeParameterListOwner>() ?: return
            val parameterName = parameter.nameAsSafeName

            for (typeConstraint in owner.typeConstraints) {
                val subjectName = typeConstraint.subjectTypeParameterName?.getReferencedNameAsName()
                if (subjectName == parameterName) {
                    typeConstraint.boundTypeReference.convertSafe()
                }
            }
        }

        // TODO introduce placeholder projection type
        private fun KtTypeProjection.isPlaceholderProjection() =
            projectionKind == KtProjectionKind.NONE && (typeReference?.typeElement as? KtUserType)?.referencedName == "_"

        override fun visitTypeProjection(typeProjection: KtTypeProjection, data: Unit) {
            typeProjection.intercept()

            if (typeProjection.projectionKind == KtProjectionKind.STAR || typeProjection.isPlaceholderProjection()) return

            typeProjection.typeReference.convertSafe()
        }

        override fun visitParameter(parameter: KtParameter, data: Unit) {
            parameter.intercept()
            parameter.toFirValueParameter()
        }

        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression, data: Unit?) {
            expression.intercept()
        }

        override fun visitConstantExpression(expression: KtConstantExpression, data: Unit?) {
            expression.intercept()
        }

        override fun visitBlockExpression(expression: KtBlockExpression, data: Unit) {
            expression.intercept()
            configureBlockWithoutBuilding(expression)
        }

        private fun configureBlockWithoutBuilding(expression: KtBlockExpression) {
            for (statement in expression.statements) {
                statement.convert()
            }
        }

        override fun visitStringTemplateExpression(expression: KtStringTemplateExpression, data: Unit) {
            expression.intercept()

            for (entry in expression.entries) {
                if (entry is KtStringTemplateEntryWithExpression) {
                    entry.expression.toFirExpression()
                }
            }
        }

        override fun visitReturnExpression(expression: KtReturnExpression, data: Unit) {
            expression.intercept()
            expression.returnedExpression?.toFirExpression()
        }

        override fun visitTryExpression(expression: KtTryExpression, data: Unit) {
            expression.intercept()

            expression.tryBlock.toFirBlock()
            expression.finallyBlock?.finalExpression?.toFirBlock()
            for (clause in expression.catchClauses) {
                clause.catchParameter?.toFirValueParameter() ?: continue
                clause.catchBody.toFirBlock()
            }
        }

        override fun visitIfExpression(expression: KtIfExpression, data: Unit) {
            expression.intercept()

            val ktCondition = expression.condition
            ktCondition.toFirExpression()
            expression.then.toFirBlock()
            if (expression.elseKeyword != null) {
                buildElseIfTrueCondition()
                expression.`else`.toFirBlock()
            }
        }

        override fun visitWhenExpression(expression: KtWhenExpression, data: Unit) {
            expression.intercept()

            val ktSubjectExpression = expression.subjectExpression

            val subjectExpression = when (ktSubjectExpression) {
                is KtVariableDeclaration -> ktSubjectExpression.initializer
                else -> ktSubjectExpression
            }
            subjectExpression?.toFirExpression()
            val hasSubject = subjectExpression != null

            if (ktSubjectExpression is KtVariableDeclaration) {
                ktSubjectExpression.typeReference.convertSafe()
            }

            ///HERE WE HAVE self binds FirExpressionRef<FirWhenExpression>
            //It used inside when expression so possibly we cant analyze subexpressions of when

            for (entry in expression.entries) {
                entry.expression.toFirBlock()
                if (!entry.isElse) {
                    if (hasSubject) {
                        for (condition in entry.conditions) {
                            when (condition) {
                                is KtWhenConditionWithExpression -> {
                                    condition.expression.toFirExpression()
                                }
                                is KtWhenConditionInRange -> {
                                    condition.rangeExpression.toFirExpression()
                                }
                                is KtWhenConditionIsPattern -> {
                                    condition.typeReference.convertSafe()
                                }
                            }
                        }
                    } else {
                        val ktCondition = entry.conditions.first() as? KtWhenConditionWithExpression
                        ktCondition?.expression.toFirExpression()
                    }
                }
            }
        }

        private fun KtLoopExpression.configure(generateBlock: () -> Unit) {
            val label = this@RawFirFragmentBuilder.context.firLabels.pop()
            val target = FirLoopTarget(label?.name)
            this.withFir<FirLoop> {
                target.bind(this)
            }
            this@RawFirFragmentBuilder.context.firLoopTargets += target
            generateBlock()
            this@RawFirFragmentBuilder.context.firLoopTargets.removeLast()
        }

        override fun visitDoWhileExpression(expression: KtDoWhileExpression, data: Unit) {
            expression.intercept()

            expression.condition.toFirExpression()
            expression.configure {
                expression.body.toFirBlock()
            }
        }

        override fun visitWhileExpression(expression: KtWhileExpression, data: Unit) {
            expression.intercept()

            expression.condition.toFirExpression()
            expression.configure {
                expression.body.toFirBlock()
            }
        }

        override fun visitForExpression(expression: KtForExpression, data: Unit?) {
            expression.intercept()

            expression.loopRange.toFirExpression()

            expression.configure {
                val body = expression.body
                if (body is KtBlockExpression) {
                    configureBlockWithoutBuilding(body)
                } else {
                    body?.convert()
                }

                val ktParameter = expression.loopParameter
                if (ktParameter != null) {
                    ktParameter.typeReference.convertSafe()

                    val multiDeclaration = ktParameter.destructuringDeclaration
                    if (multiDeclaration != null) {
                        for (entry in multiDeclaration.entries) {
                            if (entry.nameIdentifier?.text == "_") continue
                            entry.typeReference.convertSafe()
                            entry.extractAnnotations()
                        }
                    }
                }
            }
        }

        override fun visitBreakExpression(expression: KtBreakExpression, data: Unit?) {
            expression.intercept()
        }

        override fun visitContinueExpression(expression: KtContinueExpression, data: Unit?) {
            expression.intercept()
        }

        override fun visitBinaryExpression(expression: KtBinaryExpression, data: Unit) {
            expression.intercept()

            val operationToken = expression.operationToken

            if (operationToken == IDENTIFIER) {
                context.calleeNamesForLambda += expression.operationReference.getReferencedNameAsName()
            }

            expression.left.toFirExpression()
            expression.right.toFirExpression()

            if (operationToken == IDENTIFIER) {
                context.calleeNamesForLambda.removeLast()
            }
        }

        override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS, data: Unit) {
            expression.intercept()

            expression.right.convertSafe()
            expression.left.toFirExpression()
        }

        override fun visitIsExpression(expression: KtIsExpression, data: Unit) {
            expression.intercept()

            expression.typeReference.convertSafe()
            expression.leftHandSide.toFirExpression()
        }

        override fun visitUnaryExpression(expression: KtUnaryExpression, data: Unit) {
            expression.intercept()

            //TODO MAYBE HERE THE BUGS WITH DESUGARING
            expression.baseExpression.toFirExpression()
        }

        private fun splitToCalleeAndReceiver(calleeExpression: KtExpression?): Name {
            return when (calleeExpression) {
                is KtSimpleNameExpression -> calleeExpression.getReferencedNameAsName()
                is KtParenthesizedExpression -> splitToCalleeAndReceiver(calleeExpression.expression)
                null -> Name.special("<Call has no callee>")
                is KtSuperExpression -> Name.special("<Super cannot be a callee>")
                else -> {
                    calleeExpression.toFirExpression()
                    OperatorNameConventions.INVOKE
                }
            }
        }

        override fun visitCallExpression(expression: KtCallExpression, data: Unit) {
            expression.intercept()

            val calleeReferenceName = splitToCalleeAndReceiver(expression.calleeExpression)

            if (expression.valueArgumentList != null || expression.lambdaArguments.isNotEmpty()) {
                context.calleeNamesForLambda += calleeReferenceName
                expression.extractArguments()
                context.calleeNamesForLambda.removeLast()
            }

            for (typeArgument in expression.typeArguments) {
                typeArgument.convert()
            }
        }

        override fun visitArrayAccessExpression(expression: KtArrayAccessExpression, data: Unit) {
            expression.intercept()

            val arrayExpression = expression.arrayExpression
            context.arraySetArgument.remove(expression)

            arrayExpression.toFirExpression()

            for (indexExpression in expression.indexExpressions) {
                indexExpression.toFirExpression()
            }
        }

        override fun visitQualifiedExpression(expression: KtQualifiedExpression, data: Unit) {
            expression.intercept()

            val selector = expression.selectorExpression ?: return
            selector.toFirExpression()
            expression.receiverExpression.toFirExpression()
        }

        override fun visitThisExpression(expression: KtThisExpression, data: Unit?) {
            expression.intercept()
        }

        override fun visitSuperExpression(expression: KtSuperExpression, data: Unit) {
            expression.intercept()

            expression.superTypeQualifier.convertSafe()
        }

        override fun visitParenthesizedExpression(expression: KtParenthesizedExpression, data: Unit) {
            expression.intercept()

            expression.expression.convertSafe()
        }

        override fun visitLabeledExpression(expression: KtLabeledExpression, data: Unit) {
            expression.intercept()

            val label = expression.getTargetLabel()
            val size = context.firLabels.size
            if (label != null) {
                context.firLabels += buildLabel {
                    source = label.toFirPsiSourceElement()
                    name = label.getReferencedName()
                }
            }

            expression.baseExpression.convertSafe()

            if (size != context.firLabels.size) {
                context.firLabels.removeLast()
            }
        }

        override fun visitAnnotatedExpression(expression: KtAnnotatedExpression, data: Unit) {
            expression.intercept()

            expression.baseExpression.convertSafe()
            expression.extractAnnotations()
        }

        override fun visitThrowExpression(expression: KtThrowExpression, data: Unit) {
            expression.intercept()

            expression.thrownExpression.toFirExpression()
        }

        override fun visitDestructuringDeclaration(multiDeclaration: KtDestructuringDeclaration, data: Unit) {
            multiDeclaration.intercept()

            multiDeclaration.initializer.toFirExpression()
            generateDestructuringBlock(multiDeclaration)
        }

        override fun visitClassLiteralExpression(expression: KtClassLiteralExpression, data: Unit) {
            expression.intercept()

            expression.receiverExpression.toFirExpression()
        }

        override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression, data: Unit) {
            expression.intercept()

            expression.receiverExpression?.toFirExpression()
        }

        override fun visitCollectionLiteralExpression(expression: KtCollectionLiteralExpression, data: Unit) {
            expression.intercept()

            for (innerExpression in expression.getInnerExpressions()) {
                innerExpression.toFirExpression()
            }
        }
    }
}
