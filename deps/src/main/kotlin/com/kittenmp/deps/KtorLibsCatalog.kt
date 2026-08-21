package com.kittenmp.deps

import com.kittenmp.ai.ComprehensionDebt
import java.io.File

/**
 * Resolves libraries from a project's published `ktorLibs` version catalog
 * (`io.ktor:ktor-version-catalog`), declared in `settings.gradle(.kts)`.
 */
@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
internal class KtorLibsCatalog(
  private val catalogTomlLoader: CatalogTomlLoader,
  private val gradleCacheRoot: File = defaultGradleCacheRoot(),
) {

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  data class Coordinate(val group: String, val name: String, val version: String) {
    val notation: String get() = "$group:$name:$version"
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun findCoordinate(projectRoot: File): Coordinate? {
    val settings = findSettings(projectRoot) ?: return null
    return parseCoordinate(settings.readText())
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun loadToml(coordinate: Coordinate): String {
    findInGradleCache(coordinate)?.readText()?.let { return it }
    return catalogTomlLoader.load(coordinate.group, coordinate.name, coordinate.version)
  }

  /**
   * Maps a user term (`server-core`, `ktor-server-core`, `io.ktor:ktor-server-core`, or a dotted
   * accessor) onto the catalog alias that Gradle expects, preserving catalog casing.
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun resolveAlias(catalogToml: String, term: String): String? {
    val libraries = parseLibraries(catalogToml)
    if (libraries.isEmpty()) return null

    val query = ArtifactQuery.parse(term)
    val candidates = candidateAliases(query)
    for (candidate in candidates) {
      val match = libraries.keys.firstOrNull { it.equals(candidate, ignoreCase = true) }
      if (match != null) return match
      val normalized = normalizeKey(candidate)
      val fuzzy = libraries.keys.firstOrNull { normalizeKey(it) == normalized }
      if (fuzzy != null) return fuzzy
    }

    if (query.group != null) {
      libraries.entries
        .firstOrNull {
          it.value.group.equals(query.group, ignoreCase = true) &&
            it.value.name.equals(query.name, ignoreCase = true)
        }
        ?.let { return it.key }
    }

    return libraries.entries
      .firstOrNull { it.value.name.equals(query.name, ignoreCase = true) }
      ?.key
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun parseCoordinate(settingsText: String): Coordinate? {
    val match = KTOR_LIBS_FROM.find(settingsText) ?: return null
    val parts = match.groupValues[1].split(':')
    if (parts.size != 3 || parts.any { it.isBlank() }) return null
    return Coordinate(parts[0], parts[1], parts[2])
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun candidateAliases(query: ArtifactQuery): List<String> {
    val raw = query.name.trim()
    val dotted = raw.replace('.', '-')
    val sanitized = sanitizeAlias(raw)
    return listOf(raw, dotted, sanitized)
      .flatMap { listOf(it, stripKtorPrefix(it)) }
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .distinct()
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun stripKtorPrefix(value: String): String =
    if (value.startsWith("ktor-", ignoreCase = true)) value.substring(5) else value

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun sanitizeAlias(artifactId: String): String =
    artifactId
      .lowercase()
      .replace(Regex("[^a-z0-9]+"), "-")
      .trim('-')

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun normalizeKey(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9]"), "")

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun parseLibraries(catalogToml: String): Map<String, LibraryRef> {
    val lines = catalogToml.lines()
    val ranges = sectionRanges(lines).filter { it.name == LIBRARIES_SECTION }
    val result = linkedMapOf<String, LibraryRef>()
    for (range in ranges) {
      for (index in range.first until range.last) {
        val alias = keyOf(lines[index]) ?: continue
        val group = GROUP_VALUE.find(lines[index])?.groupValues?.get(1).orEmpty()
        val name = NAME_VALUE.find(lines[index])?.groupValues?.get(1).orEmpty()
        if (name.isNotEmpty()) result[alias] = LibraryRef(group, name)
      }
    }
    return result
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun sectionRanges(lines: List<String>): List<SectionRange> {
    val headers = lines.indices.mapNotNull { index ->
      SECTION_HEADER.matchEntire(lines[index].trim())?.let { index to it.groupValues[1] }
    }
    return headers.mapIndexed { position, (index, name) ->
      val end = headers.getOrNull(position + 1)?.first ?: lines.size
      SectionRange(name = name, first = index + 1, last = end)
    }
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun keyOf(line: String): String? {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("#") || '=' !in trimmed) return null
    return trimmed.substringBefore('=').trim().trim('"', '\'').ifEmpty { null }
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun findSettings(projectRoot: File): File? =
    listOf("settings.gradle.kts", "settings.gradle")
      .map { File(projectRoot, it) }
      .firstOrNull { it.isFile }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun findInGradleCache(coordinate: Coordinate): File? {
    val base = File(gradleCacheRoot, "${coordinate.group}/${coordinate.name}/${coordinate.version}")
    if (!base.isDirectory) return null
    return base.walkTopDown().firstOrNull { it.isFile && it.name.endsWith(".toml") }
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private data class LibraryRef(val group: String, val name: String)

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private data class SectionRange(val name: String, val first: Int, val last: Int)

  companion object {
    const val EXTENSION = "ktorLibs"
    private const val LIBRARIES_SECTION = "libraries"
    private val SECTION_HEADER = Regex("""^\[([^\]]+)]$""")
    private val GROUP_VALUE = Regex("""group\s*=\s*"([^"]+)"""")
    private val NAME_VALUE = Regex("""name\s*=\s*"([^"]+)"""")
    private val KTOR_LIBS_FROM = Regex(
      """create\s*\(\s*"ktorLibs"\s*\)\s*\{[^}]*?from\s*\(\s*"([^"]+)"\s*\)""",
      setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
    private fun defaultGradleCacheRoot(): File =
      File(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1")
  }
}

/** Loads a published Gradle version-catalog TOML from Maven Central (or a test double). */
@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
internal fun interface CatalogTomlLoader {
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun load(group: String, name: String, version: String): String
}
