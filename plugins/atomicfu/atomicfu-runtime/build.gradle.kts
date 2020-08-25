description = "Atomicfu Runtime"

plugins {
    kotlin("js")
    `maven-publish`
}

group = "kotlinx.atomicfu"

repositories {
    mavenLocal()
    jcenter()
}

kotlin {
    js()

    sourceSets {
        js().compilations["main"].defaultSourceSet {
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
        }
    }
}