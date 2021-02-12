/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer

import org.junit.Test
import kotlin.test.assertEquals

class CommonizerTargetTest {

    @Test
    fun leafTargetNames() {
        listOf(
            Triple("foo", "foo", FOO),
            Triple("bar", "bar", BAR),
            Triple("baz_123", "baz_123", BAZ),
        ).forEach { (name, prettyName, target: LeafCommonizerTarget) ->
            assertEquals(name, target.name)
            assertEquals(prettyName, target.identityString)
        }
    }

    @Test
    fun sharedTargetNames() {
        listOf(
            "[foo]" to SharedTarget(FOO),
            "[bar, foo]" to SharedTarget(FOO, BAR),
            "[bar, baz_123, foo]" to SharedTarget(FOO, BAR, BAZ),
            "[[bar, foo], bar, baz_123, foo]" to SharedTarget(FOO, BAR, BAZ, SharedTarget(FOO, BAR))
        ).forEach { (prettyName, target: SharedCommonizerTarget) ->
            assertEquals(prettyName, target.identityString)
        }
    }

    @Test
    fun prettyCommonizedName() {
        val sharedTarget = SharedTarget(FOO, BAR, BAZ)
        listOf(
            "[foo(*), bar, baz_123]" to FOO,
            "[foo, bar(*), baz_123]" to BAR,
            "[foo, bar, baz_123(*)]" to BAZ,
            "[foo, bar, baz_123]" to sharedTarget
        ).forEach { (prettyCommonizerName, target: CommonizerTarget) ->
            assertEquals(prettyCommonizerName, sharedTarget.contextualIdentityString(target))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun sharedTargetNoInnerTargets() {
        SharedCommonizerTarget(emptySet<CommonizerTarget>())
    }

    private companion object {
        val FOO = LeafCommonizerTarget("foo")
        val BAR = LeafCommonizerTarget("bar")
        val BAZ = LeafCommonizerTarget("baz_123")

        @Suppress("TestFunctionName")
        fun SharedTarget(vararg targets: CommonizerTarget) = SharedCommonizerTarget(linkedSetOf(*targets))
    }
}
