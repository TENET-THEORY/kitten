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

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun `inserts a plugin alias into an empty plugins block`() {
    val script = """
      plugins {
      }

      dependencies {
      }
    """.trimIndent() + "\n"

    val result = editor.addPlugin(script, "kotlin-jvm")

    assertEquals(BuildGradleEditor.Change.ADDED, result.change)
    assertEquals(
      """
      plugins {
        alias(libs.plugins.kotlin.jvm)
      }

      dependencies {
      }
      """.trimIndent() + "\n",
      result.content,
    )
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun `plugin aliases are idempotent including apply false`() {
    val script = """
      plugins {
        alias(libs.plugins.kotlin.jvm) apply false
      }
    """.trimIndent() + "\n"

    val result = editor.addPlugin(script, "kotlin-jvm", applyFalse = true)

    assertEquals(BuildGradleEditor.Change.UNCHANGED, result.change)
    assertEquals(script, result.content)
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun `does not convert an applied plugin into apply false`() {
    val script = """
      plugins {
        alias(libs.plugins.kotlin.jvm)
      }
    """.trimIndent() + "\n"

    val result = editor.addPlugin(script, "kotlin-jvm", applyFalse = true)

    assertEquals(BuildGradleEditor.Change.UNCHANGED, result.change)
    assertEquals(script, result.content)
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun `creates a plugins block at the top when missing`() {
    val script = """
      dependencies {
        implementation(libs.clikt)
      }
    """.trimIndent() + "\n"

    val result = editor.addPlugin(script, "kotlin-jvm")

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
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun `writes apply false on the plugin declaration`() {
    val script = "plugins {\n}\n"

    val result = editor.addPlugin(script, "kotlin-jvm", applyFalse = true)

    assertEquals(true, "alias(libs.plugins.kotlin.jvm) apply false" in result.content)
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun `hasPlugin matches applied and apply false declarations`() {
    assertEquals(true, editor.hasPlugin("alias(libs.plugins.kotlin.jvm)\n", "kotlin-jvm"))
    assertEquals(
      true,
      editor.hasPlugin("alias(libs.plugins.kotlin.jvm) apply false\n", "kotlin-jvm"),
    )
    assertEquals(false, editor.hasPlugin("alias(libs.plugins.ktfmt.gradle)\n", "kotlin-jvm"))
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun `uses a custom catalog for plugins`() {
    val script = "plugins {\n}\n"

    val result = editor.addPlugin(script, "ktor", catalogExtension = "ktorLibs")

    assertEquals(true, "alias(ktorLibs.plugins.ktor)" in result.content)
  }
}
