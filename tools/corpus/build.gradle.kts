import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

application {
    mainClass.set("com.smsledger.corpus.MainKt")
    applicationDefaultJvmArgs = listOf("-Xmx512m")
}

dependencies {
    implementation(project(":core"))
    testImplementation(libs.junit)
}
