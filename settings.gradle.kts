@file:Suppress("UnstableApiUsage") // Added for dependencyResolutionManagement.repositories

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")


plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
  }
}

rootProject.name = "kitten"
include("app")
include("projectGenerator")
