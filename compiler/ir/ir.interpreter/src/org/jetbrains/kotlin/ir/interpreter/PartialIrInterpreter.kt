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
import org.jetbrains.kotlin.ir.types.isArray
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.fileOrNull
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult
import kotlin.Exception

// This class is an addition to IrInterpreter. The same logic can be implemented inside IrInterpreter with some kind of flag.
// Visitor methods will return null if we cannot continue to interpret given statement
class PartialIrInterpreter(
    val irBuiltIns: IrBuiltIns, bodyMap: Map<IdSignature, IrBody> = emptyMap(), private val mode: EvaluationMode = EvaluationMode.WITH_ANNOTATIONS
) : IrElementVisitor<IrElement?, Nothing?> {
    private val stack = StackImpl()
    private val interpreter = IrInterpreter(irBuiltIns, bodyMap, stack)

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
                //--is IrSetField -> interpretSetField(this)
                //--is IrGetField -> interpretGetField(this)
                //--is IrGetValue -> interpretGetValue(this)
//                is IrGetObjectValue -> interpretGetObjectValue(this)
//                is IrGetEnumValue -> interpretGetEnumValue(this)
//                is IrEnumEntry -> interpretEnumEntry(this)
                //--is IrConst<*> -> interpretConst(this)
                //--is IrVariable -> interpretVariable(this)
                //--is IrSetVariable -> interpretSetVariable(this)
                //--is IrTypeOperatorCall -> interpretTypeOperatorCall(this)
                //--is IrBranch -> interpretBranch(this)
                //--is IrWhileLoop -> interpretWhile(this)
                //--is IrDoWhileLoop -> interpretDoWhile(this)
                //--is IrWhen -> interpretWhen(this)
//                is IrBreak -> interpretBreak(this)
//                is IrContinue -> interpretContinue(this)
                //--is IrVararg -> interpretVararg(this)
                //--is IrSpreadElement -> interpretSpreadElement(this)
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

    override fun visitElement(element: IrElement, data: Nothing?): IrElement {
        TODO("${element.javaClass} not supported")
    }

    override fun visitCall(expression: IrCall, data: Nothing?): IrElement {
        if (!mode.canEvaluateFunction(expression.symbol.owner)) return expression
        expression.dispatchReceiver = expression.dispatchReceiver?.let { it.accept(this, data) as? IrExpression ?: return expression }
        expression.extensionReceiver = expression.extensionReceiver?.let { it.accept(this, data) as? IrExpression ?: return expression }
        for (i in 0 until expression.valueArgumentsCount) {
            val argument = expression.getValueArgument(i) ?: continue
            expression.putValueArgument(i, argument.accept(this, data) as? IrExpression ?: return expression)
        }

        return interpreter.interpret(expression, clean = false)
    }

    override fun visitConstructorCall(expression: IrConstructorCall, data: Nothing?): IrElement {
        if (!mode.canEvaluateFunction(expression.symbol.owner)) return expression
        for (i in 0 until expression.valueArgumentsCount) {
            val argument = expression.getValueArgument(i) ?: continue
            expression.putValueArgument(i, argument.accept(this, data) as? IrExpression ?: return expression)
        }

        interpreter.interpret(expression, clean = false) // interpretation result is stored on stack
        return expression
    }

    override fun visitEnumConstructorCall(expression: IrEnumConstructorCall, data: Nothing?): IrElement {
        TODO("Not yet implemented")
    }

    override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall, data: Nothing?): IrElement {
        return expression
    }

    override fun visitInstanceInitializerCall(expression: IrInstanceInitializerCall, data: Nothing?): IrElement {
        return expression
    }

    override fun visitBody(body: IrBody, data: Nothing?): IrElement? {
        return when (body) {
            is IrBlockBody -> visitBlockBody(body, data).removeUnusedStatements()
            is IrExpressionBody -> visitExpressionBody(body, data)
            is IrSyntheticBody -> visitSyntheticBody(body, data)
            else -> null
        }
    }

    override fun visitBlockBody(body: IrBlockBody, data: Nothing?): IrBlockBody {
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
                val statements = mutableListOf<IrStatement>()
                stack.newFrame(asSubFrame = true) {
                    statements.addAll(expression.statements.map { it.accept(this, data) as? IrStatement ?: it })
                    Next
                }
                IrBlockImpl(expression.startOffset, expression.endOffset, expression.type, expression.origin, statements)
                    .removeUnusedStatements()
            }
            else -> return null
        }
    }

    private fun IrStatementContainer.removeUnusedStatements(): IrElement? {
        if (this !is IrElement) return null
        if (this.statements.isEmpty()) return this

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
            return this.statements.first()
        }
        // TODO do we need IrBlockBody to IrExpressionBody transformation? Must convert `return` to its expression
        /*if (this is IrBlockBody && this.statements.singleOrNull() is IrExpression) {
            return this.factory.createExpressionBody(this.startOffset, this.endOffset, this.statements.single() as IrExpression)
        }*/
        return this
    }

    override fun visitReturn(expression: IrReturn, data: Nothing?): IrElement? {
        expression.value = expression.value.accept(this, data) as? IrExpression ?: return null
        return expression
    }

    override fun visitSetField(expression: IrSetField, data: Nothing?): IrElement {
        // TODO
        return expression
    }

    override fun visitGetField(expression: IrGetField, data: Nothing?): IrElement {
        // TODO
        return expression
    }

    override fun visitGetValue(expression: IrGetValue, data: Nothing?): IrElement? {
        if (stack.contains(expression.symbol)) {
            val value = stack.getVariable(expression.symbol).state
            if (value is Primitive<*> && !value.type.isArray()) return value.value.toIrConst(value.type) // if value on stack is primitive then we can inline it
            return expression   // if value is complex (for example some object) then we still can continue interpretation, but cannot inline
        }
        return null // if value isn't present on stack, then we cannot continue interpretation
    }

    override fun <T> visitConst(expression: IrConst<T>, data: Nothing?): IrElement = expression.apply { interpreter.interpret(this, clean = false) }

    override fun visitVariable(declaration: IrVariable, data: Nothing?): IrElement? {
        if (declaration.initializer == null) {
            stack.addVar(Variable(declaration.symbol))
            return declaration
        }
        declaration.initializer = declaration.initializer?.accept(this, data) as? IrExpression ?: return null
        if (stack.hasReturnValue()) stack.addVar(Variable(declaration.symbol, stack.popReturnValue())) // TODO
        return declaration
    }

    override fun visitSetVariable(expression: IrSetVariable, data: Nothing?): IrElement? {
        if (stack.contains(expression.symbol)) {
            expression.value = expression.value.accept(this, data) as? IrExpression ?: return null
            return expression
        }
        return expression
    }

    override fun visitTypeOperator(expression: IrTypeOperatorCall, data: Nothing?): IrElement {
        return expression
    }

    override fun visitWhileLoop(loop: IrWhileLoop, data: Nothing?): IrElement? {
        while (true) {
            val condition = loop.condition.accept(this, data) as? IrExpression ?: loop.condition
            if ((condition as? IrConst<*>)?.value == false) break
            loop.body?.accept(this, data)
        }
        // TODO how to remove loop if it was fold
        // in inline interpreter we must inline loop instead of folding
        return loop
    }

    override fun visitDoWhileLoop(loop: IrDoWhileLoop, data: Nothing?): IrElement? {
        return super.visitDoWhileLoop(loop, data)
    }

    override fun visitWhen(expression: IrWhen, data: Nothing?): IrElement? {
        return expression.branches.firstNotNullResult { branch ->
            branch.condition = branch.condition.accept(this, data) as? IrExpression ?: branch.condition
            if (branch.condition !is IrConst<*>) return null
            if ((branch.condition as IrConst<*>).value == true) {
                return@firstNotNullResult branch.result.accept(this, data) as? IrExpression
            }
            null
        }
    }

    override fun visitVararg(expression: IrVararg, data: Nothing?): IrElement? {
        expression.elements.forEachIndexed { index, element ->
            element.accept(this, data)?.let { expression.putElement(index, it as IrVarargElement) } ?: return null
        }
        return expression
    }

    override fun visitSpreadElement(spread: IrSpreadElement, data: Nothing?): IrElement? {
        return super.visitSpreadElement(spread, data)
    }
}
