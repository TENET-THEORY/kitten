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
  data class Result(val content: String, val change: Change, val sourceSet: String? = null)

  /**
   * Ensures `implementation(<catalog>.<accessor>)` is present for [alias]. Hyphens in the alias
   * become dots in the Gradle accessor (`server-core` → `ktorLibs.server.core`).
   *
   * When [sourceSet] is set, the entry goes in that KMP source set. When it is omitted and the
   * script already has `kotlin { sourceSets { } }`, [DEFAULT_KMP_SOURCE_SET] is used.
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun addImplementation(
    script: String,
    alias: String,
    catalogExtension: String = DEFAULT_CATALOG_EXTENSION,
    sourceSet: String? = null,
  ): Result {
    val accessor = aliasToAccessor(alias)
    val entry = "implementation($catalogExtension.$accessor)"
    val alreadyPresent: (String) -> Boolean = { isSameImplementation(it, catalogExtension, accessor) }
    val targetSourceSet = sourceSet ?: inferredSourceSet(script)
    if (targetSourceSet != null) {
      return addSourceSetImplementation(script, targetSourceSet, entry, alreadyPresent)
    }
    return insertEntry(
      script = script,
      alreadyPresent = alreadyPresent,
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
  private fun addSourceSetImplementation(
    script: String,
    sourceSet: String,
    entry: String,
    alreadyPresent: (String) -> Boolean,
  ): Result {
    val newline = if (script.contains("\r\n")) "\r\n" else "\n"
    val endsWithNewline = script.isEmpty() || script.endsWith("\n")
    val lines = script.removeSuffix(newline).let { if (it.isEmpty()) emptyList() else it.lines() }
    val updated = insertIntoSourceSet(lines, sourceSet, entry, alreadyPresent) ?: return Result(
      script,
      Change.UNCHANGED,
      sourceSet,
    )
    val content = updated.joinToString(newline) + if (endsWithNewline) newline else ""
    return Result(content, Change.ADDED, sourceSet)
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun insertIntoSourceSet(
    lines: List<String>,
    sourceSet: String,
    entry: String,
    alreadyPresent: (String) -> Boolean,
  ): List<String>? {
    val kotlinBlock = findBlock(lines, KOTLIN_BLOCK)
    val sourceSetsBlock = kotlinBlock?.bodyRange()?.let { findBlock(lines, SOURCE_SETS_BLOCK, it) }
    val existing = sourceSetsBlock?.let { findSourceSetTarget(lines, sourceSet, it) }
      ?: kotlinBlock?.bodyRange()?.let { findQualifiedSourceSet(lines, sourceSet, it) }

    return when (existing) {
      is SourceSetTarget.Dependencies -> {
        if (linesIn(lines, existing.block).any(alreadyPresent)) null
        else insertIntoBlock(lines, existing.block, existing.blockName, entry)
      }
      is SourceSetTarget.NeedsDependencies -> insertDependenciesInto(
        lines,
        existing.container,
        existing.containerName,
        entry,
      )
      null -> when {
        sourceSetsBlock != null -> insertNewSourceSet(lines, sourceSetsBlock, sourceSet, entry)
        kotlinBlock != null && detectQualifiedStyle(lines, kotlinBlock) ->
          insertQualifiedSourceSet(lines, kotlinBlock, sourceSet, entry)
        kotlinBlock != null -> insertSourceSetsIntoKotlin(lines, kotlinBlock, sourceSet, entry)
        else -> appendKotlinSourceSets(lines, sourceSet, entry)
      }
    }
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun inferredSourceSet(script: String): String? {
    val lines = script.removeSuffix("\r\n").removeSuffix("\n").let {
      if (it.isEmpty()) emptyList() else it.lines()
    }
    return if (hasKotlinSourceSets(lines)) DEFAULT_KMP_SOURCE_SET else null
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun hasKotlinSourceSets(lines: List<String>): Boolean {
    val kotlin = findBlock(lines, KOTLIN_BLOCK) ?: return false
    return when (kotlin) {
      is ScriptBlock.Inline -> SOURCE_SETS_BLOCK in lines[kotlin.lineIndex]
      is ScriptBlock.Multiline -> findBlock(lines, SOURCE_SETS_BLOCK, kotlin.body) != null ||
        lines.slice(kotlin.body).any { QUALIFIED_SOURCE_SET.matches(it.trim()) }
    }
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun findSourceSetTarget(
    lines: List<String>,
    sourceSet: String,
    sourceSets: ScriptBlock,
  ): SourceSetTarget? {
    val search = sourceSets.bodyRange() ?: return null
    findBlock(lines, "$sourceSet.dependencies", search)?.let {
      return SourceSetTarget.Dependencies(it, "$sourceSet.dependencies")
    }
    findBlock(lines, "val $sourceSet by getting", search)?.let { container ->
      return sourceSetContainerTarget(lines, container, "val $sourceSet by getting")
    }
    findBlock(lines, sourceSet, search)?.let { container ->
      return sourceSetContainerTarget(lines, container, sourceSet)
    }
    return null
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun findQualifiedSourceSet(
    lines: List<String>,
    sourceSet: String,
    kotlinBody: IntRange,
  ): SourceSetTarget? {
    val name = "$SOURCE_SETS_BLOCK.$sourceSet.dependencies"
    val block = findBlock(lines, name, kotlinBody) ?: return null
    return SourceSetTarget.Dependencies(block, name)
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun sourceSetContainerTarget(
    lines: List<String>,
    container: ScriptBlock,
    containerName: String,
  ): SourceSetTarget {
    val search = container.bodyRange()
    val dependencies = search?.let { findBlock(lines, DEPENDENCIES_BLOCK, it) }
    return if (dependencies != null) {
      SourceSetTarget.Dependencies(dependencies, DEPENDENCIES_BLOCK)
    } else {
      SourceSetTarget.NeedsDependencies(container, containerName)
    }
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun insertNewSourceSet(
    lines: List<String>,
    sourceSets: ScriptBlock,
    sourceSet: String,
    entry: String,
  ): List<String> {
    val style = detectSourceSetStyle(lines, sourceSets)
    val indent = blockIndent(lines, sourceSets)
    return insertLinesIntoBlock(
      lines,
      sourceSets,
      SOURCE_SETS_BLOCK,
      sourceSetBlockLines(style, sourceSet, entry, indent.child, indent.grandchild()),
    )
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun insertQualifiedSourceSet(
    lines: List<String>,
    kotlin: ScriptBlock,
    sourceSet: String,
    entry: String,
  ): List<String> {
    val indent = blockIndent(lines, kotlin)
    return insertLinesIntoBlock(
      lines,
      kotlin,
      KOTLIN_BLOCK,
      sourceSetBlockLines(SourceSetStyle.QUALIFIED, sourceSet, entry, indent.child, indent.grandchild()),
    )
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun insertSourceSetsIntoKotlin(
    lines: List<String>,
    kotlin: ScriptBlock,
    sourceSet: String,
    entry: String,
  ): List<String> {
    val indent = blockIndent(lines, kotlin)
    val newLines = listOf("${indent.child}$SOURCE_SETS_BLOCK {") +
      sourceSetBlockLines(
        SourceSetStyle.ACCESSOR,
        sourceSet,
        entry,
        indent.grandchild(),
        indent.greatGrandchild(),
      ) +
      listOf("${indent.child}}")
    return insertLinesIntoBlock(lines, kotlin, KOTLIN_BLOCK, newLines)
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun appendKotlinSourceSets(
    lines: List<String>,
    sourceSet: String,
    entry: String,
  ): List<String> {
    val inner = "  "
    val child = "    "
    val block = listOf(
      "$KOTLIN_BLOCK {",
      "$inner$SOURCE_SETS_BLOCK {",
    ) + sourceSetBlockLines(SourceSetStyle.ACCESSOR, sourceSet, entry, child, "$child  ") + listOf(
      "$inner}",
      "}",
    )
    val separator = if (lines.isEmpty() || lines.last().isBlank()) emptyList() else listOf("")
    return lines + separator + block
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun insertDependenciesInto(
    lines: List<String>,
    container: ScriptBlock,
    containerName: String,
    entry: String,
  ): List<String> {
    val indent = blockIndent(lines, container)
    val newLines = listOf(
      "${indent.child}$DEPENDENCIES_BLOCK {",
      "${indent.grandchild()}$entry",
      "${indent.child}}",
    )
    return insertLinesIntoBlock(lines, container, containerName, newLines)
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun sourceSetBlockLines(
    style: SourceSetStyle,
    sourceSet: String,
    entry: String,
    indent: String,
    child: String,
  ): List<String> = when (style) {
    SourceSetStyle.ACCESSOR -> listOf(
      "$indent$sourceSet.dependencies {",
      "$child$entry",
      "$indent}",
    )
    SourceSetStyle.GETTING -> listOf(
      "${indent}val $sourceSet by getting {",
      "${child}$DEPENDENCIES_BLOCK {",
      "${nextIndent(indent, child)}$entry",
      "$child}",
      "$indent}",
    )
    SourceSetStyle.NAMED -> listOf(
      "$indent$sourceSet {",
      "${child}$DEPENDENCIES_BLOCK {",
      "${nextIndent(indent, child)}$entry",
      "$child}",
      "$indent}",
    )
    SourceSetStyle.QUALIFIED -> listOf(
      "$indent$SOURCE_SETS_BLOCK.$sourceSet.dependencies {",
      "$child$entry",
      "$indent}",
    )
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun detectSourceSetStyle(lines: List<String>, sourceSets: ScriptBlock): SourceSetStyle {
    val search = sourceSets.bodyRange() ?: return SourceSetStyle.ACCESSOR
    for (index in search) {
      val trimmed = lines[index].trim()
      when {
        GETTING_SOURCE_SET.matches(trimmed) -> return SourceSetStyle.GETTING
        ACCESSOR_SOURCE_SET.matches(trimmed) -> return SourceSetStyle.ACCESSOR
        NAMED_SOURCE_SET.matches(trimmed) -> return SourceSetStyle.NAMED
      }
    }
    return SourceSetStyle.ACCESSOR
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun detectQualifiedStyle(lines: List<String>, kotlin: ScriptBlock): Boolean {
    val search = kotlin.bodyRange() ?: return false
    return lines.slice(search).any { QUALIFIED_SOURCE_SET.matches(it.trim()) }
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun insertLinesIntoBlock(
    lines: List<String>,
    block: ScriptBlock,
    blockName: String,
    newLines: List<String>,
  ): List<String> = when (block) {
    is ScriptBlock.Inline -> {
      val indent = openingIndent(lines[block.lineIndex])
      lines.toMutableList().also { updated ->
        updated[block.lineIndex] = "$indent$blockName {"
        updated.addAll(block.lineIndex + 1, newLines)
        updated.add(block.lineIndex + 1 + newLines.size, "$indent}")
      }
    }
    is ScriptBlock.Multiline -> {
      val insertAt = block.body.lastOrNull { lines[it].isNotBlank() }?.plus(1) ?: block.body.start
      lines.toMutableList().also { it.addAll(insertAt, newLines) }
    }
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun blockIndent(lines: List<String>, block: ScriptBlock): BlockIndent {
    val opening = when (block) {
      is ScriptBlock.Inline -> openingIndent(lines[block.lineIndex])
      is ScriptBlock.Multiline -> openingIndent(lines[block.body.start - 1])
    }
    val child = when (block) {
      is ScriptBlock.Inline -> opening + fallbackStep(lines)
      is ScriptBlock.Multiline -> firstContentIndent(lines, block.body)
        ?: (opening + fallbackStep(lines))
    }
    val step = stepBetween(opening, child, lines)
    return BlockIndent(child, step)
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun firstContentIndent(lines: List<String>, body: IntRange): String? {
    for (index in body) {
      val line = lines[index]
      if (line.isBlank()) continue
      val leading = line.takeWhile { it == ' ' || it == '\t' }
      return leading.ifEmpty { null }
    }
    return null
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun openingIndent(line: String): String = line.takeWhile { it == ' ' || it == '\t' }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun nextIndent(parent: String, child: String): String = child + stepBetween(parent, child)

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun stepBetween(opening: String, child: String, lines: List<String> = emptyList()): String {
    if (child.startsWith(opening) && child.length > opening.length) {
      return child.substring(opening.length)
    }
    return fallbackStep(lines)
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun fallbackStep(lines: List<String> = emptyList()): String {
    var minSpaces: Int? = null
    for (line in lines) {
      if (line.isBlank()) continue
      val leading = line.takeWhile { it == ' ' || it == '\t' }
      if (leading.isEmpty()) continue
      if (leading.contains('\t')) return "\t"
      minSpaces = minOf(minSpaces ?: leading.length, leading.length)
    }
    return if (minSpaces != null) " ".repeat(minSpaces) else "  "
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun linesIn(lines: List<String>, block: ScriptBlock): List<String> = when (block) {
    is ScriptBlock.Inline -> listOf(lines[block.lineIndex])
    is ScriptBlock.Multiline -> lines.slice(block.body)
  }

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

    val range = findBlock(lines, blockName)
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
   * Half-open range of lines inside a `name { }` body (excluding the opening and closing brace
   * lines), or null when the block is missing. [within] limits the search to a parent body.
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun findBlock(
    lines: List<String>,
    blockName: String,
    within: IntRange = lines.indices,
  ): ScriptBlock? {
    val opener = blockOpener(blockName)
    var depth = 0
    var blockStart: Int? = null
    for (index in within) {
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
    val indent = when (block) {
      is ScriptBlock.Inline -> openingIndent(lines[block.lineIndex]) + fallbackStep(lines)
      is ScriptBlock.Multiline -> detectIndent(lines, block.body)
    }
    return insertLinesIntoBlock(lines, block, blockName, listOf(indent + entry))
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
  private fun blockOpener(blockName: String): Regex =
    Regex("^${Regex.escape(blockName)}\\s*\\{.*$")

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun ScriptBlock.bodyRange(): IntRange? = when (this) {
    is ScriptBlock.Multiline -> body
    is ScriptBlock.Inline -> null
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private sealed class ScriptBlock {
    @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
    data class Multiline(val body: IntRange) : ScriptBlock()

    @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
    data class Inline(val lineIndex: Int) : ScriptBlock()
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private sealed class SourceSetTarget {
    @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
    data class Dependencies(val block: ScriptBlock, val blockName: String) : SourceSetTarget()

    @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
    data class NeedsDependencies(
      val container: ScriptBlock,
      val containerName: String,
    ) : SourceSetTarget()
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private enum class SourceSetStyle { ACCESSOR, GETTING, NAMED, QUALIFIED }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private data class BlockIndent(val child: String, val step: String) {
    @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
    fun grandchild(): String = child + step

    @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
    fun greatGrandchild(): String = child + step + step
  }

  companion object {
    const val DEFAULT_CATALOG_EXTENSION = "libs"
    const val DEFAULT_KMP_SOURCE_SET = "commonMain"
    private const val DEPENDENCIES_BLOCK = "dependencies"
    private const val PLUGINS_BLOCK = "plugins"
    private const val KOTLIN_BLOCK = "kotlin"
    private const val SOURCE_SETS_BLOCK = "sourceSets"
    private val GETTING_SOURCE_SET = Regex("""^val\s+\w+\s+by\s+getting\s*\{.*$""")
    private val ACCESSOR_SOURCE_SET = Regex("""^\w+\.dependencies\s*\{.*$""")
    private val NAMED_SOURCE_SET = Regex("""^\w+\s*\{.*$""")
    private val QUALIFIED_SOURCE_SET = Regex("""^sourceSets\.\w+\.dependencies\s*\{.*$""")
  }
}
