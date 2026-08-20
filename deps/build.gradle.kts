plugins {
  alias(libs.plugins.kotlin.jvm)
}

dependencies {
  api(projects.ai)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.cio)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
  useJUnitPlatform()
}
