package com.kittenmp.deps

import com.kittenmp.ai.ComprehensionDebt

/**
 * Inserts catalog accessors into a module `build.gradle(.kts)` while leaving unrelated lines
 * untouched.
 */
@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
internal class BuildGradleEditor {

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  enum class Change { ADDED, UNCHANGED }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  data class Result(val content: String, val change: Change)

  /**
   * Ensures `implementation(<catalog>.<accessor>)` is present for [alias]. Hyphens in the alias
   * become dots in the Gradle accessor (`server-core` → `ktorLibs.server.core`).
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun addImplementation(
    script: String,
    alias: String,
    catalogExtension: String = DEFAULT_CATALOG_EXTENSION,
  ): Result {
    val accessor = aliasToAccessor(alias)
    val entry = "implementation($catalogExtension.$accessor)"
    return insertEntry(
      script = script,
      alreadyPresent = { isSameImplementation(it, catalogExtension, accessor) },
      blockName = DEPENDENCIES_BLOCK,
      entry = entry,
      missingBlock = { appendNamedBlock(it, DEPENDENCIES_BLOCK, entry) },
    )
  }

  /**
   * Ensures `alias(<catalog>.plugins.<accessor>)` is present for [alias]. When [applyFalse] is
   * set, the declaration is `alias(...) apply false` (root build scripts).
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun addPlugin(
    script: String,
    alias: String,
    catalogExtension: String = DEFAULT_CATALOG_EXTENSION,
    applyFalse: Boolean = false,
  ): Result {
    val accessor = aliasToAccessor(alias)
    val entry = pluginEntry(catalogExtension, accessor, applyFalse)
    return insertEntry(
      script = script,
      alreadyPresent = { isSamePlugin(it, catalogExtension, accessor) },
      blockName = PLUGINS_BLOCK,
      entry = entry,
      missingBlock = { prependNamedBlock(it, PLUGINS_BLOCK, entry) },
    )
  }

  /** True when [script] already declares `alias(<catalog>.plugins.<accessor>)`. */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun hasPlugin(
    script: String,
    alias: String,
    catalogExtension: String = DEFAULT_CATALOG_EXTENSION,
  ): Boolean {
    val accessor = aliasToAccessor(alias)
    return script.lineSequence().any { isSamePlugin(it, catalogExtension, accessor) }
  }

  /** `alias(libs.plugins.kotlin.jvm)` or `alias(libs.plugins.kotlin.jvm) apply false`. */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun pluginEntry(
    catalogExtension: String,
    accessor: String,
    applyFalse: Boolean,
  ): String {
    val aliasCall = "alias($catalogExtension.plugins.$accessor)"
    return if (applyFalse) "$aliasCall apply false" else aliasCall
  }

  /** Converts a version-catalog alias into a catalog accessor path. */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun aliasToAccessor(alias: String): String = alias.replace('-', '.')

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun insertEntry(
    script: String,
    alreadyPresent: (String) -> Boolean,
    blockName: String,
    entry: String,
    missingBlock: (List<String>) -> List<String>,
  ): Result {
    val newline = if (script.contains("\r\n")) "\r\n" else "\n"
    val endsWithNewline = script.isEmpty() || script.endsWith("\n")
    val lines = script.removeSuffix(newline).let { if (it.isEmpty()) emptyList() else it.lines() }

    if (lines.any(alreadyPresent)) {
      return Result(script, Change.UNCHANGED)
    }

    val range = findTopLevelBlock(lines, blockName)
    val updated = if (range == null) {
      missingBlock(lines)
    } else {
      insertIntoBlock(lines, range, blockName, entry)
    }

    val content = updated.joinToString(newline) + if (endsWithNewline) newline else ""
    return Result(content, Change.ADDED)
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun isSameImplementation(
    line: String,
    catalogExtension: String,
    accessor: String,
  ): Boolean {
    val normalized = line.trim().replace(Regex("\\s+"), "")
    return normalized == "implementation($catalogExtension.$accessor)"
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun isSamePlugin(
    line: String,
    catalogExtension: String,
    accessor: String,
  ): Boolean {
    val normalized = line.trim().replace(Regex("\\s+"), "")
    return normalized.startsWith("alias($catalogExtension.plugins.$accessor)")
  }

  /**
   * Half-open range of lines inside a top-level `name { }` body (excluding the opening and closing
   * brace lines), or null when the block is missing.
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun findTopLevelBlock(lines: List<String>, blockName: String): ScriptBlock? {
    val opener = blockOpener(blockName)
    var depth = 0
    var blockStart: Int? = null
    for (index in lines.indices) {
      val trimmed = lines[index].trim()
      val opensBlock = depth == 0 && opener.matchEntire(trimmed) != null
      val openCount = lines[index].count { it == '{' }
      val closeCount = lines[index].count { it == '}' }

      if (opensBlock) {
        if (openCount > closeCount) {
          blockStart = index + 1
          depth += openCount - closeCount
          continue
        }
        if (openCount == closeCount && openCount > 0) {
          return ScriptBlock.Inline(index)
        }
      }

      if (blockStart != null) {
        depth += openCount - closeCount
        if (depth == 0) return ScriptBlock.Multiline(blockStart until index)
      } else {
        depth += openCount - closeCount
      }
    }
    return null
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun insertIntoBlock(
    lines: List<String>,
    block: ScriptBlock,
    blockName: String,
    entry: String,
  ): List<String> {
    return when (block) {
      is ScriptBlock.Inline -> {
        val indent = "  "
        lines.toMutableList().also {
          it[block.lineIndex] = "$blockName {"
          it.add(block.lineIndex + 1, indent + entry)
          it.add(block.lineIndex + 2, "}")
        }
      }
      is ScriptBlock.Multiline -> {
        val body = block.body
        val indent = detectIndent(lines, body)
        val insertAt = body.lastOrNull { lines[it].isNotBlank() }?.plus(1) ?: body.start
        lines.toMutableList().also { it.add(insertAt, indent + entry) }
      }
    }
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun appendNamedBlock(lines: List<String>, blockName: String, entry: String): List<String> {
    val separator = if (lines.isEmpty() || lines.last().isBlank()) emptyList() else listOf("")
    return lines + separator + listOf("$blockName {", "  $entry", "}")
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun prependNamedBlock(lines: List<String>, blockName: String, entry: String): List<String> {
    val block = listOf("$blockName {", "  $entry", "}")
    if (lines.isEmpty()) return block
    val separator = if (lines.first().isBlank()) emptyList() else listOf("")
    return block + separator + lines
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun detectIndent(lines: List<String>, body: IntRange): String {
    for (index in body) {
      val line = lines[index]
      if (line.isBlank()) continue
      val leading = line.takeWhile { it == ' ' || it == '\t' }
      if (leading.isNotEmpty()) return leading
    }
    return "  "
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun blockOpener(blockName: String): Regex = Regex("^$blockName\\s*\\{.*$")

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private sealed class ScriptBlock {
    @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
    data class Multiline(val body: IntRange) : ScriptBlock()

    @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
    data class Inline(val lineIndex: Int) : ScriptBlock()
  }

  companion object {
    const val DEFAULT_CATALOG_EXTENSION = "libs"
    private const val DEPENDENCIES_BLOCK = "dependencies"
    private const val PLUGINS_BLOCK = "plugins"
  }
}
