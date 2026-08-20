package com.kittenmp.projectGenerator

import com.kittenmp.ai.ComprehensionDebt
import java.io.File

/** Extensions copied byte for byte instead of having template tokens substituted. */
private val VERBATIM_EXTENSIONS = setOf("jar", "js")

/**
 * Template files that ship without a leading dot and are restored to their dotted name on copy.
 *
 * Gradle's resource-processing default excludes drop `.gitignore` and `.gitattributes`, so they
 * cannot be stored under their real names.
 */
private val DOTFILE_NAMES = setOf("gitignore", "gitattributes")

internal class TemplateEntryCopier(
  private val targetDir: File,
  private val packagePath: String,
  private val projectName: String,
  private val basePackage: String
) {

  /** Copies one template entry into [targetDir], substituting tokens in text files. */
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun copy(relativePath: String) {
    val outputFile = File(targetDir, outputPathFor(relativePath))
    outputFile.parentFile?.mkdirs()

    val resourcePath = "$TEMPLATE_ROOT/$relativePath"
    val inputStream = requireNotNull(javaClass.getResourceAsStream("/$resourcePath")) {
      "missing template resource: $resourcePath"
    }
    inputStream.use { input ->
      if (isVerbatim(relativePath)) {
        outputFile.outputStream().use { output -> input.copyTo(output) }
      } else {
        outputFile.writeText(substituteTokens(input.reader().readText()))
      }
    }
  }

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun substituteTokens(content: String): String = content
    .replace("__PROJECT_NAME__", projectName)
    .replace("__PACKAGE__", basePackage)
    .replace("__PACKAGE_PATH__", packagePath)

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun isVerbatim(relativePath: String): Boolean =
    fileNameOf(relativePath).substringAfterLast('.', missingDelimiterValue = "") in VERBATIM_EXTENSIONS

  /** Expands the package-path token and restores dotted names such as `gitignore` -> `.gitignore`. */
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun outputPathFor(relativePath: String): String {
    val path = relativePath.replace('\\', '/').replace("__PACKAGE_PATH__", packagePath)
    val name = fileNameOf(path)
    return if (name in DOTFILE_NAMES) path.dropLast(name.length) + ".$name" else path
  }

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun fileNameOf(path: String): String = path.substringAfterLast('/')
}
