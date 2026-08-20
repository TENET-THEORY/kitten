plugins {
  alias(libs.plugins.kotlin.jvm)
}

dependencies {
  implementation(projects.ai)
  testImplementation(libs.kotlin.test)
}

tasks.test {
  useJUnitPlatform()
}
