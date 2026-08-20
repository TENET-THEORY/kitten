package com.kittenmp.deps

import com.kittenmp.ai.ComprehensionDebt
import java.io.File

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
class DependencyInstaller(
  private val startDir: File = File(".").absoluteFile,
) {
  private val mavenCentralClient = MavenCentralClient()
  private val versionCatalogEditor = VersionCatalogEditor()

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun install(artifactId: String): String {
    val catalogFile = findCatalog(startDir)
      ?: error("Could not find gradle/libs.versions.toml (searched from ${startDir.path})")
    val match = mavenCentralClient.findLatest(artifactId)
    val alias = sanitizeAlias(match.name)
    val original = catalogFile.readText()
    val hadAlias = hasAlias(original, alias)
    val result = versionCatalogEditor.upsert(original, alias, match)
    if (result.content != original) {
      catalogFile.writeText(result.content)
    }
    val action = when {
      !result.updated -> "unchanged"
      hadAlias -> "updated"
      else -> "added"
    }
    return "$action $alias ${match.version} (${match.group}:${match.name})"
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun hasAlias(catalog: String, alias: String): Boolean =
    Regex("""(?m)^${Regex.escape(alias)}\s*=""").containsMatchIn(catalog)

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun findCatalog(from: File): File? {
    var dir: File? = from
    while (dir != null) {
      val candidate = File(dir, "gradle/libs.versions.toml")
      if (candidate.isFile) return candidate
      dir = dir.parentFile
    }
    return null
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun sanitizeAlias(artifactId: String): String =
    artifactId
      .lowercase()
      .replace('_', '-')
      .replace('.', '-')
      .replace(Regex("[^a-z0-9-]"), "-")
      .trim('-')
}
