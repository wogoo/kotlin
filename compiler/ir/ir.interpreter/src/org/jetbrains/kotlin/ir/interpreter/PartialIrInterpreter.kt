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
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import kotlin.Exception

// This class is an addition to IrInterpreter. The same logic can be implemented inside IrInterpreter with some kind of flag.
// Visitor methods will return null if we cannot continue to interpret given statement
class PartialIrInterpreter(
    val irBuiltIns: IrBuiltIns, bodyMap: Map<IdSignature, IrBody> = emptyMap(), private val mode: EvaluationMode = EvaluationMode.WITH_ANNOTATIONS
) {
    private val interpreterStack = StackImpl()
    private val localStack = Stack()
    private val interpreter = IrInterpreter(irBuiltIns, bodyMap, interpreterStack)

    private class Stack {
//        private val pool: MutableList<Result> = mutableListOf()
//
//        fun push(result: Result): PartialResult {
//            pool.add(result)
//            return Next
//        }
//
//        fun push(element: IrElement?, executionResult: PartialResult = Next): PartialResult {
//            pool.add(Result(null, element))
//            return executionResult
//        }
//
//        fun pop(): Result? {
//            return pool.removeLastOrNull()
//        }
//
//        fun wasEvaluated() = pool.lastOrNull()?.state != null
    }

    private class Result(val state: State?, val element: IrElement?) {
        fun asExpression() = element as? IrExpression
        fun asStatement() = element as? IrStatement
    }

    //private fun IrElement.toResult() = Result(null, this)

    // This method will evaluate and replace some of function statements
    // TODO how debugger will behave?
    fun interpret(irFunction: IrFunction) {
        interpreterStack.clean(irFunction.fileOrNull)
//        irFunction.body?.interpret() // TODO check return result
//        irFunction.body = localStack.pop()?.element as? IrBody ?: irFunction.body
    }
//
//    private fun evaluate(irExpression: IrExpression): PartialResult {
//        return with(interpreter) {
//            when (val returnLabel = irExpression.interpret().returnLabel) {
//                ReturnLabel.REGULAR -> localStack.push(Result(interpreterStack.popReturnValue(), irExpression))
//                ReturnLabel.EXCEPTION -> TODO()
//                else -> TODO("$returnLabel not supported as result of interpretation")
//            }
//        }
//    }
//
//    private fun IrElement.interpret(): PartialResult {
//        try {
//            val executionResult = when (this) {
//                //is IrSimpleFunction -> interpretFunction(this)
//                is IrCall -> visitCall(this, null)
//                is IrConstructorCall -> visitConstructorCall(this, null)
//                is IrEnumConstructorCall -> visitEnumConstructorCall(this, null)
//                is IrDelegatingConstructorCall -> visitDelegatingConstructorCall(this, null)
//                is IrInstanceInitializerCall -> visitInstanceInitializerCall(this, null)
//                is IrBody -> visitBody(this, null)
//                is IrBlock -> visitBlock(this, null)
//                is IrReturn -> visitReturn(this, null)
//                is IrSetField -> visitSetField(this, null)
//                is IrGetField -> visitGetField(this, null)
//                is IrGetValue -> visitGetValue(this, null)
////                is IrGetObjectValue -> interpretGetObjectValue(this)
////                is IrGetEnumValue -> interpretGetEnumValue(this)
////                is IrEnumEntry -> interpretEnumEntry(this)
//                is IrConst<*> -> visitConst(this, null)
//                is IrVariable -> visitVariable(this, null)
//                is IrSetVariable -> visitSetVariable(this, null)
//                is IrTypeOperatorCall -> visitTypeOperator(this, null)
//                is IrBranch -> visitBranch(this, null)
//                is IrWhileLoop -> visitWhileLoop(this, null)
////                is IrDoWhileLoop -> visitDoWhileLoop(this, null)
//                is IrWhen -> visitWhen(this, null)
////                is IrBreak -> interpretBreak(this)
////                is IrContinue -> interpretContinue(this)
//                is IrVararg -> visitVararg(this, null)
////                is IrSpreadElement -> visitSpreadElement(this, null)
////                is IrTry -> interpretTry(this)
////                is IrCatch -> interpretCatch(this)
////                is IrThrow -> interpretThrow(this)
////                is IrStringConcatenation -> interpretStringConcatenation(this)
////                is IrFunctionExpression -> interpretFunctionExpression(this)
////                is IrFunctionReference -> interpretFunctionReference(this)
////                is IrPropertyReference -> interpretPropertyReference(this)
////                is IrClassReference -> interpretClassReference(this)
////                is IrComposite -> interpretComposite(this)
//
//                else -> TODO("${this.javaClass} not supported")
//            }
//
//            return executionResult.getNextLabel(this) { this@getNextLabel.interpret() }
//        } catch (e: Exception) {
//            throw e
//        }
//    }
//
//    private fun visitCall(expression: IrCall, data: Nothing?): PartialResult {
//        var canEvaluate = true
//        expression.dispatchReceiver?.interpret()?.let {
//            if (it.returnLabel == PartialReturnLabel.CALCULATED || it.returnLabel == PartialReturnLabel.EVALUATED) {
//                expression.dispatchReceiver = localStack.pop()?.asExpression()
//            } else if (it.returnLabel == PartialReturnLabel.NOT_INTERPRETABLE) {
//                // remain the same
//            } else return it
//        } // if label == CALCULATED || label == EVALUATED => replace
//        //if (expression.dispatchReceiver != null && !localStack.wasEvaluated()) canEvaluate = false
//        //localStack.pop()?.asExpression()?.let { expression.dispatchReceiver = it }
//
//        expression.extensionReceiver?.interpret()?.check { return it }
//        if (expression.extensionReceiver != null && !localStack.wasEvaluated()) canEvaluate = false
//        localStack.pop()?.asExpression()?.let { expression.extensionReceiver = it }
//
//        for (i in 0 until expression.valueArgumentsCount) {
//            expression.getValueArgument(i)?.interpret()?.check { return it }
//            if (expression.getValueArgument(i) != null && !localStack.wasEvaluated()) canEvaluate = false
//            localStack.pop()?.asExpression()?.let { expression.putValueArgument(i, it) }
//        }
//
//        if (!canEvaluate || !mode.canEvaluateFunction(expression.symbol.owner)) return localStack.push(expression) // return CALCULATED
//        return evaluate(expression)
//    }
//
//    private fun visitConstructorCall(expression: IrConstructorCall, data: Nothing?): PartialResult {
//        var canEvaluate = true
//        for (i in 0 until expression.valueArgumentsCount) {
//            expression.getValueArgument(i)?.interpret()?.check { canEvaluate = false }
//            if (expression.getValueArgument(i) != null && !localStack.wasEvaluated()) canEvaluate = false
//            localStack.pop()?.asExpression()?.let { expression.putValueArgument(i, it) }
//        }
//
//        if (!canEvaluate || !mode.canEvaluateFunction(expression.symbol.owner)) return localStack.push(expression)
//        return evaluate(expression)
//    }
//
//    private fun visitEnumConstructorCall(expression: IrEnumConstructorCall, data: Nothing?): PartialResult {
//        TODO("Not yet implemented")
//    }
//
//    private fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall, data: Nothing?): PartialResult {
//        return localStack.push(expression)
//    }
//
//    private fun visitInstanceInitializerCall(expression: IrInstanceInitializerCall, data: Nothing?): PartialResult {
//        return localStack.push(expression)
//    }
//
//    private fun visitBody(body: IrBody, data: Nothing?): PartialResult {
//        return when (body) {
//            is IrBlockBody -> visitBlockBody(body, data).apply {
//                (localStack.pop()?.element as IrStatementContainer).removeUnusedStatements()
//            }
//            is IrExpressionBody -> visitExpressionBody(body, data)
//            is IrSyntheticBody -> visitSyntheticBody(body, data)
//            else -> localStack.push(body)
//        }
//    }
//
//    private fun visitBlockBody(body: IrBlockBody, data: Nothing?): PartialResult {
//        var executionResult: PartialResult = Next
//        val statements = mutableListOf<IrStatement>()
//        for (statement in body.statements) {
//            executionResult = statement.interpret()
//            //statements.add(localStack.pop()?.asStatement() ?: statement)
//            localStack.pop()?.asStatement()?.let { statements.add(it) } // if label == NOT_INTERPRETABLE => statement; if label == CALCULATED => local.pop()
//            if (executionResult.returnLabel != ReturnLabel.REGULAR) break
//        }
//        localStack.push(body.factory.createBlockBody(body.startOffset, body.endOffset, statements))
//        return executionResult
//    }
//
//    private fun visitExpressionBody(body: IrExpressionBody, data: Nothing?): PartialResult {
//        body.expression.interpret()
//        localStack.pop()?.asExpression()?.let { body.expression = it }
//        return localStack.push(body)
//    }
//
//    private fun visitSyntheticBody(body: IrSyntheticBody, data: Nothing?): PartialResult = localStack.push(body)
//
//    private fun visitBlock(expression: IrBlock, data: Nothing?): PartialResult {
//        return when (expression) {
//            is IrBlockImpl -> {
//                var executionResult: PartialResult = Next
//                val statements = mutableListOf<IrStatement>()
//                interpreterStack.newFrame(asSubFrame = true) {
//                    for (statement in expression.statements) {
//                        executionResult = statement.interpret()
//                        //statements.add(localStack.pop()?.asStatement() ?: statement)
//                        localStack.pop()?.asStatement()?.let { statements.add(it) } // TODO
//                        if (executionResult.returnLabel != ReturnLabel.REGULAR) break
//                    }
//                    Next
//                }
//                IrBlockImpl(expression.startOffset, expression.endOffset, expression.type, expression.origin, statements)
//                    .removeUnusedStatements()
//            }
//            else -> return localStack.push(expression)
//        }
//    }
//
//    private fun IrStatementContainer.removeUnusedStatements(): PartialResult {
//        if (this !is IrElement) return Next
//        if (this.statements.isEmpty()) return localStack.push(this)
//
//        val variablesToUsage = mutableMapOf<IrVariable, Int>()
//        // inline const and count all appearances of IrVariable node
//        for (i in 0 until this.statements.size) {
//            val statement = this.statements[i]
//            if (statement is IrVariable) {
//                if (interpreterStack.contains(statement.symbol) && interpreterStack.getVariable(statement.symbol).state is Primitive<*>) {
//                    variablesToUsage[statement] = 0
//                    // for each statement, find GetValue and replace with IrConst
//                    this.accept(object : IrElementTransformerVoid() {
//                        override fun visitGetValue(expression: IrGetValue): IrExpression {
//                            val value = interpreterStack.getVariable(statement.symbol).state as Primitive<*>
//                            if (!value.type.isArray() && expression.symbol == statement.symbol) return value.value.toIrConst(value.type)
//                            return super.visitGetValue(expression)
//                        }
//                    }, null)
//                } else {
//                    this.accept(object : IrElementTransformerVoid() {
//                        override fun visitGetValue(expression: IrGetValue): IrExpression {
//                            if (expression.symbol == statement.symbol) {
//                                variablesToUsage[statement] = variablesToUsage.getOrDefault(statement, 0) + 1
//                            }
//                            return super.visitGetValue(expression)
//                        }
//
//                        override fun visitSetVariable(expression: IrSetVariable): IrExpression {
//                            if (expression.symbol == statement.symbol) {
//                                variablesToUsage[statement] = variablesToUsage.getOrDefault(statement, 0) + 1
//                            }
//                            return super.visitSetVariable(expression)
//                        }
//                    }, null)
//                }
//            }
//        }
//
//        for ((variable, usage) in variablesToUsage) {
//            when (usage) {
//                // remove if value was inlined
//                0 -> this.statements.remove(variable)
//                // replace single appearance with initializer and remove
//                1 -> {
//                    this.accept(object : IrElementTransformerVoid() {
//                        override fun visitGetValue(expression: IrGetValue): IrExpression {
//                            if (expression.symbol == variable.symbol) return variable.initializer!!
//                            return super.visitGetValue(expression)
//                        }
//                    }, null)
//                    this.statements.remove(variable)
//                }
//            }
//        }
//
//        if (this is IrBlock && !this.type.isUnit() && this.statements.size == 1) {
//            return localStack.push(this.statements.first())
//        }
//        // TODO do we need IrBlockBody to IrExpressionBody transformation? Must convert `return` to its expression
//        /*if (this is IrBlockBody && this.statements.singleOrNull() is IrExpression) {
//            return this.factory.createExpressionBody(this.startOffset, this.endOffset, this.statements.single() as IrExpression)
//        }*/
//        return localStack.push(this)
//    }
//
//    private fun visitReturn(expression: IrReturn, data: Nothing?): PartialResult {
//        expression.value.interpret().check { return it }
//        expression.value = localStack.pop()?.asExpression() ?: expression.value
//        return localStack.push(expression, Return)
//    }
//
//    private fun visitSetField(expression: IrSetField, data: Nothing?): PartialResult {
//        // TODO
//        return localStack.push(expression)
//    }
//
//    private fun visitGetField(expression: IrGetField, data: Nothing?): PartialResult {
//        // TODO
//        return localStack.push(expression)
//    }
//
//    private fun visitGetValue(expression: IrGetValue, data: Nothing?): PartialResult {
//        if (interpreterStack.contains(expression.symbol)) {
//            val value = interpreterStack.getVariable(expression.symbol).state
//            if (value is Primitive<*> && !value.type.isArray()) return localStack.push(Result(value, value.value.toIrConst(value.type))) // if value on stack is primitive then we can inline it // label = EVALUATED
//            return localStack.push(expression)   // if value is complex (for example some object) then we still can continue interpretation, but cannot inline // label = CALCULATED
//        }
//        return localStack.push(Result(null, null)) // if value isn't present on stack, then we cannot continue interpretation // label = NOT_INTERPRETABLE
//    }
//
//    private fun <T> visitConst(expression: IrConst<T>, data: Nothing?): PartialResult = evaluate(expression)
//
//    private fun visitVariable(declaration: IrVariable, data: Nothing?): PartialResult {
//        /*declaration.deepCopyWithSymbols() { it1, it2 ->
//            DeepCopyIrTreeWithSymbols(it1, it2, symbolRenamer = SymbolRenamer.DEFAULT)
//        }*/
//        if (declaration.initializer == null) {
//            interpreterStack.addVar(Variable(declaration.symbol))
//            return localStack.push(declaration)
//        }
//        declaration.initializer?.interpret()?.check { return it }
//        val result = localStack.pop()
//        declaration.initializer = result?.asExpression() ?: declaration.initializer
//        if (result?.state != null) interpreterStack.addVar(Variable(declaration.symbol, result.state))
//        return localStack.push(declaration)
//    }
//
//    private fun visitSetVariable(expression: IrSetVariable, data: Nothing?): PartialResult {
//        if (interpreterStack.contains(expression.symbol)) {
//            expression.value.interpret().check { return it }
//            val result = localStack.pop()?.apply { this.asExpression()?.let { expression.value = it } }
//            if (result?.state == null) {
//                interpreterStack.removeVar(expression.symbol)
//                return localStack.push(expression)
//            }
//            interpreterStack.getVariable(expression.symbol).state = result.state
//        }
//        return localStack.push(expression)
//    }
//
//    private fun visitTypeOperator(expression: IrTypeOperatorCall, data: Nothing?): PartialResult {
//        return localStack.push(expression)
//    }
//
//    private fun visitWhileLoop(loop: IrWhileLoop, data: Nothing?): PartialResult {
//        while (true) {
//            loop.condition.interpret().check { return it }
//            val condition = localStack.pop()?.state
//            when {
//                condition != null && condition is Primitive<*> && condition.value == false -> break
//                (loop.condition as? IrConst<*>)?.value == false -> break
//            }
//            loop.body?.interpret()?.check { return it }
//        }
//        // TODO how to remove loop if it was fold
//        // in inline interpreter we must inline loop instead of folding
//        return localStack.push(loop)
//    }
//
//    /*private fun visitDoWhileLoop(loop: IrDoWhileLoop, data: Nothing?): Result? {
//        return super.visitDoWhileLoop(loop, data)
//    }*/
//
//    private fun visitBranch(expression: IrBranch, data: Nothing?): PartialResult {
//        val executionResult = expression.condition.interpret().check { return it }
//        val condition = localStack.pop()?.asExpression() as? IrConst<*> ?: return BreakWhen
//        if (condition.value == true) {
//            expression.result.interpret().check { return it }
//            return localStack.push(localStack.pop()?.element, BreakWhen)
//        }
//        return executionResult
//    }
//
//    private fun visitWhen(expression: IrWhen, data: Nothing?): PartialResult {
//        /*expression.branches.firstNotNullResult { branch ->
//            branch.condition.interpret()
//            branch.condition = branch.condition.interpret().let { localStack.pop()?.asExpression() } ?: branch.condition
//            if (branch.condition !is IrConst<*>) return localStack.push(Result.empty())
//            if ((branch.condition as IrConst<*>).value == true) {
//                return branch.result.interpret()
//            }
//            null
//        }*/
//        var executionResult: PartialResult = Next
//        for (branch in expression.branches) {
//            executionResult = branch.interpret().check { return it }
//        }
//        // TODO if all branches returned false, then we must delete `when`
//        return executionResult
//    }
//
//    private fun visitVararg(expression: IrVararg, data: Nothing?): PartialResult {
//        expression.elements.forEachIndexed { index, element ->
//            element.interpret().check { return it }
//            expression.putElement(index, localStack.pop()?.element as IrVarargElement)
//        }
//        return localStack.push(expression)
//    }
//
//    /*private fun visitSpreadElement(spread: IrSpreadElement, data: Nothing?): Result? {
//        return super.visitSpreadElement(spread, data)
//    }*/
}
