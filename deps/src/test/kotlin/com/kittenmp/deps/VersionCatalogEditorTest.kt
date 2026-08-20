package com.kittenmp.deps

import com.kittenmp.ai.ComprehensionDebt
import kotlin.test.Test
import kotlin.test.assertEquals

@ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
class VersionCatalogEditorTest {

  private val editor = VersionCatalogEditor()
  private val clikt = ArtifactMatch(group = "com.github.ajalt.clikt", name = "clikt", version = "5.1.0")

  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `adds a new entry to existing sections`() {
    val catalog = """
      [versions]
      kotlin = "2.4.10"

      [libraries]
      kotlin-test = { group = "org.jetbrains.kotlin", name = "kotlin-test", version.ref = "kotlin" }
    """.trimIndent() + "\n"

    val result = editor.upsert(catalog, "clikt", clikt)

    assertEquals(VersionCatalogEditor.Change.ADDED, result.change)
    assertEquals(
      """
      [versions]
      kotlin = "2.4.10"
      clikt = "5.1.0"

      [libraries]
      kotlin-test = { group = "org.jetbrains.kotlin", name = "kotlin-test", version.ref = "kotlin" }
      clikt = { group = "com.github.ajalt.clikt", name = "clikt", version.ref = "clikt" }
      """.trimIndent() + "\n",
      result.content,
    )
  }

  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `re-upserting the same version changes nothing`() {
    val catalog = editor.upsert(EMPTY_CATALOG, "clikt", clikt).content
    val result = editor.upsert(catalog, "clikt", clikt)

    assertEquals(VersionCatalogEditor.Change.UNCHANGED, result.change)
    assertEquals(catalog, result.content)
  }

  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `bumping a version updates in place`() {
    val catalog = editor.upsert(EMPTY_CATALOG, "clikt", clikt).content
    val result = editor.upsert(catalog, "clikt", clikt.copy(version = "5.2.0"))

    assertEquals(VersionCatalogEditor.Change.UPDATED, result.change)
    assertEquals(catalog.replace("5.1.0", "5.2.0"), result.content)
  }

  /** The old editor rebuilt the file from a name-keyed map, silently dropping the first `[libraries]`. */
  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `a repeated section header does not drop entries`() {
    val catalog = """
      [libraries]
      first = { group = "a", name = "a", version = "1" }

      [versions]
      kotlin = "2.4.10"

      [libraries]
      second = { group = "b", name = "b", version = "1" }
    """.trimIndent() + "\n"

    val result = editor.upsert(catalog, "clikt", clikt)

    assertEquals(1, Regex("^first = ", RegexOption.MULTILINE).findAll(result.content).count())
    assertEquals(1, Regex("^second = ", RegexOption.MULTILINE).findAll(result.content).count())
    assertEquals(2, Regex("^\\[libraries]$", RegexOption.MULTILINE).findAll(result.content).count())
  }

  /** Keys written without spaces around `=` used to be missed, producing a duplicate entry. */
  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `matches keys regardless of spacing around the equals sign`() {
    val catalog = """
      [versions]
      clikt="5.0.0"

      [libraries]
      clikt={ group = "com.github.ajalt.clikt", name = "clikt", version.ref = "clikt" }
    """.trimIndent() + "\n"

    val result = editor.upsert(catalog, "clikt", clikt)

    assertEquals(VersionCatalogEditor.Change.UPDATED, result.change)
    assertEquals(2, Regex("""^clikt\s*=""", RegexOption.MULTILINE).findAll(result.content).count())
    assertEquals(true, """clikt = "5.1.0"""" in result.content)
  }

  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `comments and unrelated lines are preserved verbatim`() {
    val catalog = """
      # keep me

      [versions]
      # clikt = "0.0.1"
      kotlin = "2.4.10"

      [libraries]
      kotlin-test = { group = "org.jetbrains.kotlin", name = "kotlin-test", version.ref = "kotlin" }

      [plugins]
      kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
    """.trimIndent() + "\n"

    val content = editor.upsert(catalog, "clikt", clikt).content

    catalog.lines().filter { it.isNotBlank() }.forEach {
      assertEquals(true, it in content.lines(), "lost line: $it")
    }
    assertEquals(true, content.indexOf("[plugins]") > content.indexOf("[libraries]"))
  }

  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `creates missing sections`() {
    val result = editor.upsert("", "clikt", clikt)

    assertEquals(VersionCatalogEditor.Change.ADDED, result.change)
    assertEquals(
      """
      [versions]
      clikt = "5.1.0"

      [libraries]
      clikt = { group = "com.github.ajalt.clikt", name = "clikt", version.ref = "clikt" }
      """.trimIndent() + "\n",
      result.content,
    )
  }

  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `a file without a trailing newline stays that way`() {
    val catalog = "[versions]\nkotlin = \"2.4.10\""
    assertEquals(false, editor.upsert(catalog, "clikt", clikt).content.endsWith("\n"))
  }

  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `windows line endings are preserved`() {
    val catalog = "[versions]\r\nkotlin = \"2.4.10\"\r\n"
    val content = editor.upsert(catalog, "clikt", clikt).content

    assertEquals(true, content.contains("clikt = \"5.1.0\"\r\n"))
    assertEquals(0, Regex("(?<!\r)\n").findAll(content).count())
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `removes matching version and library entries`() {
    val catalog = """
      [versions]
      kotlin = "2.4.10"
      clikt = "5.1.0"

      [libraries]
      kotlin-test = { group = "org.jetbrains.kotlin", name = "kotlin-test", version.ref = "kotlin" }
      clikt = { group = "com.github.ajalt.clikt", name = "clikt", version.ref = "clikt" }
    """.trimIndent() + "\n"

    val result = editor.remove(catalog, "clikt")

    assertEquals(VersionCatalogEditor.Change.REMOVED, result.change)
    assertEquals(
      """
      [versions]
      kotlin = "2.4.10"

      [libraries]
      kotlin-test = { group = "org.jetbrains.kotlin", name = "kotlin-test", version.ref = "kotlin" }
      """.trimIndent() + "\n",
      result.content,
    )
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `removing a missing alias changes nothing`() {
    val catalog = """
      [versions]
      kotlin = "2.4.10"

      [libraries]
      kotlin-test = { group = "org.jetbrains.kotlin", name = "kotlin-test", version.ref = "kotlin" }
    """.trimIndent() + "\n"

    val result = editor.remove(catalog, "clikt")

    assertEquals(VersionCatalogEditor.Change.UNCHANGED, result.change)
    assertEquals(catalog, result.content)
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `remove leaves plugins and comments alone`() {
    val catalog = """
      # keep me

      [versions]
      # clikt = "0.0.1"
      clikt = "5.1.0"

      [libraries]
      clikt = { group = "com.github.ajalt.clikt", name = "clikt", version.ref = "clikt" }

      [plugins]
      kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
    """.trimIndent() + "\n"

    val result = editor.remove(catalog, "clikt")

    assertEquals(VersionCatalogEditor.Change.REMOVED, result.change)
    assertEquals(true, "# keep me" in result.content)
    assertEquals(true, """# clikt = "0.0.1"""" in result.content)
    assertEquals(true, "[plugins]" in result.content)
    assertEquals(true, """kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }""" in result.content)
    assertEquals(false, Regex("""^clikt\s*=""", RegexOption.MULTILINE).containsMatchIn(result.content))
  }

  private companion object {
    const val EMPTY_CATALOG = "[versions]\n\n[libraries]\n"
  }
}
