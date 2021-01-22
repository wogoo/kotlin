/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.lower

import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.interpreter.PartialIrInterpreter
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

@Suppress("UNCHECKED_CAST")
class PartialCompileTimeCalculationLowering(val context: CommonBackendContext) : FileLoweringPass {
    private val bodyMap = context.configuration[CommonConfigurationKeys.IR_BODY_MAP] as? Map<IdSignature, IrBody> ?: emptyMap()
    private val partialInterpreter = PartialIrInterpreter(context.ir.irModule.irBuiltins, bodyMap)

    override fun lower(irFile: IrFile) {
        if (!context.configuration.languageVersionSettings.supportsFeature(LanguageFeature.PartialCompileTimeCalculations)) return
        if (irFile.fileEntry.name.contains("/kotlin/libraries/")) return
        irFile.transformChildren(Transformer(irFile), null)
    }

    private inner class Transformer(private val irFile: IrFile) : IrElementTransformerVoid() {
        private fun IrElement.report(before: String, after: String) {
            if (before == after) return
            context.report(this, irFile, "\n$after", false)
        }

        override fun visitFunction(declaration: IrFunction): IrStatement {
            val before = declaration.dump()
            partialInterpreter.interpret(declaration)
            val after = declaration.dump()
            declaration.report(before, after)
            return declaration
        }
    }
}