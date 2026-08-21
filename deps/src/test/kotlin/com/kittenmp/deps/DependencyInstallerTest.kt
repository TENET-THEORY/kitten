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

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `uninstall removes the catalog entries`() {
    val root = tempProject(catalog = "[versions]\n\n[libraries]\n")
    installerFor(root).use { it.install("clikt") }

    val summary = installerFor(root).use { it.uninstall("clikt") }

    assertEquals("removed clikt", summary)
    assertEquals("[versions]\n\n[libraries]\n", File(root, "gradle/libs.versions.toml").readText())
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `uninstall accepts a group coordinate`() {
    val root = tempProject(
      catalog = """
        [versions]
        clikt = "5.1.0"

        [libraries]
        clikt = { group = "com.github.ajalt.clikt", name = "clikt", version.ref = "clikt" }
      """.trimIndent() + "\n",
    )

    val summary = installerFor(root).use { it.uninstall("com.github.ajalt.clikt:clikt") }

    assertEquals("removed clikt", summary)
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `uninstall of a missing library is an error`() {
    val root = tempProject(catalog = "[versions]\n\n[libraries]\n")
    val failure = assertFailsWith<IllegalStateException> {
      installerFor(root).use { it.uninstall("clikt") }
    }
    assertTrue("clikt" in failure.message.orEmpty())
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `addToModule wires an installed library into the module build script`() {
    val root = tempProject(
      catalog = """
        [versions]
        clikt = "5.1.0"

        [libraries]
        clikt = { group = "com.github.ajalt.clikt", name = "clikt", version.ref = "clikt" }
      """.trimIndent() + "\n",
      moduleBuild = """
        dependencies {
        }
      """.trimIndent() + "\n",
    )

    val summary = installerFor(root).use { it.addToModule("app", "clikt") }

    assertEquals("added implementation(libs.clikt) to app/build.gradle.kts", summary)
    assertEquals(
      """
      dependencies {
        implementation(libs.clikt)
      }
      """.trimIndent() + "\n",
      File(root, "app/build.gradle.kts").readText(),
    )
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `addToModule fails when the library is not in the catalog`() {
    val root = tempProject(
      catalog = "[versions]\n\n[libraries]\n",
      moduleBuild = "dependencies {\n}\n",
    )
    val failure = assertFailsWith<IllegalStateException> {
      installerFor(root).use { it.addToModule("app", "clikt") }
    }
    assertTrue("clikt" in failure.message.orEmpty())
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `addToModule wires a ktorLibs dependency into the module build script`() {
    val root = tempProject(
      catalog = "[versions]\n\n[libraries]\n",
      moduleBuild = """
        dependencies {
        }
      """.trimIndent() + "\n",
      settings = """
        versionCatalogs {
          create("ktorLibs") { from("io.ktor:ktor-version-catalog:3.5.2") }
        }
      """.trimIndent() + "\n",
    )

    val summary = installerFor(root, ktorToml = KTOR_TOML).use { it.addToModule("app", "server-auth") }

    assertEquals("added implementation(ktorLibs.server.auth) to app/build.gradle.kts", summary)
    assertEquals(
      """
      dependencies {
        implementation(ktorLibs.server.auth)
      }
      """.trimIndent() + "\n",
      File(root, "app/build.gradle.kts").readText(),
    )
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `addToModule resolves ktor artifact ids to ktorLibs aliases`() {
    val root = tempProject(
      catalog = "[versions]\n\n[libraries]\n",
      moduleBuild = "dependencies {\n}\n",
      settings = """
        versionCatalogs {
          create("ktorLibs") { from("io.ktor:ktor-version-catalog:3.5.2") }
        }
      """.trimIndent() + "\n",
    )

    val summary = installerFor(root, ktorToml = KTOR_TOML).use {
      it.addToModule("app", "ktor-server-html-builder")
    }

    assertEquals("added implementation(ktorLibs.server.htmlBuilder) to app/build.gradle.kts", summary)
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `install with module updates catalog and build script`() {
    val root = tempProject(
      catalog = "[versions]\n\n[libraries]\n",
      moduleBuild = "dependencies {\n}\n",
    )

    val summary = installerFor(root).use { it.install("clikt", module = "app") }

    assertTrue(summary.startsWith("added clikt 5.1.0"), summary)
    assertTrue("added implementation(libs.clikt) to app/build.gradle.kts" in summary, summary)
    assertTrue("clikt = \"5.1.0\"" in File(root, "gradle/libs.versions.toml").readText())
    assertTrue("implementation(libs.clikt)" in File(root, "app/build.gradle.kts").readText())
  }

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun tempProject(
    catalog: String,
    moduleBuild: String? = null,
    settings: String? = null,
  ): File {
    val root = Files.createTempDirectory("kitten-project").toFile().also { it.deleteOnExit() }
    File(root, "gradle").mkdirs()
    File(root, "gradle/libs.versions.toml").writeText(catalog)
    if (moduleBuild != null) {
      File(root, "app").mkdirs()
      File(root, "app/build.gradle.kts").writeText(moduleBuild)
    }
    if (settings != null) {
      File(root, "settings.gradle.kts").writeText(settings)
    }
    return root
  }

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun installerFor(
    startDir: File,
    docs: String = CLIKT_DOC,
    ktorToml: String? = null,
  ): DependencyInstaller {
    val engine = MockEngine {
      respond(
        """{"response":{"docs":[$docs]}}""",
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }
    val emptyCache = Files.createTempDirectory("kitten-empty-cache").toFile().also { it.deleteOnExit() }
    return DependencyInstaller(
      startDir,
      MavenCentralClient(client = HttpClient(engine)),
      KtorLibsCatalog(
        catalogTomlLoader = CatalogTomlLoader { _, _, _ ->
          ktorToml ?: error("ktor catalog download not configured")
        },
        gradleCacheRoot = emptyCache,
      ),
    )
  }

  private companion object {
    const val CLIKT_DOC =
      """{"g":"com.github.ajalt.clikt","a":"clikt","latestVersion":"5.1.0","timestamp":1}"""
    val KTOR_TOML = """
      [libraries]
      server-auth = {group = "io.ktor", name = "ktor-server-auth", version.ref = "ktor" }
      server-htmlBuilder = {group = "io.ktor", name = "ktor-server-html-builder", version.ref = "ktor" }
    """.trimIndent()
  }
}
