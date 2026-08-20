package com.kittenmp.deps

import com.kittenmp.ai.ComprehensionDebt

/**
 * Inserts `implementation(libs…)` into a module `build.gradle(.kts)` while leaving unrelated
 * lines untouched.
 */
@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
internal class BuildGradleEditor {

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  enum class Change { ADDED, UNCHANGED }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  data class Result(val content: String, val change: Change)

  /**
   * Ensures `implementation(libs.<accessor>)` is present for [alias]. Hyphens in the alias become
   * dots in the Gradle accessor (`ktor-client-core` → `libs.ktor.client.core`).
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun addImplementation(script: String, alias: String): Result {
    val accessor = aliasToAccessor(alias)
    val entry = "implementation(libs.$accessor)"
    val newline = if (script.contains("\r\n")) "\r\n" else "\n"
    val endsWithNewline = script.isEmpty() || script.endsWith("\n")
    val lines = script.removeSuffix(newline).let { if (it.isEmpty()) emptyList() else it.lines() }

    if (lines.any { isSameImplementation(it, accessor) }) {
      return Result(script, Change.UNCHANGED)
    }

    val range = findDependenciesBlock(lines)
    val updated = if (range == null) {
      appendDependenciesBlock(lines, entry)
    } else {
      insertIntoBlock(lines, range, entry)
    }

    val content = updated.joinToString(newline) + if (endsWithNewline) newline else ""
    return Result(content, Change.ADDED)
  }

  /** Converts a version-catalog alias into a `libs.` accessor path. */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun aliasToAccessor(alias: String): String = alias.replace('-', '.')

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun isSameImplementation(line: String, accessor: String): Boolean {
    val normalized = line.trim().replace(Regex("\\s+"), "")
    return normalized == "implementation(libs.$accessor)"
  }

  /**
   * Half-open range of lines inside the top-level `dependencies { }` body (excluding the opening
   * and closing brace lines), or null when the block is missing.
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun findDependenciesBlock(lines: List<String>): DependenciesBlock? {
    var depth = 0
    var blockStart: Int? = null
    for (index in lines.indices) {
      val trimmed = lines[index].trim()
      val opensDependencies = depth == 0 && DEPENDENCIES_OPEN.matchEntire(trimmed) != null
      val openCount = lines[index].count { it == '{' }
      val closeCount = lines[index].count { it == '}' }

      if (opensDependencies) {
        if (openCount > closeCount) {
          blockStart = index + 1
          depth += openCount - closeCount
          continue
        }
        if (openCount == closeCount && openCount > 0) {
          return DependenciesBlock.Inline(index)
        }
      }

      if (blockStart != null) {
        depth += openCount - closeCount
        if (depth == 0) return DependenciesBlock.Multiline(blockStart until index)
      } else {
        depth += openCount - closeCount
      }
    }
    return null
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun insertIntoBlock(lines: List<String>, block: DependenciesBlock, entry: String): List<String> {
    return when (block) {
      is DependenciesBlock.Inline -> {
        val indent = "  "
        lines.toMutableList().also {
          it[block.lineIndex] = "dependencies {"
          it.add(block.lineIndex + 1, indent + entry)
          it.add(block.lineIndex + 2, "}")
        }
      }
      is DependenciesBlock.Multiline -> {
        val body = block.body
        val indent = detectIndent(lines, body)
        val insertAt = body.lastOrNull { lines[it].isNotBlank() }?.plus(1) ?: body.start
        lines.toMutableList().also { it.add(insertAt, indent + entry) }
      }
    }
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun appendDependenciesBlock(lines: List<String>, entry: String): List<String> {
    val separator = if (lines.isEmpty() || lines.last().isBlank()) emptyList() else listOf("")
    return lines + separator + listOf("dependencies {", "  $entry", "}")
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

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private sealed class DependenciesBlock {
    @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
    data class Multiline(val body: IntRange) : DependenciesBlock()

    @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
    data class Inline(val lineIndex: Int) : DependenciesBlock()
  }

  companion object {
    private val DEPENDENCIES_OPEN = Regex("""^dependencies\s*\{.*$""")
  }
}
