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
import org.jetbrains.kotlin.descriptors.commonizer.*
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtensionOrNull
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinSharedNativeCompilation
import org.jetbrains.kotlin.gradle.targets.native.internal.CInteropCommonizerTask.CInteropGist
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

open class CInteropCommonizerTask : DefaultTask() {

    internal data class CInteropGist(
        @get:Input val identifier: CInteropIdentifier,
        @get:Input val name: String,
        @get:Input val konanTarget: KonanTarget,
        @get:Internal val sourceSets: Provider<Set<KotlinSourceSet>>,
        @get:Internal val allSourceSets: Provider<Set<KotlinSourceSet>>,
        @get:Classpath val libraryFile: Provider<File>,
        @get:Classpath val dependencies: FileCollection
    ) {
        @Suppress("unused") // Used for UP-TO-DATE check
        @get:Input
        val sourceSetNames: Provider<List<String>> = sourceSets.map { sourceSets -> sourceSets.map { sourceSet -> sourceSet.name } }

        @Suppress("unused") // Used for UP-TO-DATE check
        @get:Input
        val allSourceSetNames: Provider<List<String>> = allSourceSets.map { sourceSets -> sourceSets.map { sourceSet -> sourceSet.name } }
    }

    @get:Nested
    internal var cinterops = setOf<CInteropGist>()
        private set

    @Internal
    internal fun getAllCInteropCommonizerTargetsInProject(): Set<CInteropCommonizerTarget> {
        val multiplatformExtension = project.multiplatformExtensionOrNull ?: return emptySet()
        return multiplatformExtension.targets.flatMap { it.compilations }
            .filterIsInstance<KotlinSharedNativeCompilation>()
            .mapNotNull { sharedNativeCompilation -> project.getCInteropCommonizerTarget(sharedNativeCompilation) }
            .mapNotNull(::filterUnregisteredCInterops)
            .toSet()
    }

    private fun filterUnregisteredCInterops(target: CInteropCommonizerTarget): CInteropCommonizerTarget? {
        val filteredCInterops = target.cinterops.filter { it in cinterops.map(CInteropGist::identifier) }
        if (filteredCInterops.isEmpty()) return null
        return target.copy(cinterops = filteredCInterops.toSet())
    }

    private fun findRootCommonizerTargetInProject(target: CInteropCommonizerTarget): CInteropCommonizerTarget {
        return getAllCInteropCommonizerTargetsInProject()
            .filter { candidate -> candidate != target }
            .filter { candidate -> target in candidate }
            .maxBy { candidate -> candidate.commonizerTarget.level } ?: target
    }

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
        val targets = getAllCInteropCommonizerTargetsInProject().map(::findRootCommonizerTargetInProject).distinct()
        for (target in targets) {
            commonize(target)
        }
    }

    private fun commonize(target: CInteropCommonizerTarget) {
        val cinteropsForTarget = cinterops.filter { cinterop -> cinterop.identifier in target.cinterops }
        outputDirectory(target).deleteRecursively()
        if (cinteropsForTarget.isEmpty()) return
        GradleCliCommonizer(project).commonizeLibraries(
            konanHome = project.file(project.konanHome),
            outputCommonizerTarget = target.commonizerTarget,
            inputLibraries = cinteropsForTarget.map { it.libraryFile.get() }.toSet(),
            dependencyLibraries = emptySet(),/*cinteropsForTarget.flatMap { it.dependencies.files }.toSet() */ // TODO NOW,
            outputDirectory = outputDirectory(target)
        )
    }

    private fun outputDirectory(target: CInteropCommonizerTarget): File {
        return project.rootDir.resolve(".gradle/kotlin/commonizer/cinterop")
            .resolve(project.path)
            .resolve(target.commonizerTarget.prettyName)
            .resolve(target.cinterops.map { it.cinteropName }.distinct().joinToString("-"))
    }

    internal fun getLibraries(compilation: KotlinSharedNativeCompilation): FileCollection {
        val fileProvider = project.provider<Set<File>> {
            val cinteropCommonizerTarget = project.getCInteropCommonizerTarget(compilation) ?: return@provider emptySet()
            val rootCommonizerTarget = findRootCommonizerTargetInProject(cinteropCommonizerTarget)
            val outputDirectory = outputDirectory(rootCommonizerTarget)
            HierarchicalCommonizerOutputLayout
                .getTargetDirectory(outputDirectory, cinteropCommonizerTarget.commonizerTarget)
                .listFiles().orEmpty().toSet()
        }

        return project.files(fileProvider) { fileCollection ->
            fileCollection.builtBy(this)
        }
    }
}

private fun CInteropProcess.toGist(): CInteropGist {
    return CInteropGist(
        identifier = settings.identifier,
        name = settings.name,
        konanTarget = konanTarget,
        sourceSets = project.provider { settings.compilation.kotlinSourceSets.toSet() },
        allSourceSets = project.provider { settings.compilation.allKotlinSourceSets.toSet() },
        libraryFile = outputFileProvider,
        dependencies = settings.dependencyFiles // TODO NOW: var shall be replaced by provider
    )
}
