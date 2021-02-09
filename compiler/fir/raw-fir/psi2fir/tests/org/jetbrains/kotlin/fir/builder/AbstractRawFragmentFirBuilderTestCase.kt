/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.builder

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.Getter
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.PathUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.realPsi
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.session.FirSessionFactory
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.testFramework.KtParsingTestCase
import org.jetbrains.kotlin.test.util.KtTestUtil

abstract class AbstractRawFragmentFirBuilderTestCase : KtParsingTestCase(
    "",
    "kt",
    KotlinParserDefinition()
) {
    override fun getTestDataPath() = KtTestUtil.getHomeDirectory()

    protected fun createKtFile(filePath: String): KtFile {
        myFileExt = FileUtilRt.getExtension(PathUtil.getFileName(filePath))
        val text = loadFile(filePath)
        return (createFile(filePath, text) as KtFile).apply {
            myFile = this
        }
    }

    private class SimpleFirMapper : FirVisitorVoid(), PsiToFirElementMapper {
        private val result = mutableMapOf<PsiElement, FirElement>()

        override fun visitElement(element: FirElement) {
            val psi = element.realPsi
            if (result.containsKey(psi)) return
            if (result.containsValue(element)) return
            if (psi != null) {
                result[psi] = element
            }
            element.acceptChildren(this)
        }

        override fun mapToFirElement(ktElement: KtElement): FirElement? {
            return result[ktElement]
        }
    }

    fun doRawFirTest(filePath: String) {

        val text = loadFile(filePath)
        if (InTextDirectivesUtils.isDirectiveDefined(text, "FIR_FRAGMENTS_IGNORE")) return
        val successCountString = InTextDirectivesUtils.findStringWithPrefixes(text, "FIR_FRAGMENTS_COUNT")
        TestCase.assertNotNull(successCountString)
        val successCount = successCountString!!.toInt()
        var actuallySuccess = 0

        val ktFile = createKtFile(filePath)
        val session = FirSessionFactory.createEmptySession()

        val firFile = RawFirBuilder(
            session = session,
            baseScopeProvider = StubFirScopeProvider,
            mode = RawFirBuilderMode.NORMAL,
        ).buildFirFile(ktFile)

        val firMap = SimpleFirMapper()
        firFile.accept(firMap)

        val testSeq = sequence {
            for (block in PsiTreeUtil.collectElementsOfType(ktFile, KtBlockExpression::class.java)) {
                yield(block)
                yieldAll(block.statements)
            }
            yieldAll(PsiTreeUtil.collectElementsOfType(ktFile, KtDeclaration::class.java))
            yieldAll(PsiTreeUtil.collectElementsOfType(ktFile, KtLambdaExpression::class.java))
            yieldAll(PsiTreeUtil.collectElementsOfType(ktFile, KtLoopExpression::class.java))
            yieldAll(PsiTreeUtil.collectElementsOfType(ktFile, KtAnnotatedExpression::class.java))
            yieldAll(PsiTreeUtil.collectElementsOfType(ktFile, KtNamedFunction::class.java))
        }
        for (expression in testSeq.distinct()) {

            if (expression !is KtExpression) continue

            val firElement = firMap.mapToFirElement(expression) ?: continue
            if (firElement.realPsi != expression) continue

            val firFragmentElement = tryBuildFirFragment(
                session = session,
                baseScopeProvider = StubFirScopeProvider,
                psiToFirElementMapper = firMap,
                targetExpression = expression,
                anchorExpression = expression,
                moveAnchorToAcceptable = false
            )

            val desugaredFragment = when (expression) {
                is KtForExpression -> (firFragmentElement as? FirBlock)?.statements?.get(1)
                is KtDestructuringDeclaration -> (firFragmentElement as FirBlock).statements.firstOrNull()
                else -> firFragmentElement
            }

            val fragmentRender = desugaredFragment?.render() ?: continue
            val elementRender = firElement.render()

            TestCase.assertEquals(elementRender, fragmentRender)

            actuallySuccess++
        }

        TestCase.assertEquals(successCount, actuallySuccess)
    }

    override fun tearDown() {
        super.tearDown()
        FileTypeRegistry.ourInstanceGetter = Getter<FileTypeRegistry> { FileTypeManager.getInstance() }
    }
}