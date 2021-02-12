/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer

import java.io.File
import java.net.URLClassLoader

private const val commonizerMainClass = "org.jetbrains.kotlin.descriptors.commonizer.cli.CommonizerCLI"
private const val commonizerMainFunction = "main"

public fun CliCommonizer(classpath: Iterable<File>): CliCommonizer {
    return CliCommonizer(URLClassLoader(classpath.map { it.absoluteFile.toURI().toURL() }.toTypedArray()))
}

public class CliCommonizer(private val commonizerClassLoader: ClassLoader) : Commonizer {
    override fun commonizeLibraries(
        konanHome: File,
        targetLibraries: Set<File>,
        dependencyLibraries: Set<File>,
        outputHierarchy: SharedCommonizerTarget,
        outputDirectory: File
    ) {
        val arguments = mutableListOf<String>().apply {
            add("native-klib-commonize")
            add("-distribution-path"); add(konanHome.absolutePath)
            add("-input-libraries"); add(targetLibraries.joinToString(";") { it.absolutePath })
            add("-dependency-libraries"); add(dependencyLibraries.joinToString(";") { it.absolutePath })
            add("-output-commonizer-target"); add(outputHierarchy.identityString)
            add("-output-path"); add(outputDirectory.absolutePath)
        }

        val commonizerMainClass = commonizerClassLoader.loadClass(commonizerMainClass)
        val commonizerMainMethod = commonizerMainClass.methods.single { it.name == commonizerMainFunction }
        commonizerMainMethod.invoke(null, arguments.toTypedArray())
    }
}
