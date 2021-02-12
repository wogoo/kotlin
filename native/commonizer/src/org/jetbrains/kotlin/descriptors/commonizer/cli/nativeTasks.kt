/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer.cli

import org.jetbrains.kotlin.descriptors.commonizer.*
import org.jetbrains.kotlin.descriptors.commonizer.konan.LibraryCommonizer
import org.jetbrains.kotlin.descriptors.commonizer.konan.LibraryResultsConsumer
import org.jetbrains.kotlin.descriptors.commonizer.konan.NativeDistributionCommonizer
import org.jetbrains.kotlin.descriptors.commonizer.repository.FilesRepository
import org.jetbrains.kotlin.descriptors.commonizer.repository.KonanDistributionRepository
import org.jetbrains.kotlin.descriptors.commonizer.repository.plus
import org.jetbrains.kotlin.descriptors.commonizer.stats.FileStatsOutput
import org.jetbrains.kotlin.descriptors.commonizer.stats.StatsCollector
import org.jetbrains.kotlin.descriptors.commonizer.stats.StatsType
import org.jetbrains.kotlin.konan.library.KONAN_DISTRIBUTION_KLIB_DIR
import org.jetbrains.kotlin.konan.library.KONAN_DISTRIBUTION_PLATFORM_LIBS_DIR
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

internal class NativeDistributionListTargets(options: Collection<Option<*>>) : Task(options) {
    override val category get() = Category.INFORMATIONAL

    override fun execute(logPrefix: String) {
        val distributionPath = getMandatory<File, NativeDistributionOptionType>()

        val targets = distributionPath.resolve(KONAN_DISTRIBUTION_KLIB_DIR)
            .resolve(KONAN_DISTRIBUTION_PLATFORM_LIBS_DIR)
            .list()
            ?.sorted()
            ?: emptyList()

        println()
        if (targets.isEmpty())
            println("No hardware targets found inside of the Kotlin/Native distribution \"$distributionPath\".")
        else {
            println("${targets.size} hardware targets found inside of the Kotlin/Native distribution \"$distributionPath\":")
            targets.forEach(::println)
        }
        println()
    }
}

internal class NativeKlibCommonize(options: Collection<Option<*>>) : Task(options) {
    override val category: Category = Category.COMMONIZATION

    override fun execute(logPrefix: String) {
        val distribution = KonanDistribution(getMandatory<File, NativeDistributionOptionType>())
        val destination = getMandatory<File, OutputOptionType>()
        val targetLibraries = getMandatory<List<File>, TargetLibrariesOptionType>()
        val dependencyLibraries = getMandatory<List<File>, DependencyLibrariesOptionType>()
        val outputHierarchy = getMandatory<SharedCommonizerTarget, OutputHierarchyOptionType>()

        val statsType = getOptional<StatsType, StatsTypeOptionType> { it == "log-stats" } ?: StatsType.NONE

        val logger = CliLoggerAdapter(2)
        val libraryLoader = DefaultNativeLibraryLoader(logger)
        val resultsConsumer = LibraryResultsConsumer(destination, HierarchicalCommonizerOutputLayout, logger.toProgressLogger())

        val statsCollector = StatsCollector(statsType, outputHierarchy.konanTargets.toList())

        LibraryCommonizer(
            konanDistribution = distribution,
            repository = FilesRepository(targetLibraries.toSet(), libraryLoader),
            dependencies = KonanDistributionRepository(distribution, outputHierarchy.konanTargets, libraryLoader) +
                    FilesRepository(dependencyLibraries.toSet(), libraryLoader),
            libraryLoader = libraryLoader,
            targets = outputHierarchy.konanTargets.toList(),
            resultsConsumer = resultsConsumer,
            statsCollector = statsCollector,
            logger = CliLoggerAdapter(2)
        ).run()

        statsCollector?.writeTo(FileStatsOutput(destination, statsType.name.toLowerCase()))
    }
}

internal class NativeDistributionCommonize(options: Collection<Option<*>>) : Task(options) {
    override val category get() = Category.COMMONIZATION

    override fun execute(logPrefix: String) {
        val distribution = getMandatory<File, NativeDistributionOptionType>()
        val destination = getMandatory<File, OutputOptionType>()
        val targets = getMandatory<List<KonanTarget>, NativeTargetsOptionType>()

        val copyStdlib = getOptional<Boolean, BooleanOptionType> { it == "copy-stdlib" } ?: false
        val copyEndorsedLibs = getOptional<Boolean, BooleanOptionType> { it == "copy-endorsed-libs" } ?: false
        val statsType = getOptional<StatsType, StatsTypeOptionType> { it == "log-stats" } ?: StatsType.NONE

        val targetNames = targets.joinToString { "[${it.name}]" }
        val descriptionSuffix = estimateLibrariesCount(distribution, targets)?.let { " ($it items)" } ?: ""
        val description = "${logPrefix}Preparing commonized Kotlin/Native libraries for targets $targetNames$descriptionSuffix"

        println(description)

        NativeDistributionCommonizer(
            repository = distribution,
            targets = targets,
            destination = destination,
            copyStdlib = copyStdlib,
            copyEndorsedLibs = copyEndorsedLibs,
            statsType = statsType,
            logger = CliLoggerAdapter(2)
        ).run()

        println("$description: Done")
    }

    companion object {
        private fun estimateLibrariesCount(distribution: File, targets: List<KonanTarget>): Int? {
            val targetNames = targets.map { it.name }
            return distribution.resolve(KONAN_DISTRIBUTION_KLIB_DIR)
                .resolve(KONAN_DISTRIBUTION_PLATFORM_LIBS_DIR)
                .listFiles()
                ?.filter { it.name in targetNames }
                ?.mapNotNull { it.listFiles() }
                ?.flatMap { it.toList() }
                ?.size
        }
    }

}
