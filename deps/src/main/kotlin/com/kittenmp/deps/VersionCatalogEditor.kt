package com.kittenmp.deps

import com.kittenmp.ai.ComprehensionDebt

/**
 * Adds or updates a single library entry in a Gradle version catalog.
 *
 * Editing is line-based on purpose: every line the edit does not touch — comments, blank lines,
 * section order, unrelated entries — is written back byte for byte.
 */
@ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
internal class VersionCatalogEditor {

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  enum class Change { ADDED, UPDATED, UNCHANGED, REMOVED }

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  data class UpsertResult(val content: String, val change: Change) {
    val updated: Boolean get() = change != Change.UNCHANGED
  }

  /**
   * Points `[versions]` and `[libraries].alias` at [match], creating either section if the catalog
   * does not have one yet. When the library already exists, its existing `version.ref` is reused.
   * Compose UI artifacts share `[versions].compose-ui` (or a sibling's ref) instead of minting a
   * per-artifact version.
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun upsert(catalog: String, alias: String, match: ArtifactMatch): UpsertResult {
    val newline = if (catalog.contains("\r\n")) "\r\n" else "\n"
    val endsWithNewline = catalog.isEmpty() || catalog.endsWith("\n")
    val lines = catalog.removeSuffix(newline).let { if (it.isEmpty()) emptyList() else it.lines() }
    val existing = libraryLine(lines, alias)
    val versionAlias = existing?.let { versionRefOf(it) }
      ?: chooseLibraryVersionAlias(lines, match, alias)

    val afterVersions = when {
      existing != null && versionRefOf(existing) == null -> EntryUpsert(lines, Change.UNCHANGED)
      existing == null && versionAlias != alias && hasVersion(lines, versionAlias) ->
        EntryUpsert(lines, Change.UNCHANGED)
      else -> upsertEntry(
        lines = lines,
        section = VERSIONS_SECTION,
        alias = versionAlias,
        entry = """$versionAlias = "${match.version}"""",
      )
    }

    val libraryEntry = if (existing != null && versionRefOf(existing) == null) {
      """$alias = { group = "${match.group}", name = "${match.name}", version = "${match.version}" }"""
    } else {
      """$alias = { group = "${match.group}", name = "${match.name}", version.ref = "$versionAlias" }"""
    }
    val libraries = upsertEntry(
      lines = afterVersions.lines,
      section = LIBRARIES_SECTION,
      alias = alias,
      entry = libraryEntry,
    )

    val content = libraries.lines.joinToString(newline) + if (endsWithNewline) newline else ""
    return UpsertResult(content, merge(afterVersions.change, libraries.change))
  }

  /** True when `[libraries]` already defines [alias]. */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun containsLibrary(catalog: String, alias: String): Boolean {
    val lines = catalog.lines()
    return sectionRanges(lines)
      .filter { it.name == LIBRARIES_SECTION }
      .any { range -> (range.first until range.last).any { keyOf(lines[it]) == alias } }
  }

  /**
   * Points `[versions]` and `[plugins].alias` at [match], creating either section if the catalog
   * does not have one yet. When the plugin already exists, its existing `version.ref` is reused.
   * New Kotlin Gradle plugins share `[versions].kotlin` (or a sibling plugin's ref) instead of
   * minting a per-plugin version.
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun upsertPlugin(catalog: String, alias: String, match: PluginMatch): UpsertResult {
    val newline = if (catalog.contains("\r\n")) "\r\n" else "\n"
    val endsWithNewline = catalog.isEmpty() || catalog.endsWith("\n")
    val lines = catalog.removeSuffix(newline).let { if (it.isEmpty()) emptyList() else it.lines() }
    val existing = pluginLine(lines, alias)
    val versionAlias = existing?.let { versionRefOf(it) }
      ?: choosePluginVersionAlias(lines, match.id, alias)

    val afterVersions = when {
      existing != null && versionRefOf(existing) == null -> EntryUpsert(lines, Change.UNCHANGED)
      existing == null && hasVersion(lines, versionAlias) -> EntryUpsert(lines, Change.UNCHANGED)
      else -> upsertEntry(
        lines = lines,
        section = VERSIONS_SECTION,
        alias = versionAlias,
        entry = """$versionAlias = "${match.version}"""",
      )
    }

    val pluginEntry = if (existing != null && versionRefOf(existing) == null) {
      """$alias = { id = "${match.id}", version = "${match.version}" }"""
    } else {
      """$alias = { id = "${match.id}", version.ref = "$versionAlias" }"""
    }
    val plugins = upsertEntry(
      lines = afterVersions.lines,
      section = PLUGINS_SECTION,
      alias = alias,
      entry = pluginEntry,
    )

    val content = plugins.lines.joinToString(newline) + if (endsWithNewline) newline else ""
    return UpsertResult(content, merge(afterVersions.change, plugins.change))
  }

  /** True when `[plugins]` already defines [alias]. */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun containsPlugin(catalog: String, alias: String): Boolean =
    parsePlugins(catalog).keys.any { it.equals(alias, ignoreCase = true) }

