plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    application
}

version = "0.1.0"

dependencies {
  implementation(libs.clikt)
  implementation(libs.mosaic.runtime)
  implementation(projects.ai)
  implementation(projects.deps)
  implementation(projects.projectGenerator)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    applicationName = "kitten"
    mainClass = "com.kittenmp.AppKt"
}
