package com.kittenmp.deps

import com.kittenmp.ai.ComprehensionDebt
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Looks up the newest published version of an artifact using the Maven Central search API.
 *
 * Callers own the client and must [close] it (directly or via `use`) so the underlying connection
 * pool is released.
 */
@ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
internal class MavenCentralClient(
  private val searchUrl: String = CENTRAL_SEARCH_URL,
  private val client: HttpClient = defaultHttpClient(),
) : AutoCloseable {

  /**
   * Resolves [term] (`artifactId` or `group:artifactId`) to the newest release on Maven Central.
   *
   * Throws if nothing matches. When several groups publish the same artifact name the newest one
   * wins and the rest are reported in [ArtifactMatch.alternatives]; naming the group pins the
   * choice.
   */
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  suspend fun findLatest(term: String): ArtifactMatch {
    val query = ArtifactQuery.parse(term)
    val ranked = rank(query, search(query.name))
    val best = ranked.firstOrNull() ?: error("No Maven Central release found for '$term'")
    return ArtifactMatch(
      group = best.group,
      name = best.artifact,
      version = best.latestVersion,
      alternatives = ranked.map { "${it.group}:${it.artifact}" }
        .distinct()
        .filterNot { it == "${best.group}:${best.artifact}" },
    )
  }

  /**
   * Runs an artifact-name query.
   *
   * Name-only queries are deliberate: they make Central group its results and hand back the
   * authoritative `latestVersion` per group. A `g:... AND a:...` query instead returns one
   * unsorted, [SEARCH_ROWS]-capped entry per release, from which the newest cannot be picked
   * reliably.
   */
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private suspend fun search(artifactName: String): List<SearchDoc> {
    val response = client.get(searchUrl) {
      url.parameters.append("q", "a:$artifactName")
      url.parameters.append("rows", "$SEARCH_ROWS")
      url.parameters.append("wt", "json")
    }
    if (!response.status.isSuccess()) {
      error("Maven Central search failed with HTTP ${response.status.value}")
    }
    return JSON.decodeFromString<SearchResponse>(response.bodyAsText()).response.docs
  }

  override fun close() = client.close()

  /**
   * Narrows [docs] to what the user asked for and orders it best-first, most recently published.
   *
   * A term that names a group never widens back out to other groups; a bare artifact name falls
   * back to Central's fuzzy hits only when nothing matches it exactly.
   *
   * The `versionCount` field the old ranking sorted on primarily is reported as `0` by the current
   * Central endpoint, so it is only a tie-breaker here.
   */
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun rank(query: ArtifactQuery, docs: List<SearchDoc>): List<SearchDoc> {
    val named = docs.filter { it.artifact.equals(query.name, ignoreCase = true) }
    val candidates = when {
      query.group != null -> named.filter { it.group.equals(query.group, ignoreCase = true) }
      else -> named.ifEmpty { docs }
    }
    return candidates
      .filter { it.latestVersion.isNotEmpty() }
      .sortedWith(
        compareByDescending<SearchDoc> { it.timestamp }
          .thenByDescending { it.versionCount }
          .thenBy { it.group },
      )
  }

  /** A single grouped `docs` entry: one artifact coordinate and its newest published version. */
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  @Serializable
  private data class SearchDoc(
    @SerialName("g") val group: String = "",
    @SerialName("a") val artifact: String = "",
    val latestVersion: String = "",
    val versionCount: Int = 0,
    val timestamp: Long = 0L,
  )

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  @Serializable
  private data class SearchResponse(val response: ResponseBody = ResponseBody())

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  @Serializable
  private data class ResponseBody(val docs: List<SearchDoc> = emptyList())

  companion object {
    /**
     * `search.maven.org` serves a stale index (it still reports versions that Central has long
     * since superseded), so queries go to the current Central endpoint instead.
     */
    const val CENTRAL_SEARCH_URL = "https://central.sonatype.com/solrsearch/select"

    private const val SEARCH_ROWS = 20
    private const val CONNECT_TIMEOUT_MILLIS = 30_000L
    private const val REQUEST_TIMEOUT_MILLIS = 60_000L
    private const val MAX_RETRIES = 3

    private val JSON = Json { ignoreUnknownKeys = true }

    /** Retries only what is worth retrying: throttling, server faults, and transport errors. */
    @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
    private fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
      install(HttpTimeout) {
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
      }
      install(HttpRequestRetry) {
        maxRetries = MAX_RETRIES
        retryIf { _, response ->
          response.status == HttpStatusCode.TooManyRequests || response.status.value >= 500
        }
        retryOnExceptionIf { _, cause -> cause is IOException }
        exponentialDelay()
      }
    }
  }
}
