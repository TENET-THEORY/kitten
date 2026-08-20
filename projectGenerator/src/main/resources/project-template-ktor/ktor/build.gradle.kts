plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(ktorLibs.plugins.ktor)
  alias(libs.plugins.ktfmt.gradle)
}

dependencies {
  implementation(ktorLibs.server.core)
  implementation(ktorLibs.server.netty)
  implementation(ktorLibs.server.config.yaml)
  implementation(ktorLibs.server.htmlBuilder)
  implementation(libs.logback.classic)
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

application { mainClass.set("io.ktor.server.netty.EngineMain") }
