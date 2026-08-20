package com.kittenmp.projectGenerator

import com.kittenmp.projectGenerator.ai.ComprehensionDebt
import java.net.JarURLConnection
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.jvm.javaClass

private const val PROTOCOL_FILE = "file"
private const val PROTOCOL_JAR = "jar"

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

  @ComprehensionDebt
  private fun listTemplateEntriesFromFile(url: URL): List<String> {
    val rootPath = Path.of(url.toURI())
    return rootPath.walk()
      .filter { it.isRegularFile() }
      .map { it.relativeTo(rootPath).toString().replace('\\', '/') }
      .sorted()
      .toList()
  }

  @ComprehensionDebt
  private fun listTemplateEntriesFromJar(url: URL): List<String> {
    val connection = url.openConnection() as JarURLConnection
    return connection.jarFile.entries().asSequence()
      .filter { !it.isDirectory && it.name.startsWith("$TEMPLATE_ROOT/") }
      .map { it.name.removePrefix("$TEMPLATE_ROOT/") }
      .sorted()
      .toList()
  }
}

