/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.builder

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
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
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.runIf

fun interface PsiToFirElementMapper {
    fun mapToFirElement(ktElement: KtElement): FirElement?
}

private fun interface RawFirContextInterceptor {
    fun intercept(context: Context<PsiElement>, psiElement: PsiElement)
}

fun tryBuildFirFragment(
    session: FirSession,
    baseScopeProvider: FirScopeProvider,
    psiToFirElementMapper: PsiToFirElementMapper,
    targetExpression: KtExpression,
    anchorExpression: KtExpression,
    mode: RawFirBuilderMode = RawFirBuilderMode.NORMAL,
): FirElement? {

    var result: FirElement? = null
    val interceptor = RawFirContextInterceptor { context, element ->
        if (element != anchorExpression) return@RawFirContextInterceptor
        if (result != null) return@RawFirContextInterceptor

        val fragmentBuilder = RawFirBuilder(session, baseScopeProvider, mode, context)
        result = fragmentBuilder.buildFirElement(targetExpression)
    }

    val contextBuilder = RawFirContextBuilder(
        session,
        psiToFirElementMapper,
        mode,
        interceptor
    )
    contextBuilder.execute(anchorExpression.containingKtFile)

    return result
}


private class RawFirContextBuilder(
    session: FirSession,
    private val psiToFirElementMapper: PsiToFirElementMapper,
    val mode: RawFirBuilderMode = RawFirBuilderMode.NORMAL,
    private val contextInterceptor: RawFirContextInterceptor
) : BaseFirBuilder<PsiElement>(session) {

    private val stubMode get() = mode == RawFirBuilderMode.STUBS

    fun execute(file: KtFile) {
        file.accept(Visitor(), Unit)
    }

    private inline fun <reified T> KtElement.withFir(body: T.() -> Unit) where T : FirElement {
        val firMappedElement = psiToFirElementMapper.mapToFirElement(this) as? T ?: return
        if (firMappedElement.source.psi == this) {
            firMappedElement.body()
        }
    }

    override fun PsiElement.toFirSourceElement(kind: FirFakeSourceElementKind?): FirPsiSourceElement<*> {
        val actualKind = kind ?: this@RawFirContextBuilder.context.forcedElementSourceKind ?: FirRealSourceElementKind
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

    private inner class Visitor : KtVisitor<Unit, Unit>() {

        override fun visitKtElement(element: KtElement, data: Unit?) {
            element.intercept()
            super.visitKtElement(element, data)
        }

        private fun KtElement?.convertSafe() =
            this?.accept(this@Visitor, Unit)

        private fun KtElement.convert() =
            this.accept(this@Visitor, Unit)

        private fun KtExpression?.toFirExpression() {
            if (!stubMode) convertSafe()
        }

        private fun KtExpression.toFirStatement() = convertSafe()

        private fun KtDeclaration.toFirDeclaration(owner: KtClassOrObject) {
            this.intercept()
            when (this) {
                is KtSecondaryConstructor -> toFirConstructor()
                is KtEnumEntry -> {
                    val primaryConstructor = owner.primaryConstructor.interceptSafe()
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
            this.interceptSafe()
            if (this is KtBlockExpression) {
                accept(this@Visitor, Unit)
            } else {
                this?.convert()
            }
        }

        private fun KtDeclarationWithBody.buildFirBody() {
            this.intercept()
            if (!hasBody()) return
            require(mode != RawFirBuilderMode.LAZY_BODIES)

            if (hasBlockBody()) {
                if (!stubMode) {
                    bodyBlockExpression?.accept(this@Visitor, Unit)
                }
            } else {
                bodyExpression.toFirExpression()
            }
        }

        private fun ValueArgument?.toFirExpression() {
            if (this == null) return

            when (val expression = getArgumentExpression()) {
                is KtConstantExpression, is KtStringTemplateExpression -> {
                    expression.accept(this@Visitor, Unit)
                }
                else -> {
                    expression.toFirExpression()
                }
            }
        }

        private fun KtPropertyAccessor?.toFirPropertyAccessor(isGetter: Boolean) {
            this.interceptSafe()

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
            this@RawFirContextBuilder.context.firFunctionTargets += accessorTarget

            extractValueParameters()
            this@toFirPropertyAccessor.obtainContractDescription()
            this@toFirPropertyAccessor.buildFirBody()

            this@RawFirContextBuilder.context.firFunctionTargets.removeLast()
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
            this.intercept()
            for (typeParameter in typeParameters) {
                typeParameter.convert()
            }
        }

        private fun KtDeclarationWithBody.extractValueParameters() {
            this.intercept()
            for (valueParameter in valueParameters) {
                valueParameter.toFirValueParameter()
            }
        }

        private fun KtCallElement.extractArguments() {
            this.intercept()
            for (argument in valueArguments) {
                argument.toFirExpression()
            }
        }


        private fun generateDestructuringBlock(multiDeclaration: KtDestructuringDeclaration) {
            multiDeclaration.intercept()
            for (entry in multiDeclaration.entries) {
                entry.intercept()
                if (entry.nameIdentifier?.text == "_") continue
                entry.typeReference.convertSafe()
                entry.extractAnnotations()
            }
        }


        private fun KtClassOrObject.extractSuperTypeListEntriesTo() {
            this.intercept()
            var superTypeCallEntry: KtSuperTypeCallEntry? = null
            for (superTypeListEntry in superTypeListEntries) {
                superTypeCallEntry.interceptSafe()
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
            superTypeCallEntry.interceptSafe()
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
            this.intercept()

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

                    val typeParameters = context.capturedTypeParameters.map { buildOuterClassTypeParameterRef { symbol = it } }
                    addCapturedTypeParameters(typeParameters.take(classOrObject.typeParameters.size))

                    classOrObject.withFir<FirRegularClass> {
                        val resolvedTypeRef = classOrObject.toDelegatedSelfType(this)
                        registerSelfType(resolvedTypeRef)
                    }

                    classOrObject.extractSuperTypeListEntriesTo()

                    val primaryConstructor = classOrObject.primaryConstructor.interceptSafe()
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

                objectDeclaration.intercept()
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
            getExpressions().forEach { it.accept(this@Visitor, Unit) }
        }

        override fun visitLambdaExpression(expression: KtLambdaExpression, data: Unit) {
            expression.intercept()

            val literal = expression.functionLiteral.intercept()

            for (valueParameter in literal.valueParameters) {
                val multiDeclaration = valueParameter.destructuringDeclaration.interceptSafe()
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
            this@RawFirContextBuilder.context.firFunctionTargets += target

            extractAnnotations()
            extractValueParameters()
            buildFirBody()

            this@RawFirContextBuilder.context.firFunctionTargets.removeLast()
        }

        private fun KtConstructorDelegationCall.convert() {
            if (!stubMode) {
                extractArguments()
            }
        }

        private fun KtProperty.toFirProperty() {
            typeReference.convertSafe()

            require(mode != RawFirBuilderMode.LAZY_BODIES)

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

            val typeElement = typeReference.typeElement.interceptSafe()

            fun KtTypeElement?.unwrapNullable(): KtTypeElement? =
                if (this is KtNullableType) this.innerType.unwrapNullable() else this

            when (val unwrappedElement = typeElement.unwrapNullable().interceptSafe()) {
                is KtDynamicType -> {
                }
                is KtUserType -> {
                    var referenceExpression = unwrappedElement.referenceExpression.interceptSafe()
                    if (referenceExpression != null) {
                        var ktQualifier: KtUserType? = unwrappedElement
                        do {
                            for (typeArgument in ktQualifier!!.typeArguments) {
                                typeArgument.convert()
                            }
                            ktQualifier = ktQualifier.qualifier.interceptSafe()
                            referenceExpression = ktQualifier?.referenceExpression.interceptSafe()
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
                typeConstraint.intercept()
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

            val projectionKind = typeProjection.projectionKind

            if (projectionKind == KtProjectionKind.STAR || typeProjection.isPlaceholderProjection()) return

            typeProjection.typeReference.convertSafe()
        }

        override fun visitParameter(parameter: KtParameter, data: Unit) {
            parameter.intercept()
            parameter.toFirValueParameter()
        }


        override fun visitBlockExpression(expression: KtBlockExpression, data: Unit) {
            expression.intercept()
            configureBlockWithoutBuilding(expression)
        }

        private fun configureBlockWithoutBuilding(expression: KtBlockExpression) {
            for (statement in expression.statements) {
                statement.toFirStatement()
            }
        }

        override fun visitStringTemplateExpression(expression: KtStringTemplateExpression, data: Unit) {
            expression.intercept()

            for (entry in expression.entries) {
                entry.intercept()
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
                clause.intercept()
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

            val ktSubjectExpression = expression.subjectExpression.interceptSafe()

            val subjectExpression = when (ktSubjectExpression) {
                is KtVariableDeclaration -> ktSubjectExpression.initializer.interceptSafe()
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
                entry.intercept()
                entry.expression.toFirBlock()
                if (!entry.isElse) {
                    if (hasSubject) {
                        for (condition in entry.conditions) {
                            condition.intercept()
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

        private fun KtLoopExpression.configureX(generateBlock: () -> Unit) {
            val label = this@RawFirContextBuilder.context.firLabels.pop()
            val target = FirLoopTarget(label?.name)
            this.withFir<FirLoop> {
                target.bind(this)
            }
            this@RawFirContextBuilder.context.firLoopTargets += target
            generateBlock()
            this@RawFirContextBuilder.context.firLoopTargets.removeLast()
        }

        override fun visitDoWhileExpression(expression: KtDoWhileExpression, data: Unit) {
            expression.intercept()

            expression.condition.toFirExpression()
            expression.configureX {
                expression.body.toFirBlock()
            }
        }

        override fun visitWhileExpression(expression: KtWhileExpression, data: Unit) {
            expression.intercept()

            expression.condition.toFirExpression()
            expression.configureX {
                expression.body.toFirBlock()
            }
        }

        override fun visitForExpression(expression: KtForExpression, data: Unit?) {
            expression.intercept()

            expression.loopRange.toFirExpression()

            expression.configureX {
                val body = expression.body
                if (body is KtBlockExpression) {
                    configureBlockWithoutBuilding(body)
                } else {
                    body?.toFirStatement()
                }

                val ktParameter = expression.loopParameter?.intercept()
                if (ktParameter != null) {
                    ktParameter.typeReference.convertSafe()

                    val multiDeclaration = ktParameter.destructuringDeclaration?.intercept()
                    if (multiDeclaration != null) {
                        for (entry in multiDeclaration.entries) {
                            entry.intercept()
                            if (entry.nameIdentifier?.text == "_") continue
                            entry.typeReference.convertSafe()
                            entry.extractAnnotations()
                        }
                    }
                }
            }
        }

        override fun visitBinaryExpression(expression: KtBinaryExpression, data: Unit) {
            expression.intercept()

            val operationToken = expression.operationToken

            if (operationToken == IDENTIFIER) {
                context.calleeNamesForLambda += expression.operationReference.intercept().getReferencedNameAsName()
            }

            expression.left.toFirExpression()
            expression.right.toFirExpression()

            if (operationToken == IDENTIFIER) {
                context.calleeNamesForLambda.removeLast()
            }

            when (operationToken) {
                ELVIS, ANDAND, OROR, in OperatorConventions.IN_OPERATIONS, in OperatorConventions.COMPARISON_OPERATIONS ->
                    return
            }

            val conventionCallName = operationToken.toBinaryName()

            if (conventionCallName == null && operationToken != IDENTIFIER) {
                val firOperation = operationToken.toFirOperation()
                if (firOperation in FirOperation.ASSIGNMENTS) {
//TODO
//                    return expression.left.generateAssignment(source, expression.right, rightArgument, firOperation) {
//                        (this as KtExpression).toFirExpression("Incorrect expression in assignment: ${expression.text}")
//                    }
                }
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

        private fun splitToCalleeAndReceiverX(calleeExpression: KtExpression?): Name {
            return when (calleeExpression) {
                is KtSimpleNameExpression -> calleeExpression.getReferencedNameAsName()
                is KtParenthesizedExpression -> splitToCalleeAndReceiverX(calleeExpression.expression.interceptSafe())
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

            val calleeReferenceName = splitToCalleeAndReceiverX(expression.calleeExpression.interceptSafe())

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
            expression.receiverExpression.intercept()

            val selector = expression.selectorExpression ?: return
            selector.toFirExpression()
            expression.receiverExpression.toFirExpression()
        }

        override fun visitSuperExpression(expression: KtSuperExpression, data: Unit) {
            expression.intercept()

            expression.superTypeQualifier.convertSafe()
        }

        override fun visitParenthesizedExpression(expression: KtParenthesizedExpression, data: Unit) {
            expression.intercept()

            expression.expression?.accept(this, data)
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

            expression.baseExpression?.accept(this, data)

            if (size != context.firLabels.size) {
                context.firLabels.removeLast()
            }
        }

        override fun visitAnnotatedExpression(expression: KtAnnotatedExpression, data: Unit) {
            expression.intercept()

            expression.baseExpression?.accept(this, data)
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

        private fun <T> T.intercept(): T where T : KtElement = this.also {
            contextInterceptor.intercept(this@RawFirContextBuilder.context, this)
        }

        private fun <T> T?.interceptSafe(): T? where T : KtElement = this?.also {
            contextInterceptor.intercept(this@RawFirContextBuilder.context, this)
        }

        override fun visitCollectionLiteralExpression(expression: KtCollectionLiteralExpression, data: Unit) {
            expression.intercept()

            for (innerExpression in expression.getInnerExpressions()) {
                innerExpression.toFirExpression()
            }
        }
    }
}
