plugins {
    kotlin("jvm")
    id("jps-compatible")
}

kotlin {
    explicitApi()
}

description = "Kotlin KLIB Library Commonizer API"
publish()

dependencies {
    implementation(kotlinStdlib())
    implementation(project(":native:kotlin-native-utils"))
    testImplementation(project(":kotlin-test::kotlin-test-junit"))
    testImplementation(commonDep("junit:junit"))
    testImplementation(projectTests(":compiler:tests-common"))
    testRuntimeOnly(project(":native:kotlin-klib-commonizer"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { projectDefault() }
}

tasks.register("downloadNativeCompiler", DownloadNativeCompiler::class.java)

projectTest(parallel = false) {
    dependsOn(":dist")
    dependsOn("downloadNativeCompiler")
    workingDir = projectDir
    setKotlinCompilerEnvironmentVariable()
}

runtimeJar()
sourcesJar()
javadocJar()
