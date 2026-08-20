package com.kittenmp.deps

import com.kittenmp.ai.ComprehensionDebt
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@ComprehensionDebt(agent = "junie", model = "gemini-3-flash-preview")
internal class MavenCentralClient(
  private val client: HttpClient = createDefaultHttpClient(),
) {
  @ComprehensionDebt(agent = "junie", model = "gemini-3-flash-preview")
  fun findLatest(artifactId: String): ArtifactMatch = runBlocking {
    val url = buildSearchUrl(artifactId)
    val body = fetch(url)
    val docs = parseDocs(body)
    val match = pickBestMatch(artifactId, docs)
      ?: error("No Maven Central artifact found for '$artifactId'")
    match
  }

  @ComprehensionDebt(agent = "junie", model = "gemini-3-flash-preview")
  private fun buildSearchUrl(artifactId: String): String {
    val query = URLEncoder.encode("a:$artifactId", StandardCharsets.UTF_8)
    return "https://search.maven.org/solrsearch/select?q=$query&rows=20&wt=json"
  }

  @ComprehensionDebt(agent = "junie", model = "gemini-3-flash-preview")
  private suspend fun fetch(url: String): String {
    val response = client.get(url)
    if (!response.status.isSuccess()) {
      error("Maven Central search failed with HTTP ${response.status.value}")
    }
    return response.bodyAsText()
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun pickBestMatch(artifactId: String, docs: List<SearchDoc>): ArtifactMatch? {
    val exact = docs.filter { it.artifactId.equals(artifactId, ignoreCase = true) }
    val candidates = exact.ifEmpty { docs }
    val best = candidates.maxWithOrNull(
      compareBy<SearchDoc> { it.versionCount }
        .thenBy { it.timestamp }
    ) ?: return null
    val version = best.latestVersion ?: return null
    return ArtifactMatch(
      group = best.groupId,
      name = best.artifactId,
      version = version,
    )
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun parseDocs(json: String): List<SearchDoc> {
    val docsBlock = docsArray(json) ?: return emptyList()
    return DOC_REGEX.findAll(docsBlock).mapNotNull { match ->
      val doc = match.value
      val groupId = stringField(doc, "g") ?: return@mapNotNull null
      val artifactId = stringField(doc, "a") ?: return@mapNotNull null
      val latestVersion = stringField(doc, "latestVersion")
      SearchDoc(
        groupId = groupId,
        artifactId = artifactId,
        latestVersion = latestVersion,
        versionCount = intField(doc, "versionCount") ?: 0,
        timestamp = longField(doc, "timestamp") ?: 0L,
      )
    }.toList()
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun docsArray(json: String): String? {
    val marker = "\"docs\""
    val startKey = json.indexOf(marker)
    if (startKey < 0) return null
    val arrayStart = json.indexOf('[', startKey)
    if (arrayStart < 0) return null
    var depth = 0
    for (i in arrayStart until json.length) {
      when (json[i]) {
        '[' -> depth++
        ']' -> {
          depth--
          if (depth == 0) return json.substring(arrayStart, i + 1)
        }
      }
    }
    return null
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun stringField(doc: String, name: String): String? =
    Regex(""""$name"\s*:\s*"([^"]*)"""").find(doc)?.groupValues?.get(1)

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun intField(doc: String, name: String): Int? =
    Regex(""""$name"\s*:\s*(\d+)""").find(doc)?.groupValues?.get(1)?.toIntOrNull()

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun longField(doc: String, name: String): Long? =
    Regex(""""$name"\s*:\s*(\d+)""").find(doc)?.groupValues?.get(1)?.toLongOrNull()

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private data class SearchDoc(
    val groupId: String,
    val artifactId: String,
    val latestVersion: String?,
    val versionCount: Int,
    val timestamp: Long,
  )

  companion object {
    private val DOC_REGEX = Regex("""\{[^{}]*("g"\s*:\s*"[^"]*")[^{}]*\}""")

    @ComprehensionDebt(agent = "junie", model = "gemini-3-flash-preview")
    private fun createDefaultHttpClient(): HttpClient = HttpClient(CIO) {
      install(HttpTimeout) {
        connectTimeoutMillis = 30000
        requestTimeoutMillis = 60000
      }
      install(HttpRequestRetry) {
        maxRetries = 3
        retryIf { _, response ->
          !response.status.isSuccess()
        }
        retryOnExceptionIf { _, cause ->
          cause is Exception
        }
        delayMillis { retry ->
          retry * 1000L
        }
      }
    }
  }
}
