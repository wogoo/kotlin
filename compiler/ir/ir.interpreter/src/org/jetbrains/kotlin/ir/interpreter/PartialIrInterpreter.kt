/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.interpreter

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.interpreter.checker.EvaluationMode
import org.jetbrains.kotlin.ir.interpreter.stack.StackImpl
import org.jetbrains.kotlin.ir.interpreter.stack.Variable
import org.jetbrains.kotlin.ir.interpreter.state.Primitive
import org.jetbrains.kotlin.ir.interpreter.state.State
import org.jetbrains.kotlin.ir.types.isArray
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.fileOrNull
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult
import kotlin.Exception

// This class is an addition to IrInterpreter. The same logic can be implemented inside IrInterpreter with some kind of flag.
// Visitor methods will return null if we cannot continue to interpret given statement
class PartialIrInterpreter(
    val irBuiltIns: IrBuiltIns, bodyMap: Map<IdSignature, IrBody> = emptyMap(), private val mode: EvaluationMode = EvaluationMode.WITH_ANNOTATIONS
) {
    private val stack = StackImpl()
    private val interpreter = IrInterpreter(irBuiltIns, bodyMap, stack)

    private class Result(val state: State?, val element: IrElement?) {
        fun asExpression() = element as? IrExpression
        fun asStatement() = element as? IrStatement

        companion object {
            fun empty() = Result(null, null)
        }
    }

    private fun IrElement.toResult() = Result(null, this)

    // This method will evaluate and replace some of function statements
    // TODO how debugger will behave?
    fun interpret(irFunction: IrFunction) {
        stack.clean(irFunction.fileOrNull)
        irFunction.body = irFunction.body?.interpret()?.element as? IrBody ?: irFunction.body
    }

    private fun interpret(irExpression: IrExpression): Result {
        return with(interpreter) {
            when (val returnLabel = irExpression.interpret().returnLabel) {
                ReturnLabel.REGULAR -> Result(stack.popReturnValue(), irExpression)
                ReturnLabel.EXCEPTION -> TODO()
                else -> TODO("$returnLabel not supported as result of interpretation")
            }
        }
    }

    private fun IrElement.interpret(): Result {
        return try {
            when (this) {
                //is IrSimpleFunction -> interpretFunction(this)
                is IrCall -> visitCall(this, null)
                is IrConstructorCall -> visitConstructorCall(this, null)
                is IrEnumConstructorCall -> visitEnumConstructorCall(this, null)
                is IrDelegatingConstructorCall -> visitDelegatingConstructorCall(this, null)
                is IrInstanceInitializerCall -> visitInstanceInitializerCall(this, null)
                is IrBody -> visitBody(this, null)
                is IrBlock -> visitBlock(this, null)
                is IrReturn -> visitReturn(this, null)
                is IrSetField -> visitSetField(this, null)
                is IrGetField -> visitGetField(this, null)
                is IrGetValue -> visitGetValue(this, null)
//                is IrGetObjectValue -> interpretGetObjectValue(this)
//                is IrGetEnumValue -> interpretGetEnumValue(this)
//                is IrEnumEntry -> interpretEnumEntry(this)
                is IrConst<*> -> visitConst(this, null)
                is IrVariable -> visitVariable(this, null)
                is IrSetVariable -> visitSetVariable(this, null)
                is IrTypeOperatorCall -> visitTypeOperator(this, null)
                //is IrBranch -> visitBranch(this, null)
                is IrWhileLoop -> visitWhileLoop(this, null)
//                is IrDoWhileLoop -> visitDoWhileLoop(this, null)
                is IrWhen -> visitWhen(this, null)
//                is IrBreak -> interpretBreak(this)
//                is IrContinue -> interpretContinue(this)
                is IrVararg -> visitVararg(this, null)
//                is IrSpreadElement -> visitSpreadElement(this, null)
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
            throw e
        }
    }

    private fun visitCall(expression: IrCall, data: Nothing?): Result {
        if (!mode.canEvaluateFunction(expression.symbol.owner)) return expression.toResult()
        expression.dispatchReceiver = expression.dispatchReceiver?.let { it.interpret().asExpression() ?: return expression.toResult() }
        expression.extensionReceiver = expression.extensionReceiver?.let { it.interpret().asExpression() ?: return expression.toResult() }
        for (i in 0 until expression.valueArgumentsCount) {
            val argument = expression.getValueArgument(i) ?: continue
            expression.putValueArgument(i, argument.interpret().asExpression() ?: return expression.toResult())
        }

        return interpret(expression)
    }

    private fun visitConstructorCall(expression: IrConstructorCall, data: Nothing?): Result {
        if (!mode.canEvaluateFunction(expression.symbol.owner)) return expression.toResult()
        for (i in 0 until expression.valueArgumentsCount) {
            val argument = expression.getValueArgument(i) ?: continue
            expression.putValueArgument(i, argument.interpret().asExpression() ?: return expression.toResult())
        }

        return interpret(expression)
    }

    private fun visitEnumConstructorCall(expression: IrEnumConstructorCall, data: Nothing?): Result {
        TODO("Not yet implemented")
    }

    private fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall, data: Nothing?): Result {
        return expression.toResult()
    }

    private fun visitInstanceInitializerCall(expression: IrInstanceInitializerCall, data: Nothing?): Result {
        return expression.toResult()
    }

    private fun visitBody(body: IrBody, data: Nothing?): Result {
        return when (body) {
            is IrBlockBody -> visitBlockBody(body, data).removeUnusedStatements()
            is IrExpressionBody -> visitExpressionBody(body, data)
            is IrSyntheticBody -> visitSyntheticBody(body, data)
            else -> Result.empty()
        }
    }

    private fun visitBlockBody(body: IrBlockBody, data: Nothing?): IrBlockBody {
        return body.factory.createBlockBody(body.startOffset, body.endOffset) {
            this.statements.addAll(body.statements.map { it.interpret().asStatement() ?: it })
        }
    }

    private fun visitExpressionBody(body: IrExpressionBody, data: Nothing?): Result {
        return body.factory.createExpressionBody(
            body.startOffset, body.endOffset, body.expression.interpret().asExpression() ?: body.expression
        ).toResult()
    }

    private fun visitSyntheticBody(body: IrSyntheticBody, data: Nothing?): Result = body.toResult()

    private fun visitBlock(expression: IrBlock, data: Nothing?): Result {
        return when (expression) {
            is IrBlockImpl -> {
                val statements = mutableListOf<IrStatement>()
                stack.newFrame(asSubFrame = true) {
                    statements.addAll(expression.statements.map { it.interpret().asStatement() ?: it })
                    Next
                }
                IrBlockImpl(expression.startOffset, expression.endOffset, expression.type, expression.origin, statements)
                    .removeUnusedStatements()
            }
            else -> return Result.empty()
        }
    }

    private fun IrStatementContainer.removeUnusedStatements(): Result {
        if (this !is IrElement) return Result.empty()
        if (this.statements.isEmpty()) return this.toResult()

        val variablesToUsage = mutableMapOf<IrVariable, Int>()
        // inline const and count all appearances of IrVariable node
        for (i in 0 until this.statements.size) {
            val statement = this.statements[i]
            if (statement is IrVariable) {
                if (statement.initializer is IrConst<*>) {
                    variablesToUsage[statement] = 0
                    // for each statement, find GetValue and replace with IrConst
                    this.accept(object : IrElementTransformerVoid() {
                        override fun visitGetValue(expression: IrGetValue): IrExpression {
                            if (expression.symbol == statement.symbol) return statement.initializer!!
                            return super.visitGetValue(expression)
                        }
                    }, null)
                } else {
                    this.accept(object : IrElementTransformerVoid() {
                        override fun visitGetValue(expression: IrGetValue): IrExpression {
                            if (expression.symbol == statement.symbol) {
                                variablesToUsage[statement] = variablesToUsage.getOrDefault(statement, 0) + 1
                            }
                            return super.visitGetValue(expression)
                        }
                    }, null)
                }
            }
        }

        for ((variable, usage) in variablesToUsage) {
            when (usage) {
                // remove if value was inlined
                0 -> this.statements.remove(variable)
                // replace single appearance with initializer and remove
                1 -> {
                    this.accept(object : IrElementTransformerVoid() {
                        override fun visitGetValue(expression: IrGetValue): IrExpression {
                            if (expression.symbol == variable.symbol) return variable.initializer!!
                            return super.visitGetValue(expression)
                        }
                    }, null)
                    this.statements.remove(variable)
                }
            }
        }

        if (this is IrBlock && !this.type.isUnit() && this.statements.size == 1) {
            return this.statements.first().toResult()
        }
        // TODO do we need IrBlockBody to IrExpressionBody transformation? Must convert `return` to its expression
        /*if (this is IrBlockBody && this.statements.singleOrNull() is IrExpression) {
            return this.factory.createExpressionBody(this.startOffset, this.endOffset, this.statements.single() as IrExpression)
        }*/
        return this.toResult()
    }

    private fun visitReturn(expression: IrReturn, data: Nothing?): Result {
        expression.value = expression.value.interpret().asExpression() ?: return Result.empty()
        return expression.toResult()
    }

    private fun visitSetField(expression: IrSetField, data: Nothing?): Result {
        // TODO
        return expression.toResult()
    }

    private fun visitGetField(expression: IrGetField, data: Nothing?): Result {
        // TODO
        return expression.toResult()
    }

    private fun visitGetValue(expression: IrGetValue, data: Nothing?): Result {
        if (stack.contains(expression.symbol)) {
            val value = stack.getVariable(expression.symbol).state
            if (value is Primitive<*> && !value.type.isArray()) return Result(value, value.value.toIrConst(value.type)) // if value on stack is primitive then we can inline it
            return expression.toResult()   // if value is complex (for example some object) then we still can continue interpretation, but cannot inline
        }
        return Result.empty() // if value isn't present on stack, then we cannot continue interpretation
    }

    private fun <T> visitConst(expression: IrConst<T>, data: Nothing?): Result = interpret(expression)

    private fun visitVariable(declaration: IrVariable, data: Nothing?): Result {
        if (declaration.initializer == null) {
            stack.addVar(Variable(declaration.symbol))
            return declaration.toResult()
        }
        val result = declaration.initializer?.interpret() ?: return Result.empty()
        declaration.initializer = result.asExpression()
        if (result.state != null) stack.addVar(Variable(declaration.symbol, result.state))
        return declaration.toResult()
    }

    private fun visitSetVariable(expression: IrSetVariable, data: Nothing?): Result {
        if (stack.contains(expression.symbol)) {
            val result = expression.value.interpret()
            expression.value = result.asExpression() ?: return Result.empty()
            stack.getVariable(expression.symbol).apply { this.state = result.state ?: return Result.empty() }
            return expression.toResult()
        }
        return expression.toResult()
    }

    private fun visitTypeOperator(expression: IrTypeOperatorCall, data: Nothing?): Result {
        return expression.toResult()
    }

    private fun visitWhileLoop(loop: IrWhileLoop, data: Nothing?): Result {
        while (true) {
            val condition = loop.condition.interpret().state
            if (condition != null && condition is Primitive<*> && condition.value == false) break
            else if ((loop.condition as? IrConst<*>)?.value == false) break
            loop.body?.interpret()
        }
        // TODO how to remove loop if it was fold
        // in inline interpreter we must inline loop instead of folding
        return loop.toResult()
    }

    /*private fun visitDoWhileLoop(loop: IrDoWhileLoop, data: Nothing?): Result? {
        return super.visitDoWhileLoop(loop, data)
    }*/

    private fun visitWhen(expression: IrWhen, data: Nothing?): Result {
        expression.branches.firstNotNullResult { branch ->
            branch.condition = branch.condition.interpret().asExpression() ?: branch.condition
            if (branch.condition !is IrConst<*>) return Result.empty()
            if ((branch.condition as IrConst<*>).value == true) {
                return branch.result.interpret()
            }
            null
        }
        return Result.empty()
    }

    private fun visitVararg(expression: IrVararg, data: Nothing?): Result {
        expression.elements.forEachIndexed { index, element ->
            element.interpret().let { expression.putElement(index, it.element as IrVarargElement) }
        }
        return expression.toResult()
    }

    /*private fun visitSpreadElement(spread: IrSpreadElement, data: Nothing?): Result? {
        return super.visitSpreadElement(spread, data)
    }*/
}
