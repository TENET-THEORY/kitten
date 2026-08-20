package com.kittenmp.deps

import com.kittenmp.ai.ComprehensionDebt

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
internal class VersionCatalogEditor {
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun upsert(catalog: String, alias: String, match: ArtifactMatch): UpsertResult {
    val versionLine = """$alias = "${match.version}""""
    val libraryLine =
      """$alias = { group = "${match.group}", name = "${match.name}", version.ref = "$alias" }"""

    val sections = splitSections(catalog)
    val versions = sections["versions"] ?: Section("versions", emptyList())
    val libraries = sections["libraries"] ?: Section("libraries", emptyList())

    val versionsResult = upsertLine(versions.lines, alias, versionLine)
    val librariesResult = upsertLine(libraries.lines, alias, libraryLine)

    sections["versions"] = Section("versions", versionsResult.lines)
    sections["libraries"] = Section("libraries", librariesResult.lines)

    val updated = wasUpdated(versionsResult.changed, librariesResult.changed)
    return UpsertResult(
      content = render(sections, catalog),
      updated = updated,
    )
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun wasUpdated(versionChanged: Boolean, libraryChanged: Boolean): Boolean =
    versionChanged || libraryChanged

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun upsertLine(
    lines: List<String>,
    alias: String,
    newLine: String,
  ): LineUpsert {
    val keyPrefix = "$alias ="
    val index = lines.indexOfFirst { it.trimStart().startsWith(keyPrefix) }
    if (index >= 0) {
      if (lines[index].trim() == newLine) {
        return LineUpsert(lines, changed = false)
      }
      val updated = lines.toMutableList()
      updated[index] = newLine
      return LineUpsert(updated, changed = true)
    }
    val insertion = insertionIndex(lines)
    val updated = lines.toMutableList()
    updated.add(insertion, newLine)
    return LineUpsert(updated, changed = true)
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun insertionIndex(lines: List<String>): Int {
    if (lines.isEmpty()) return 0
    var lastContent = -1
    lines.forEachIndexed { index, line ->
      if (line.isNotBlank()) lastContent = index
    }
    return if (lastContent < 0) lines.size else lastContent + 1
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun splitSections(catalog: String): LinkedHashMap<String, Section> {
    val result = LinkedHashMap<String, Section>()
    var currentName: String? = null
    var currentLines = mutableListOf<String>()
    val preamble = mutableListOf<String>()

    catalog.lines().forEach { line ->
      val header = SECTION_HEADER.matchEntire(line.trim())
      if (header != null) {
        flushSection(result, currentName, currentLines, preamble)
        currentName = header.groupValues[1]
        currentLines = mutableListOf()
      } else if (currentName == null) {
        preamble.add(line)
      } else {
        currentLines.add(line)
      }
    }
    flushSection(result, currentName, currentLines, preamble)
    if (preamble.isNotEmpty() && !result.containsKey("")) {
      result[""] = Section("", preamble)
    }
    return result
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun flushSection(
    result: LinkedHashMap<String, Section>,
    name: String?,
    lines: MutableList<String>,
    preamble: MutableList<String>,
  ) {
    if (name == null) return
    if (preamble.isNotEmpty() && result.isEmpty()) {
      result[""] = Section("", preamble.toList())
      preamble.clear()
    }
    result[name] = Section(name, trimTrailingBlanks(lines))
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun trimTrailingBlanks(lines: List<String>): List<String> {
    var end = lines.size
    while (end > 0 && lines[end - 1].isBlank()) end--
    return lines.subList(0, end)
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun render(
    sections: LinkedHashMap<String, Section>,
    original: String,
  ): String {
    val endsWithNewline = original.endsWith("\n")
    val builder = StringBuilder()
    sections.entries.forEachIndexed { index, (name, section) ->
      if (index > 0) builder.append('\n')
      if (name.isNotEmpty()) {
        builder.append('[').append(name).append(']').append('\n')
      }
      section.lines.forEach { line ->
        builder.append(line).append('\n')
      }
    }
    val rendered = builder.toString()
    return if (endsWithNewline) rendered else rendered.trimEnd('\n')
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private data class Section(val name: String, val lines: List<String>)

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private data class LineUpsert(val lines: List<String>, val changed: Boolean)

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  data class UpsertResult(val content: String, val updated: Boolean)

  companion object {
    private val SECTION_HEADER = Regex("""^\[([^\]]+)]$""")
  }
}
