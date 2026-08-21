package com.kittenmp.deps

import com.kittenmp.ai.ComprehensionDebt
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
class KtorLibsCatalogTest {

  private val catalog = KtorLibsCatalog(
    catalogTomlLoader = CatalogTomlLoader { _, _, _ -> error("should not download") },
    gradleCacheRoot = Files.createTempDirectory("kitten-empty-gradle-cache").toFile()
      .also { it.deleteOnExit() },
  )

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `parses the ktorLibs coordinate from settings`() {
    val settings = """
      versionCatalogs {
        create("ktorLibs") { from("io.ktor:ktor-version-catalog:3.5.2") }
      }
    """.trimIndent()

    assertEquals(
      KtorLibsCatalog.Coordinate("io.ktor", "ktor-version-catalog", "3.5.2"),
      catalog.parseCoordinate(settings),
    )
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `findCoordinate reads settings next to the project root`() {
    val root = Files.createTempDirectory("kitten-ktor-settings").toFile().also { it.deleteOnExit() }
    File(root, "settings.gradle.kts").writeText(
      """
      versionCatalogs {
        create("ktorLibs") { from("io.ktor:ktor-version-catalog:3.5.2") }
      }
      """.trimIndent(),
    )

    assertEquals(
      KtorLibsCatalog.Coordinate("io.ktor", "ktor-version-catalog", "3.5.2"),
      catalog.findCoordinate(root),
    )
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `resolveAlias accepts catalog aliases artifact ids and accessors`() {
    val toml = """
      [libraries]
      server-core = {group = "io.ktor", name = "ktor-server-core", version.ref = "ktor" }
      server-htmlBuilder = {group = "io.ktor", name = "ktor-server-html-builder", version.ref = "ktor" }
    """.trimIndent()

    assertEquals("server-core", catalog.resolveAlias(toml, "server-core"))
    assertEquals("server-core", catalog.resolveAlias(toml, "ktor-server-core"))
    assertEquals("server-core", catalog.resolveAlias(toml, "server.core"))
    assertEquals("server-core", catalog.resolveAlias(toml, "io.ktor:ktor-server-core"))
    assertEquals("server-htmlBuilder", catalog.resolveAlias(toml, "server-html-builder"))
    assertEquals("server-htmlBuilder", catalog.resolveAlias(toml, "ktor-server-html-builder"))
    assertNull(catalog.resolveAlias(toml, "missing-lib"))
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun `loadToml prefers the gradle cache over downloading`() {
    val cacheRoot = Files.createTempDirectory("kitten-gradle-cache").toFile().also { it.deleteOnExit() }
    val tomlDir = File(cacheRoot, "io.ktor/ktor-version-catalog/3.5.2/abc123").apply { mkdirs() }
    File(tomlDir, "ktor-version-catalog-3.5.2.toml").writeText(SAMPLE_TOML)
    val cached = KtorLibsCatalog(
      catalogTomlLoader = CatalogTomlLoader { _, _, _ -> error("should not download") },
      gradleCacheRoot = cacheRoot,
    )

    assertEquals(
      SAMPLE_TOML,
      cached.loadToml(KtorLibsCatalog.Coordinate("io.ktor", "ktor-version-catalog", "3.5.2")),
    )
  }

  private companion object {
    const val SAMPLE_TOML = "[libraries]\nserver-core = {group = \"io.ktor\", name = \"ktor-server-core\" }\n"
  }
}
