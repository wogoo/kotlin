import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType.IR

description = "Atomicfu Runtime"

plugins {
    kotlin("js")
}

group = "kotlinx.atomicfu"

kotlin.sourceSets {
    getByName("main") {
        dependencies {
            implementation(kotlin("stdlib-js"))
        }
    }
}

kotlin.target {
    nodejs()
}