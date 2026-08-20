package com.kittenmp.projectGenerator

import com.kittenmp.ai.ComprehensionDebt
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.isExecutable
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
class ProjectGeneratorTest {

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `scaffolds a plain kotlin project`() {
    val parent = createTempDirectory("kitten-plain-").toFile()
    try {
      ProjectGenerator(
        projectName = "demo-app",
        basePackage = "com.example.app",
        parentDir = parent,
        projectType = ProjectType.PLAIN,
      ).generateNewProject()

      val root = parent.resolve("demo-app").toPath()
      assertTrue(root.resolve("settings.gradle.kts").exists())
      assertTrue(root.resolve("gradlew").isExecutable())
      assertTrue(root.resolve("app/src/main/kotlin/com/example/app/App.kt").exists())
      assertTrue(root.resolve("settings.gradle.kts").readText().contains("include(\"app\")"))
      assertFalse(root.resolve("ktor").exists())
    } finally {
      parent.deleteRecursively()
    }
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `scaffolds a ktor project without database htmx or resources plugin`() {
    val parent = createTempDirectory("kitten-ktor-").toFile()
    try {
      ProjectGenerator(
        projectName = "demo-api",
        basePackage = "com.example.app",
        parentDir = parent,
        projectType = ProjectType.KTOR,
      ).generateNewProject()

      val root = parent.resolve("demo-api").toPath()
      assertTrue(root.resolve("settings.gradle.kts").exists())
      assertTrue(root.resolve("gradlew").isExecutable())
      assertTrue(root.resolve("ktor/src/main/kotlin/com/example/app/Routing.kt").exists())
      assertTrue(root.resolve("ktor/src/main/kotlin/com/example/app/view/LayoutTemplate.kt").exists())

      val settings = root.resolve("settings.gradle.kts").readText()
      assertTrue(settings.contains("rootProject.name = \"demo-api\""))
      assertTrue(settings.contains("include(\"ktor\")"))

      val applicationYaml = root.resolve("ktor/src/main/resources/application.yaml").readText()
      assertTrue(applicationYaml.contains("com.example.app.RoutingKt.routing"))
      assertFalse(applicationYaml.contains("DatabaseModule"))
      assertFalse(applicationYaml.contains("ResourcesKt"))

      val ktorBuild = root.resolve("ktor/build.gradle.kts").readText()
      assertFalse(ktorBuild.contains("htmx"))
      assertFalse(ktorBuild.contains("server.resources"))
      assertFalse(ktorBuild.contains("exposed"))
      assertFalse(ktorBuild.contains("postgresql"))

      assertFalse(root.resolve("ktor/src/main/kotlin/com/example/app/db").exists())
      assertFalse(root.resolve("ktor/src/main/kotlin/com/example/app/plugins").exists())
      assertFalse(root.resolve("ktor/src/main/resources/static/js/htmx.min.js").exists())
      assertTrue(root.resolve(".gitignore").exists())
      assertTrue(root.resolve(".gitattributes").exists())
    } finally {
      parent.deleteRecursively()
    }
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `fails when project directory already exists`() {
    val parent = createTempDirectory("kitten-exists-").toFile()
    try {
      Files.createDirectory(parent.toPath().resolve("demo-app"))
      assertFailsWith<IllegalStateException> {
        ProjectGenerator(
          projectName = "demo-app",
          basePackage = "com.example",
          parentDir = parent,
          projectType = ProjectType.PLAIN,
        ).generateNewProject()
      }
    } finally {
      parent.deleteRecursively()
    }
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `parses project type from cli values`() {
    assertEquals(ProjectType.PLAIN, ProjectType.fromCli("plain"))
    assertEquals(ProjectType.PLAIN, ProjectType.fromCli("kotlin"))
    assertEquals(ProjectType.KTOR, ProjectType.fromCli("ktor"))
  }
}
