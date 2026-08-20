package com.kittenmp.deps

import com.kittenmp.ai.ComprehensionDebt
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
class DependencyInstallerTest {

  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `writes the catalog found further up the tree`() {
    val root = tempProject(catalog = "[versions]\n\n[libraries]\n")
    val nested = File(root, "app/src/main").apply { mkdirs() }

    val summary = installerFor(nested).use { it.install("clikt") }

    assertEquals("added clikt 5.1.0 (com.github.ajalt.clikt:clikt)", summary)
    assertEquals(
      "[versions]\nclikt = \"5.1.0\"\n\n[libraries]\n" +
        "clikt = { group = \"com.github.ajalt.clikt\", name = \"clikt\", version.ref = \"clikt\" }\n",
      File(root, "gradle/libs.versions.toml").readText(),
    )
  }

  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `reports an unchanged catalog without rewriting it`() {
    val root = tempProject(catalog = "[versions]\n\n[libraries]\n")
    installerFor(root).use { it.install("clikt") }

    val catalogFile = File(root, "gradle/libs.versions.toml")
    val before = catalogFile.readText()
    val lastModified = catalogFile.lastModified()

    assertTrue(installerFor(root).use { it.install("clikt") }.startsWith("unchanged"))
    assertEquals(before, catalogFile.readText())
    assertEquals(lastModified, catalogFile.lastModified())
  }

  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `mentions other artifacts sharing the name`() {
    val root = tempProject(catalog = "[versions]\n\n[libraries]\n")
    val docs = """
      {"g":"com.github.ajalt.clikt","a":"clikt","latestVersion":"5.1.0","timestamp":2},
      {"g":"com.github.ajalt","a":"clikt","latestVersion":"2.8.0","timestamp":1}
    """.trimIndent()

    val summary = installerFor(root, docs).use { it.install("clikt") }

    assertTrue("com.github.ajalt:clikt" in summary, summary)
  }

  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `dots and underscores become alias separators`() {
    val root = tempProject(catalog = "[versions]\n\n[libraries]\n")
    val docs = """{"g":"org.example","a":"my_fancy.lib","latestVersion":"1.0.0","timestamp":1}"""

    val summary = installerFor(root, docs).use { it.install("my_fancy.lib") }

    assertTrue(summary.startsWith("added my-fancy-lib 1.0.0"), summary)
  }

  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `a missing catalog is an error`() {
    val root = Files.createTempDirectory("kitten-no-catalog").toFile().also { it.deleteOnExit() }
    val failure = assertFailsWith<IllegalStateException> { installerFor(root).use { it.install("clikt") } }
    assertTrue("libs.versions.toml" in failure.message.orEmpty())
  }

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun tempProject(catalog: String): File {
    val root = Files.createTempDirectory("kitten-project").toFile().also { it.deleteOnExit() }
    File(root, "gradle").mkdirs()
    File(root, "gradle/libs.versions.toml").writeText(catalog)
    return root
  }

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun installerFor(startDir: File, docs: String = CLIKT_DOC): DependencyInstaller {
    val engine = MockEngine {
      respond(
        """{"response":{"docs":[$docs]}}""",
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }
    return DependencyInstaller(startDir, MavenCentralClient(client = HttpClient(engine)))
  }

  private companion object {
    const val CLIKT_DOC =
      """{"g":"com.github.ajalt.clikt","a":"clikt","latestVersion":"5.1.0","timestamp":1}"""
  }
}
