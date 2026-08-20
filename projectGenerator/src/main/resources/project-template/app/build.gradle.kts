plugins {
  alias(libs.plugins.kotlin.jvm)
  application
}

dependencies {
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

application {
  mainClass = "__PACKAGE_PATH__.AppKt"
}