  /**
   * Resolves [term] to a `[plugins]` alias. Accepts the alias itself or the plugin id stored in
   * the catalog.
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun resolvePluginAlias(catalog: String, term: String): String? {
    val plugins = parsePlugins(catalog)
    if (plugins.isEmpty()) return null
    val trimmed = term.trim()
    val sanitized = sanitizeAlias(trimmed)
    plugins.keys.firstOrNull { it.equals(trimmed, ignoreCase = true) }?.let { return it }
    plugins.keys.firstOrNull { it.equals(sanitized, ignoreCase = true) }?.let { return it }
    plugins.entries.firstOrNull { it.value.equals(trimmed, ignoreCase = true) }?.let { return it.key }
    return null
  }

  /** The plugin id stored under [alias], or null when that alias is missing. */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun pluginId(catalog: String, alias: String): String? =
    parsePlugins(catalog).entries.firstOrNull { it.key.equals(alias, ignoreCase = true) }?.value

  /**
   * Picks a catalog alias for [match]: reuse an existing id mapping when there is one, otherwise
   * the last two segments of the plugin id, falling back to the full id if that pretty alias is
   * already taken by a different plugin.
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun choosePluginAlias(catalog: String, match: PluginMatch): String {
    resolvePluginAlias(catalog, match.id)?.let { return it }
    val pretty = pluginAliasFromId(match.id)
    val existingId = pluginId(catalog, pretty)
    if (existingId == null || existingId == match.id) return pretty
    return sanitizeAlias(match.id)
  }

  /**
   * Catalog alias for a plugin id. Kotlin Gradle plugins drop the `org.jetbrains.kotlin` prefix
   * and a `plugin` segment (`org.jetbrains.kotlin.plugin.compose` → `kotlin-compose`); everything
   * else uses the last two dotted segments.
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun pluginAliasFromId(pluginId: String): String {
    val parts = pluginId.split('.').filter { it.isNotEmpty() }
    if (pluginFamily(pluginId)?.prefix == KOTLIN_PLUGIN_PREFIX) {
      val rest = parts.drop(3).filter { it != "plugin" }
      val tokens = if (rest.isEmpty()) listOf("kotlin") else listOf("kotlin") + rest
      return sanitizeAlias(tokens.joinToString("-"))
    }
    val slice = if (parts.size >= 2) parts.takeLast(2) else parts
    return sanitizeAlias(slice.joinToString("-"))
  }

  /**
   * Drops `[libraries].alias` when present. The matching `[versions]` entry is removed only when
   * nothing else still references it.
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun remove(catalog: String, alias: String): UpsertResult {
    val newline = if (catalog.contains("\r\n")) "\r\n" else "\n"
    val endsWithNewline = catalog.isEmpty() || catalog.endsWith("\n")
    val lines = catalog.removeSuffix(newline).let { if (it.isEmpty()) emptyList() else it.lines() }
    val versionAlias = libraryLine(lines, alias)?.let { versionRefOf(it) } ?: alias

    val libraries = removeEntry(lines, LIBRARIES_SECTION, alias)
    val versions = if (!isVersionRefUsed(libraries.lines, versionAlias)) {
      removeEntry(libraries.lines, VERSIONS_SECTION, versionAlias)
    } else {
      EntryUpsert(libraries.lines, Change.UNCHANGED)
    }

    val content = versions.lines.joinToString(newline) + if (endsWithNewline) newline else ""
    val change = if (libraries.change == Change.REMOVED || versions.change == Change.REMOVED) {
      Change.REMOVED
    } else {
      Change.UNCHANGED
    }
    return UpsertResult(content, change)
  }

  /**
   * Drops `[plugins].alias` when present. The matching `[versions]` entry is removed only when
   * nothing else still references it.
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun removePlugin(catalog: String, alias: String): UpsertResult {
    val newline = if (catalog.contains("\r\n")) "\r\n" else "\n"
    val endsWithNewline = catalog.isEmpty() || catalog.endsWith("\n")
    val lines = catalog.removeSuffix(newline).let { if (it.isEmpty()) emptyList() else it.lines() }
    val versionAlias = pluginLine(lines, alias)?.let { versionRefOf(it) }

    val plugins = removeEntry(lines, PLUGINS_SECTION, alias)
    val versions = if (versionAlias != null && !isVersionRefUsed(plugins.lines, versionAlias)) {
      removeEntry(plugins.lines, VERSIONS_SECTION, versionAlias)
    } else {
      EntryUpsert(plugins.lines, Change.UNCHANGED)
    }

    val content = versions.lines.joinToString(newline) + if (endsWithNewline) newline else ""
    val change = if (plugins.change == Change.REMOVED || versions.change == Change.REMOVED) {
      Change.REMOVED
    } else {
      Change.UNCHANGED
    }
    return UpsertResult(content, change)
  }

  /**
   * Replaces the existing `alias = ...` line in [section] if there is one, otherwise appends the
   * entry after the section's last non-blank line.
   */
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun upsertEntry(
    lines: List<String>,
    section: String,
    alias: String,
    entry: String,
  ): EntryUpsert {
    val ranges = sectionRanges(lines).filter { it.name == section }

    for (range in ranges) {
      val index = (range.first until range.last).firstOrNull { keyOf(lines[it]) == alias } ?: continue
      if (lines[index].trim() == entry) return EntryUpsert(lines, Change.UNCHANGED)
      return EntryUpsert(lines.toMutableList().also { it[index] = entry }, Change.UPDATED)
    }

    val range = ranges.lastOrNull()
      ?: return EntryUpsert(lines + newSection(lines, section, entry), Change.ADDED)
    val insertAt = (range.first until range.last).lastOrNull { lines[it].isNotBlank() }?.plus(1)
      ?: range.first
    return EntryUpsert(lines.toMutableList().also { it.add(insertAt, entry) }, Change.ADDED)
  }

