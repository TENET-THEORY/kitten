package com.kittenmp.projectGenerator

import com.kittenmp.ai.ComprehensionDebt
import java.io.File
import java.net.JarURLConnection
import java.net.URL

private const val PROTOCOL_FILE = "file"
private const val PROTOCOL_JAR = "jar"

/**
 * Lists the template entries, which live either on disk (running from a build directory) or inside
 * the packaged jar.
 */
internal class TemplateLister {

  fun listEntries(): List<String> {
    val url = requireNotNull(javaClass.getResource("/$TEMPLATE_ROOT")) {
      "missing $TEMPLATE_ROOT resource"
    }
    return when (url.protocol) {
      PROTOCOL_FILE -> listTemplateEntriesFromFile(url)
      PROTOCOL_JAR -> listTemplateEntriesFromJar(url)
      else -> error("Unsupported resource protocol: ${url.protocol}")
    }
  }

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun listTemplateEntriesFromFile(url: URL): List<String> {
    val root = File(url.toURI())
    return root.walkTopDown()
      .filter { it.isFile }
      .map { it.relativeTo(root).invariantSeparatorsPath }
      .sorted()
      .toList()
  }

  /**
   * Reads the jar directly rather than through the URL cache, so the [java.util.jar.JarFile] this
   * opens is ours to close.
   */
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun listTemplateEntriesFromJar(url: URL): List<String> {
    val connection = (url.openConnection() as JarURLConnection).apply { useCaches = false }
    val prefix = "$TEMPLATE_ROOT/"
    return connection.jarFile.use { jar ->
      jar.entries().asSequence()
        .filter { !it.isDirectory && it.name.startsWith(prefix) }
        .map { it.name.removePrefix(prefix) }
        .sorted()
        .toList()
    }
  }
}
