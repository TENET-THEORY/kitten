package com.kittenmp.deps

import com.kittenmp.ai.ComprehensionDebt
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Resolves a Maven Central artifact and writes it into the nearest `gradle/libs.versions.toml`.
 *
 * Holds an HTTP client, so callers must [close] it — `DependencyInstaller().use { ... }`.
 */
@ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
class DependencyInstaller internal constructor(
  private val startDir: File,
  private val mavenCentralClient: MavenCentralClient,
) : AutoCloseable {

  constructor(startDir: File = File(".").absoluteFile) : this(startDir, MavenCentralClient())

  private val versionCatalogEditor = VersionCatalogEditor()

  /**
   * Adds or updates [term] (`artifactId` or `group:artifactId`) and returns a one-line summary of
   * what changed.
   */
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun install(term: String): String {
    val catalogFile = findCatalog(startDir)
      ?: error("Could not find gradle/libs.versions.toml (searched from ${startDir.path})")
    val match = runBlocking { mavenCentralClient.findLatest(term) }
    val alias = sanitizeAlias(match.name)
    val original = catalogFile.readText()
    val result = versionCatalogEditor.upsert(original, alias, match)
    if (result.content != original) {
      catalogFile.writeText(result.content)
    }
    return summarize(result.change, alias, match)
  }

  /**
   * Removes [term] (`artifactId` or `group:artifactId`) from the nearest version catalog and
   * returns a one-line summary.
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun uninstall(term: String): String {
    val catalogFile = findCatalog(startDir)
      ?: error("Could not find gradle/libs.versions.toml (searched from ${startDir.path})")
    val alias = sanitizeAlias(ArtifactQuery.parse(term).name)
    val original = catalogFile.readText()
    val result = versionCatalogEditor.remove(original, alias)
    if (result.change == VersionCatalogEditor.Change.UNCHANGED) {
      error("No library '$alias' in ${catalogFile.path}")
    }
    catalogFile.writeText(result.content)
    return "removed $alias"
  }

  override fun close() = mavenCentralClient.close()

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun summarize(change: VersionCatalogEditor.Change, alias: String, match: ArtifactMatch): String {
    val summary = "${change.name.lowercase()} $alias ${match.version} (${match.coordinate})"
    if (match.alternatives.isEmpty()) return summary
    return "$summary\nother artifacts named '${match.name}': ${match.alternatives.joinToString()}"
  }

  /** Walks up from [from] to the first directory containing a Gradle version catalog. */
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun findCatalog(from: File): File? = generateSequence(from) { it.parentFile }
    .map { File(it, "gradle/libs.versions.toml") }
    .firstOrNull { it.isFile }

  /** Converts an artifact name into a version catalog alias (lowercase, `-` separated). */
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun sanitizeAlias(artifactId: String): String =
    artifactId
      .lowercase()
      .replace(Regex("[^a-z0-9]+"), "-")
      .trim('-')
}