  /** Deletes every `alias = ...` line under [section], including duplicates across repeated headers. */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun removeEntry(lines: List<String>, section: String, alias: String): EntryUpsert {
    val ranges = sectionRanges(lines).filter { it.name == section }
    val indices = ranges.flatMap { range ->
      (range.first until range.last).filter { keyOf(lines[it]) == alias }
    }
    if (indices.isEmpty()) return EntryUpsert(lines, Change.UNCHANGED)
    val mutable = lines.toMutableList()
    for (index in indices.sortedDescending()) {
      mutable.removeAt(index)
    }
    return EntryUpsert(mutable, Change.REMOVED)
  }

  /** Half-open range of entry lines belonging to `[name]`, excluding the header itself. */
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun sectionRanges(lines: List<String>): List<SectionRange> {
    val headers = lines.indices.mapNotNull { index ->
      SECTION_HEADER.matchEntire(lines[index].trim())?.let { index to it.groupValues[1] }
    }
    return headers.mapIndexed { position, (index, name) ->
      val end = headers.getOrNull(position + 1)?.first ?: lines.size
      SectionRange(name = name, first = index + 1, last = end)
    }
  }

  /** The bare key a catalog line assigns to, or `null` for blanks, comments and headers. */
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun keyOf(line: String): String? {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("#") || '=' !in trimmed) return null
    return trimmed.substringBefore('=').trim().trim('"', '\'').ifEmpty { null }
  }

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun newSection(lines: List<String>, section: String, entry: String): List<String> {
    val separator = if (lines.isEmpty() || lines.last().isBlank()) emptyList() else listOf("")
    return separator + listOf("[$section]", entry)
  }

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun merge(vararg changes: Change): Change = when {
    Change.ADDED in changes -> Change.ADDED
    Change.UPDATED in changes -> Change.UPDATED
    else -> Change.UNCHANGED
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun chooseLibraryVersionAlias(
    lines: List<String>,
    match: ArtifactMatch,
    alias: String,
  ): String {
    if (!isComposeUiGroup(match.group)) return alias
    siblingComposeUiVersionRef(lines)?.let { return it }
    return COMPOSE_UI_VERSION_ALIAS
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun siblingComposeUiVersionRef(lines: List<String>): String? {
    for (range in sectionRanges(lines).filter { it.name == LIBRARIES_SECTION }) {
      for (index in range.first until range.last) {
        val group = groupOf(lines[index]) ?: continue
        if (!isComposeUiGroup(group)) continue
        versionRefOf(lines[index])?.let { return it }
      }
    }
    return null
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun libraryLine(lines: List<String>, alias: String): String? {
    for (range in sectionRanges(lines).filter { it.name == LIBRARIES_SECTION }) {
      val index = (range.first until range.last).firstOrNull { keyOf(lines[it]) == alias } ?: continue
      return lines[index]
    }
    return null
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun groupOf(line: String): String? {
    GROUP_VALUE.find(line)?.groupValues?.get(1)?.let { return it }
    val module = MODULE_VALUE.find(line)?.groupValues?.get(1) ?: return null
    val group = module.substringBefore(':', missingDelimiterValue = "")
    return group.ifEmpty { null }
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun choosePluginVersionAlias(lines: List<String>, pluginId: String, pluginAlias: String): String {
    val family = pluginFamily(pluginId) ?: return pluginAlias
    siblingVersionRef(lines, family)?.let { return it }
    return family.versionAlias
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun siblingVersionRef(lines: List<String>, family: PluginFamily): String? {
    for (range in sectionRanges(lines).filter { it.name == PLUGINS_SECTION }) {
      for (index in range.first until range.last) {
        val id = pluginIdOf(lines[index]) ?: continue
        if (pluginFamily(id)?.prefix != family.prefix) continue
        versionRefOf(lines[index])?.let { return it }
      }
    }
    return null
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun hasVersion(lines: List<String>, alias: String): Boolean =
    sectionRanges(lines)
      .filter { it.name == VERSIONS_SECTION }
      .any { range -> (range.first until range.last).any { keyOf(lines[it]) == alias } }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun parsePlugins(catalog: String): Map<String, String> {
    val lines = catalog.lines()
    val result = linkedMapOf<String, String>()
    for (range in sectionRanges(lines).filter { it.name == PLUGINS_SECTION }) {
      for (index in range.first until range.last) {
        val alias = keyOf(lines[index]) ?: continue
        val id = pluginIdOf(lines[index]) ?: continue
        result[alias] = id
      }
    }
    return result
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun pluginLine(lines: List<String>, alias: String): String? {
    for (range in sectionRanges(lines).filter { it.name == PLUGINS_SECTION }) {
      val index = (range.first until range.last).firstOrNull { keyOf(lines[it]) == alias } ?: continue
      return lines[index]
    }
    return null
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun pluginIdOf(line: String): String? = PLUGIN_ID.find(line)?.groupValues?.get(1)

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun versionRefOf(line: String): String? = VERSION_REF.find(line)?.groupValues?.get(1)

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun isVersionRefUsed(lines: List<String>, versionAlias: String): Boolean {
    val sections = setOf(LIBRARIES_SECTION, PLUGINS_SECTION)
    return sectionRanges(lines)
      .filter { it.name in sections }
      .any { range ->
        (range.first until range.last).any { versionRefOf(lines[it]) == versionAlias }
      }
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun sanitizeAlias(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun pluginFamily(pluginId: String): PluginFamily? = when {
    pluginId == KOTLIN_PLUGIN_PREFIX || pluginId.startsWith("$KOTLIN_PLUGIN_PREFIX.") ->
      PluginFamily(prefix = KOTLIN_PLUGIN_PREFIX, versionAlias = "kotlin")
    pluginId == ANDROID_PLUGIN_PREFIX || pluginId.startsWith("$ANDROID_PLUGIN_PREFIX.") ->
      PluginFamily(prefix = ANDROID_PLUGIN_PREFIX, versionAlias = "agp")
    else -> null
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private data class PluginFamily(val prefix: String, val versionAlias: String)

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private data class SectionRange(val name: String, val first: Int, val last: Int)

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private data class EntryUpsert(val lines: List<String>, val change: Change)

  companion object {
    private const val VERSIONS_SECTION = "versions"
    private const val LIBRARIES_SECTION = "libraries"
    private const val PLUGINS_SECTION = "plugins"
    private const val KOTLIN_PLUGIN_PREFIX = "org.jetbrains.kotlin"
    private const val ANDROID_PLUGIN_PREFIX = "com.android"
    private val SECTION_HEADER = Regex("""^\[([^\]]+)]$""")
    private val PLUGIN_ID = Regex("""id\s*=\s*"([^"]+)"""")
    private val VERSION_REF = Regex("""version\.ref\s*=\s*"([^"]+)"""")
    private val GROUP_VALUE = Regex("""group\s*=\s*"([^"]+)"""")
    private val MODULE_VALUE = Regex("""module\s*=\s*"([^"]+)"""")
    private const val COMPOSE_UI_VERSION_ALIAS = "compose-ui"

    @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
    private fun isComposeUiGroup(group: String): Boolean = group.endsWith(".compose.ui")
  }
}
