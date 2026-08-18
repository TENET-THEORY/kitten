package com.kittenmp.projectgenerator

import org.example.ai.ComprehensionDebt
import java.io.File
import kotlin.jvm.javaClass

private val BINARY_EXTENSIONS = setOf("jar", "js")

class TemplateEntryCopier(
  private val targetDir: File,
  private val packagePath: String,
  private val projectName: String,
  private val basePackage: String
) {

  @ComprehensionDebt
  fun copy(
    relativePath: String,
  ) {
    val outputRelativePath = mapOutputPath(
      relativePath
        .replace("__PACKAGE_PATH__", packagePath)
        .replace('\\', '/')
    )
    val outputFile = File(targetDir, outputRelativePath)
    outputFile.parentFile.mkdirs()
    val resourcePath = "$TEMPLATE_ROOT/$relativePath"
    val inputStream = requireNotNull(javaClass.getResourceAsStream("/$resourcePath")) {
      "missing template resource: $resourcePath"
    }
    inputStream.use { input ->
      if (isBinary(relativePath)) {
        outputFile.outputStream().use { output -> input.copyTo(output) }
      } else {
        val content = input.reader().readText()
          .replace("__PROJECT_NAME__", projectName)
          .replace("__PACKAGE__", basePackage)
          .replace("__PACKAGE_PATH__", packagePath)
        outputFile.writeText(content)
      }
    }
  }

  @ComprehensionDebt
  private fun isBinary(relativePath: String): Boolean {
    val name = relativePath.substringAfterLast('/')
    val extension = name.substringAfterLast('.', missingDelimiterValue = "")
    return extension in BINARY_EXTENSIONS
  }

  @ComprehensionDebt
  private fun mapOutputPath(relativePath: String): String =
    when {
      relativePath == "gitignore" || relativePath.endsWith("/gitignore") ->
        relativePath.replace(Regex("(^|/)gitignore$"), "$1.gitignore")
      relativePath == "gitattributes" || relativePath.endsWith("/gitattributes") ->
        relativePath.replace(Regex("(^|/)gitattributes$"), "$1.gitattributes")
      else -> relativePath
    }
}

