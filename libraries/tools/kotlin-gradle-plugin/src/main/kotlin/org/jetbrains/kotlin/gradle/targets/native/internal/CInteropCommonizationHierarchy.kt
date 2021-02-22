/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.internal

import org.jetbrains.kotlin.descriptors.commonizer.CommonizerTarget
import org.jetbrains.kotlin.descriptors.commonizer.SharedCommonizerTarget

internal data class CInteropCommonizerTarget(
    val commonizerTarget: SharedCommonizerTarget,
    val cinterops: Set<CInteropIdentifier>
)

// TODO NOW: Test!!!
internal operator fun CInteropCommonizerTarget.contains(other: CInteropCommonizerTarget): Boolean {
    return other.commonizerTarget in commonizerTarget && cinterops.containsAll(other.cinterops)
}

private operator fun CommonizerTarget.contains(other: CommonizerTarget): Boolean {
    if (this == other) return true
    if (this !is SharedCommonizerTarget) return false
    return targets.any { child -> other in child }
}
