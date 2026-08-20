plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
  implementation(libs.clikt)
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
