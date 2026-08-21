package com.kittenmp.deps

import com.kittenmp.ai.ComprehensionDebt
import kotlin.test.Test
import kotlin.test.assertEquals

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
class BuildGradleEditorTest {

  private val editor = BuildGradleEditor()

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `inserts into an empty dependencies block`() {
    val script = """
      plugins {
        alias(libs.plugins.kotlin.jvm)
      }

      dependencies {
      }
    """.trimIndent() + "\n"

    val result = editor.addImplementation(script, "clikt")

    assertEquals(BuildGradleEditor.Change.ADDED, result.change)
    assertEquals(
      """
      plugins {
        alias(libs.plugins.kotlin.jvm)
      }

      dependencies {
        implementation(libs.clikt)
      }
      """.trimIndent() + "\n",
      result.content,
    )
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `appends after existing dependency lines`() {
    val script = """
      dependencies {
        implementation(projects.ai)
      }
    """.trimIndent() + "\n"

    val result = editor.addImplementation(script, "clikt")

    assertEquals(BuildGradleEditor.Change.ADDED, result.change)
    assertEquals(
      """
      dependencies {
        implementation(projects.ai)
        implementation(libs.clikt)
      }
      """.trimIndent() + "\n",
      result.content,
    )
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `is idempotent when the dependency already exists`() {
    val script = """
      dependencies {
        implementation(libs.clikt)
      }
    """.trimIndent() + "\n"

    val result = editor.addImplementation(script, "clikt")

    assertEquals(BuildGradleEditor.Change.UNCHANGED, result.change)
    assertEquals(script, result.content)
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `creates a dependencies block when missing`() {
    val script = """
      plugins {
        alias(libs.plugins.kotlin.jvm)
      }
    """.trimIndent() + "\n"

    val result = editor.addImplementation(script, "clikt")

    assertEquals(BuildGradleEditor.Change.ADDED, result.change)
    assertEquals(
      """
      plugins {
        alias(libs.plugins.kotlin.jvm)
      }

      dependencies {
        implementation(libs.clikt)
      }
      """.trimIndent() + "\n",
      result.content,
    )
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `hyphenated aliases become dotted accessors`() {
    val script = "dependencies {\n}\n"

    val result = editor.addImplementation(script, "ktor-client-core")

    assertEquals(true, "implementation(libs.ktor.client.core)" in result.content)
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `uses a custom catalog extension`() {
    val script = "dependencies {\n}\n"

    val result = editor.addImplementation(script, "server-core", catalogExtension = "ktorLibs")

    assertEquals(true, "implementation(ktorLibs.server.core)" in result.content)
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `custom catalog duplicates are detected`() {
    val script = """
      dependencies {
        implementation(ktorLibs.server.core)
      }
    """.trimIndent() + "\n"

    val result = editor.addImplementation(script, "server-core", catalogExtension = "ktorLibs")

    assertEquals(BuildGradleEditor.Change.UNCHANGED, result.change)
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `expands an inline empty dependencies block`() {
    val script = "plugins {}\n\ndependencies { }\n"

    val result = editor.addImplementation(script, "clikt")

    assertEquals(
      "plugins {}\n\ndependencies {\n  implementation(libs.clikt)\n}\n",
      result.content,
    )
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `tolerates whitespace differences when detecting duplicates`() {
    val script = """
      dependencies {
        implementation( libs.clikt )
      }
    """.trimIndent() + "\n"

    val result = editor.addImplementation(script, "clikt")

    assertEquals(BuildGradleEditor.Change.UNCHANGED, result.change)
  }
}
