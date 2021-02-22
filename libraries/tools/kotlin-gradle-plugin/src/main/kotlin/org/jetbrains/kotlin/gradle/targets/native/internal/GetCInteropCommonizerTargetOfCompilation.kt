/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.internal

import org.gradle.api.Project
import org.jetbrains.kotlin.descriptors.commonizer.SharedCommonizerTarget
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtensionOrNull
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinSharedNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.sources.getSourceSetHierarchy

// TODO NOW: Test
internal fun Project.getCInteropCommonizerTarget(compilation: KotlinSharedNativeCompilation): CInteropCommonizerTarget? {
    val multiplatformExtension = multiplatformExtensionOrNull ?: return null

    val nativeCompilations = multiplatformExtension.targets
        .flatMap { target -> target.compilations }
        .filterIsInstance<KotlinNativeCompilation>()

    val dependingNativeCompilations = nativeCompilations.filter { nativeCompilation ->
        nativeCompilation.allParticipatingSourceSets().containsAll(compilation.allParticipatingSourceSets())
    }

    if (dependingNativeCompilations.any { it.cinterops.isEmpty() }) {
        /* At least one native compilation has no cinterops. No need to commonize anything. No shared API possible */
        return null
    }

    val cinterops = dependingNativeCompilations
        .flatMap { nativeCompilation -> nativeCompilation.cinterops }
        .map { cinterop -> cinterop.identifier }
        .toSet()

    return CInteropCommonizerTarget(
        commonizerTarget = getCommonizerTarget(compilation) as? SharedCommonizerTarget ?: return null,
        cinterops = cinterops
    )
}

// TODO: Make allKotlinSourceSets behave in the intuition of other source sets
private fun KotlinCompilation<*>.allParticipatingSourceSets(): Set<KotlinSourceSet> {
    return allKotlinSourceSets + defaultSourceSet.getSourceSetHierarchy()
}
