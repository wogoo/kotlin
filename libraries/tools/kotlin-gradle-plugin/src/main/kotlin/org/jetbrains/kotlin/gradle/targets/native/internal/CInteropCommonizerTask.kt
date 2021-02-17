/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.internal

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.compilerRunner.GradleCliCommonizer
import org.jetbrains.kotlin.compilerRunner.konanHome
import org.jetbrains.kotlin.descriptors.commonizer.CommonizerTarget
import org.jetbrains.kotlin.descriptors.commonizer.HierarchicalCommonizerOutputLayout
import org.jetbrains.kotlin.descriptors.commonizer.SharedCommonizerTarget
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtensionOrNull
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinSharedNativeCompilation
import org.jetbrains.kotlin.gradle.targets.native.internal.CInteropCommonizerTask.CInteropGist
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

open class CInteropCommonizerTask : DefaultTask() {

    data class CInteropGist(
        @get:Input val name: String,
        @get:Input val konanTarget: KonanTarget,
        @get:Internal val sourceSets: Provider<Set<KotlinSourceSet>>,
        @get:Classpath val libraryFile: Provider<File>,
        @get:Classpath val dependencies: FileCollection
    ) {
        @Suppress("unused") // Used for UP-TO-DATE check
        @get:Input
        val sourceSetNames = sourceSets.map { sourceSets -> sourceSets.map { sourceSet -> sourceSet.name } }
    }

    @get:Nested
    internal var cinterops = setOf<CInteropGist>()
        private set


    fun from(vararg tasks: CInteropProcess) = from(
        tasks.toList()
            .onEach { task -> this.dependsOn(task) }
            .map { task -> task.toGist() }
    )

    internal fun from(vararg cinterop: CInteropGist) {
        from(cinterop.toList())
    }

    internal fun from(cinterops: List<CInteropGist>) {
        this.cinterops += cinterops
    }

    @TaskAction
    internal fun commonizeCInteropLibraries() {
        val commonizerRequestsForSharedCompilations = project.sharedCInterops(cinterops)
        for (interopGists in commonizerRequestsForSharedCompilations.map { it.cinterops }.toSet()) {
            commonize(interopGists)
        }
    }

    private fun commonize(cinterops: Set<CInteropGist>) {
        outputDirectory(cinterops).deleteRecursively()
        val commonizer = GradleCliCommonizer(project)
        commonizer.commonizeLibraries(
            konanHome = project.file(project.konanHome),
            inputLibraries = cinterops.map { it.libraryFile.get() }.toSet(),
            dependencyLibraries = cinterops.flatMap { it.dependencies.files }.toSet(),
            outputCommonizerTarget = SharedCommonizerTarget(cinterops.map { CommonizerTarget(it.konanTarget) }.toSet()),
            outputDirectory = outputDirectory(cinterops)
        )
    }

    private fun outputDirectory(cinterops: Set<CInteropGist>): File {
        return project.rootDir.resolve(".gradle/kotlin/commonizer/cinterop")
            .resolve(project.path)
            .resolve(cinterops.joinToString("-") { it.name })
    }

    private fun commonizedOutputDirectory(cinterops: Set<CInteropGist>): File {
        return HierarchicalCommonizerOutputLayout.getTargetDirectory(
            outputDirectory(cinterops), SharedCommonizerTarget(cinterops.map { it.konanTarget })
        )
    }

    internal fun commonizedOutputDirectory(compilation: KotlinSharedNativeCompilation): FileCollection {
        val request = sharedCInteropsForCompilation(compilation, cinterops) ?: return project.files()
        return project.files(project.provider {
            commonizedOutputDirectory(request.cinterops).listFiles().orEmpty()
        }).builtBy(this)
    }
}

private data class SharedCInterops(
    val sharedNativeCompilation: KotlinSharedNativeCompilation, val cinterops: Set<CInteropGist>
)

private fun Project.sharedCInterops(
    cinterops: Set<CInteropGist>
): Set<SharedCInterops> {
    val multiplatformExtension = project.multiplatformExtensionOrNull ?: return emptySet()
    return multiplatformExtension.targets.flatMap { it.compilations }
        .filterIsInstance<KotlinSharedNativeCompilation>()
        .mapNotNull { sharedCompilation -> sharedCInteropsForCompilation(sharedCompilation, cinterops) }
        .toSet()
}

private fun sharedCInteropsForCompilation(
    compilation: KotlinSharedNativeCompilation,
    cinterops: Set<CInteropGist>
): SharedCInterops? {
    compilation.kotlinSourceSets.
}


private fun CInteropProcess.toGist(): CInteropGist {
    return CInteropGist(
        name = settings.name,
        konanTarget = konanTarget,
        sourceSets = project.provider { settings.compilation.kotlinSourceSets.toSet() },
        libraryFile = outputFileProvider,
        dependencies = settings.dependencyFiles // TODO NOW: var shall be replaced by provider
    )
}
