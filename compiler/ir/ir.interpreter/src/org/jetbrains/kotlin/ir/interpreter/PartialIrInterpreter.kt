/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.interpreter

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.interpreter.checker.EvaluationMode
import org.jetbrains.kotlin.ir.interpreter.checker.IrCompileTimeChecker
import org.jetbrains.kotlin.ir.interpreter.stack.StackImpl
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.fileOrNull
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult
import kotlin.Exception

// This class is an addition to IrInterpreter. The same logic can be implemented inside IrInterpreter with some kind of flag.
class PartialIrInterpreter(
    val irBuiltIns: IrBuiltIns, bodyMap: Map<IdSignature, IrBody> = emptyMap(), mode: EvaluationMode = EvaluationMode.WITH_ANNOTATIONS
) : IrElementVisitor<IrElement?, Nothing?> {
    private val stack = StackImpl()
    private val interpreter = IrInterpreter(irBuiltIns, bodyMap, stack)
    private val checker = IrCompileTimeChecker(mode = mode)

    // This method will evaluate and replace some of function statements
    // TODO how debugger will behave?
    fun interpret(irFunction: IrFunction) {
        stack.clean(irFunction.fileOrNull)
        irFunction.body = irFunction.body?.let { visitBody(it, null) } as? IrBody ?: irFunction.body
    }

    private fun IrElement.interpret(): IrElement? {
        try {
            return when (this) {
                //is IrSimpleFunction -> interpretFunction(this)
                //--is IrCall -> interpretCall(this)
                //--is IrConstructorCall -> interpretConstructorCall(this)
                //--is IrEnumConstructorCall -> interpretEnumConstructorCall(this)
                //--is IrDelegatingConstructorCall -> interpretDelegatedConstructorCall(this)
                //--is IrInstanceInitializerCall -> interpretInstanceInitializerCall(this)
                //--is IrBody -> interpretBody(this)
                //--is IrBlock -> interpretBlock(this)
                //--is IrReturn -> interpretReturn(this)
//                is IrSetField -> interpretSetField(this)
//                is IrGetField -> interpretGetField(this)
//                is IrGetValue -> interpretGetValue(this)
//                is IrGetObjectValue -> interpretGetObjectValue(this)
//                is IrGetEnumValue -> interpretGetEnumValue(this)
//                is IrEnumEntry -> interpretEnumEntry(this)
                //--is IrConst<*> -> interpretConst(this)
//                is IrVariable -> interpretVariable(this)
//                is IrSetVariable -> interpretSetVariable(this)
//                is IrTypeOperatorCall -> interpretTypeOperatorCall(this)
                //--is IrBranch -> interpretBranch(this)
//                is IrWhileLoop -> interpretWhile(this)
//                is IrDoWhileLoop -> interpretDoWhile(this)
                //--is IrWhen -> interpretWhen(this)
//                is IrBreak -> interpretBreak(this)
//                is IrContinue -> interpretContinue(this)
//                is IrVararg -> interpretVararg(this)
//                is IrSpreadElement -> interpretSpreadElement(this)
//                is IrTry -> interpretTry(this)
//                is IrCatch -> interpretCatch(this)
//                is IrThrow -> interpretThrow(this)
//                is IrStringConcatenation -> interpretStringConcatenation(this)
//                is IrFunctionExpression -> interpretFunctionExpression(this)
//                is IrFunctionReference -> interpretFunctionReference(this)
//                is IrPropertyReference -> interpretPropertyReference(this)
//                is IrClassReference -> interpretClassReference(this)
//                is IrComposite -> interpretComposite(this)

                else -> TODO("${this.javaClass} not supported")
            }
        } catch (e: Exception) {
            return this
        }
    }

    override fun visitElement(element: IrElement, data: Nothing?): IrElement? {
        TODO("${element.javaClass} not supported")
    }

    override fun visitCall(expression: IrCall, data: Nothing?): IrElement? {
        if (!expression.accept(checker, null)) return expression
        expression.dispatchReceiver = expression.dispatchReceiver?.let { it.accept(this, data) as? IrExpression ?: return null }
        expression.extensionReceiver = expression.extensionReceiver?.let { it.accept(this, data) as? IrExpression ?: return null }
        for (i in 0 until expression.valueArgumentsCount) {
            val argument = expression.getValueArgument(i) ?: continue
            expression.putValueArgument(i, argument.accept(this, data) as? IrExpression ?: return null)
        }

        return interpreter.interpret(expression)
    }

    override fun visitConstructorCall(expression: IrConstructorCall, data: Nothing?): IrElement? {
        TODO("Not yet implemented")
    }

    override fun visitEnumConstructorCall(expression: IrEnumConstructorCall, data: Nothing?): IrElement? {
        TODO("Not yet implemented")
    }

    override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall, data: Nothing?): IrElement? {
        TODO("Not yet implemented")
    }

    override fun visitInstanceInitializerCall(expression: IrInstanceInitializerCall, data: Nothing?): IrElement? {
        TODO("Not yet implemented")
    }

    override fun visitBody(body: IrBody, data: Nothing?): IrElement? {
        return when (body) {
            is IrBlockBody -> visitBlockBody(body, data)
            is IrExpressionBody -> visitExpressionBody(body, data)
            is IrSyntheticBody -> visitSyntheticBody(body, data)
            else -> null
        }
    }

    override fun visitBlockBody(body: IrBlockBody, data: Nothing?): IrElement {
        return body.factory.createBlockBody(body.startOffset, body.endOffset) {
            this.statements.addAll(body.statements.map { it.accept(this@PartialIrInterpreter, data) as? IrStatement ?: it })
        }
    }

    override fun visitExpressionBody(body: IrExpressionBody, data: Nothing?): IrElement {
        return body.factory.createExpressionBody(
            body.startOffset, body.endOffset, body.expression.accept(this, data) as? IrExpression ?: body.expression
        )
    }

    override fun visitSyntheticBody(body: IrSyntheticBody, data: Nothing?): IrElement = body

    override fun visitBlock(expression: IrBlock, data: Nothing?): IrElement? {
        return when (expression) {
            is IrBlockImpl -> {
                val statements = expression.statements.map { it.accept(this, data) as? IrStatement ?: it }
                IrBlockImpl(expression.startOffset, expression.endOffset, expression.type, expression.origin, statements)
            }
            else -> return null
        }
    }

    override fun visitReturn(expression: IrReturn, data: Nothing?): IrElement? {
        expression.value = expression.value.accept(this, data) as? IrExpression ?: return null
        return expression
    }

    override fun <T> visitConst(expression: IrConst<T>, data: Nothing?): IrElement = expression

    override fun visitBranch(branch: IrBranch, data: Nothing?): IrElement? {
        branch.condition = branch.condition.accept(this, data) as? IrExpression ?: return null
        if (branch.condition.let { it is IrConst<*> && it.value == true }) {
            return branch.result.accept(this, data) as? IrExpression ?: return null
        }
        return null
    }

    override fun visitWhen(expression: IrWhen, data: Nothing?): IrElement {
        return expression.branches.firstNotNullResult { it.accept(this, data) } ?: expression
    }
}
