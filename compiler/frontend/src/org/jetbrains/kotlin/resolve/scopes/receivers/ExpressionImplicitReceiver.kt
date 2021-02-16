/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.scopes.receivers

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.types.KotlinType

class ExpressionImplicitReceiver(
    override val declarationDescriptor: DeclarationDescriptor,
    override val expression: KtExpression,
    receiverType: KotlinType,
    original: ReceiverValue?
) : AbstractReceiverValue(receiverType, original), ImplicitReceiver, ExpressionReceiver {
    override fun replaceType(newType: KotlinType): ReceiverValue =
        ExpressionImplicitReceiver(declarationDescriptor, expression, receiverType, original)
}