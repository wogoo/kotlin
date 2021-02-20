/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package foo

import bar.BarConstant
import bar.BarDummy
import foo.FooDummy.Companion.FOO_DUMMY_CONSTANT


fun callObjectConstant() {
    println("Object constant: ${BarConstant.CONSTANT}")
}

fun callImportedCompanionConstant() {
    println("Imported companion constant: ${FOO_DUMMY_CONSTANT}")
}

fun callCompanionConstant() {
    println("Called companion constant: ${BarDummy.BAR_DYMMY_CONSTANT}")
}

fun main() {
    callCompanionConstant()
    callImportedCompanionConstant()
    callObjectConstant()
}
