/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.interpreter

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.interpreter.checker.EvaluationMode
import org.jetbrains.kotlin.ir.interpreter.checker.IrCompileTimeChecker
import org.jetbrains.kotlin.ir.interpreter.stack.StackImpl
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.fileOrNull
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult
import kotlin.Exception

// This class is an addition to IrInterpreter. The same logic can be implemented inside IrInterpreter with some kind of flag.
class PartialIrInterpreter(
    val irBuiltIns: IrBuiltIns, bodyMap: Map<IdSignature, IrBody> = emptyMap(), mode: EvaluationMode = EvaluationMode.WITH_ANNOTATIONS
) {
    private val stack = StackImpl()
    private val interpreter = IrInterpreter(irBuiltIns, bodyMap, stack)
    private val checker = IrCompileTimeChecker(mode = mode)

    // This method will evaluate and replace some of function statements
    // TODO how debugger will behave?
    fun interpret(irFunction: IrFunction) {
        stack.clean(irFunction.fileOrNull)
        irFunction.body = irFunction.body?.interpret() as? IrBody ?: irFunction.body
    }

    private fun IrElement.interpret(): IrElement? {
        try {
            return when (this) {
                //is IrSimpleFunction -> interpretFunction(this)
                is IrCall -> interpretCall(this)
                is IrConstructorCall -> interpretConstructorCall(this)
                is IrEnumConstructorCall -> interpretEnumConstructorCall(this)
                is IrDelegatingConstructorCall -> interpretDelegatedConstructorCall(this)
                is IrInstanceInitializerCall -> interpretInstanceInitializerCall(this)
                is IrBody -> interpretBody(this)
                is IrBlock -> interpretBlock(this)
                is IrReturn -> interpretReturn(this)
//                is IrSetField -> interpretSetField(this)
//                is IrGetField -> interpretGetField(this)
//                is IrGetValue -> interpretGetValue(this)
//                is IrGetObjectValue -> interpretGetObjectValue(this)
//                is IrGetEnumValue -> interpretGetEnumValue(this)
//                is IrEnumEntry -> interpretEnumEntry(this)
                is IrConst<*> -> interpretConst(this)
//                is IrVariable -> interpretVariable(this)
//                is IrSetVariable -> interpretSetVariable(this)
//                is IrTypeOperatorCall -> interpretTypeOperatorCall(this)
                is IrBranch -> interpretBranch(this)
//                is IrWhileLoop -> interpretWhile(this)
//                is IrDoWhileLoop -> interpretDoWhile(this)
                is IrWhen -> interpretWhen(this)
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

    private fun interpretCall(call: IrCall): IrElement? {
        if (!call.accept(checker, null)) return call
        call.dispatchReceiver = call.dispatchReceiver?.let { it.interpret() as? IrExpression ?: return null }
        call.extensionReceiver = call.extensionReceiver?.let { it.interpret() as? IrExpression ?: return null }
        for (i in 0 until call.valueArgumentsCount) {
            val argument = call.getValueArgument(i) ?: continue
            call.putValueArgument(i, argument.interpret() as? IrExpression ?: return null)
        }

        return interpreter.interpret(call)
    }

    private fun interpretConstructorCall(constructorCall: IrConstructorCall): IrElement? {
        TODO("Not yet implemented")
    }

    private fun interpretEnumConstructorCall(enumConstructorCall: IrEnumConstructorCall): IrElement? {
        TODO("Not yet implemented")
    }

    private fun interpretDelegatedConstructorCall(delegatingConstructorCall: IrDelegatingConstructorCall): IrElement? {
        TODO("Not yet implemented")
    }

    private fun interpretInstanceInitializerCall(initializerCall: IrInstanceInitializerCall): IrElement? {
        TODO("Not yet implemented")
    }

    private fun interpretBody(body: IrBody): IrElement? {
        return when (body) {
            is IrBlockBody -> body.factory.createBlockBody(body.startOffset, body.endOffset) {
                this.statements.addAll(body.statements.map { it.interpret() as? IrStatement ?: it })
            }
            is IrExpressionBody -> body.factory.createExpressionBody(
                body.startOffset, body.endOffset, body.expression.interpret() as? IrExpression ?: body.expression
            )
            is IrSyntheticBody -> body
            else -> null
        }
    }

    private fun interpretBlock(block: IrBlock): IrElement? {
        return when (block) {
            is IrBlockImpl -> {
                val statements = block.statements.map { it.interpret() as? IrStatement ?: it }
                IrBlockImpl(block.startOffset, block.endOffset, block.type, block.origin, statements)
            }
            else -> return null
        }
    }

    private fun interpretReturn(expression: IrReturn): IrElement? {
        expression.value = expression.value.interpret() as? IrExpression ?: return null
        return expression
    }

    private fun interpretConst(irConst: IrConst<*>): IrElement = irConst

    private fun interpretBranch(branch: IrBranch): IrElement? {
        branch.condition = branch.condition.interpret() as? IrExpression ?: return null
        if (branch.condition.let { it is IrConst<*> && it.value == true }) {
            return branch.result.interpret() as? IrExpression ?: return null
        }
        return null
    }

    private fun interpretWhen(expression: IrWhen): IrElement {
        return expression.branches.firstNotNullResult { it.interpret() } ?: expression
    }
}
