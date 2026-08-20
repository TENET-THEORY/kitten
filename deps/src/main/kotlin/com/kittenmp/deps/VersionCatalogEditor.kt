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
   * Points `[versions].alias` and `[libraries].alias` at [match], creating either section if the
   * catalog does not have one yet.
   */
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun upsert(catalog: String, alias: String, match: ArtifactMatch): UpsertResult {
    val newline = if (catalog.contains("\r\n")) "\r\n" else "\n"
    val endsWithNewline = catalog.isEmpty() || catalog.endsWith("\n")
    val lines = catalog.removeSuffix(newline).let { if (it.isEmpty()) emptyList() else it.lines() }

    val versions = upsertEntry(
      lines = lines,
      section = VERSIONS_SECTION,
      alias = alias,
      entry = """$alias = "${match.version}"""",
    )
    val libraries = upsertEntry(
      lines = versions.lines,
      section = LIBRARIES_SECTION,
      alias = alias,
      entry = """$alias = { group = "${match.group}", name = "${match.name}", version.ref = "$alias" }""",
    )

    val content = libraries.lines.joinToString(newline) + if (endsWithNewline) newline else ""
    return UpsertResult(content, merge(versions.change, libraries.change))
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
   * Drops `[versions].alias` and `[libraries].alias` when present. Other sections and unrelated
   * lines are left untouched.
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun remove(catalog: String, alias: String): UpsertResult {
    val newline = if (catalog.contains("\r\n")) "\r\n" else "\n"
    val endsWithNewline = catalog.isEmpty() || catalog.endsWith("\n")
    val lines = catalog.removeSuffix(newline).let { if (it.isEmpty()) emptyList() else it.lines() }

    val versions = removeEntry(lines, VERSIONS_SECTION, alias)
    val libraries = removeEntry(versions.lines, LIBRARIES_SECTION, alias)

    val content = libraries.lines.joinToString(newline) + if (endsWithNewline) newline else ""
    val change = if (versions.change == Change.REMOVED || libraries.change == Change.REMOVED) {
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

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private data class SectionRange(val name: String, val first: Int, val last: Int)

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private data class EntryUpsert(val lines: List<String>, val change: Change)

  companion object {
    private const val VERSIONS_SECTION = "versions"
    private const val LIBRARIES_SECTION = "libraries"
    private val SECTION_HEADER = Regex("""^\[([^\]]+)]$""")
  }
}
