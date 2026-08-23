package com.kittenmp.deps

import com.kittenmp.ai.ComprehensionDebt
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
class MavenCentralClientTest {

  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `prefers the exact artifact name`() = runTest {
    val client = clientReturning(
      doc(group = "com.example.fuzzy", artifact = "clikt-extensions", latestVersion = "9.9.9", timestamp = 3),
      doc(group = "com.github.ajalt.clikt", artifact = "clikt", latestVersion = "5.1.0", timestamp = 2),
    )
    assertEquals("com.github.ajalt.clikt:clikt", client.use { it.findLatest("clikt") }.coordinate)
  }

  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `picks the most recently published group and reports the rest`() = runTest {
    val client = clientReturning(
      doc(group = "com.github.ajalt", artifact = "clikt", latestVersion = "2.8.0", timestamp = 1),
      doc(group = "com.github.ajalt.clikt", artifact = "clikt", latestVersion = "5.1.0", timestamp = 2),
    )
    val match = client.use { it.findLatest("clikt") }
    assertEquals("5.1.0", match.version)
    assertEquals("com.github.ajalt.clikt", match.group)
    assertEquals(listOf("com.github.ajalt:clikt"), match.alternatives)
  }

  /** Ranking must not depend on `versionCount`: the current Central endpoint always reports 0. */
  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `ranks correctly when versionCount is absent`() = runTest {
    val client = clientReturning(
      doc(group = "org.stale", artifact = "widget", latestVersion = "1.0.0", timestamp = 1),
      doc(group = "org.fresh", artifact = "widget", latestVersion = "4.0.0", timestamp = 5),
    )
    assertEquals("4.0.0", client.use { it.findLatest("widget") }.version)
  }

  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `a group-qualified term pins that group`() = runTest {
    val client = clientReturning(
      doc(group = "com.upstart", artifact = "widget", latestVersion = "2.0.0-alpha", timestamp = 9),
      doc(group = "org.jetbrains.kotlinx", artifact = "widget", latestVersion = "1.11.0", timestamp = 1),
    )
    val match = client.use { it.findLatest("org.jetbrains.kotlinx:widget") }
    assertEquals("1.11.0", match.version)
    assertEquals("org.jetbrains.kotlinx", match.group)
    assertTrue(match.alternatives.isEmpty())
  }

  /** A pinned group must never silently widen back out to some other publisher's artifact. */
  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `a group-qualified term that matches nothing is an error`() = runTest {
    val client = clientReturning(
      doc(group = "com.upstart", artifact = "widget", latestVersion = "2.0.0", timestamp = 9),
    )
    assertFailsWith<IllegalStateException> { client.use { it.findLatest("org.nobody:widget") } }
  }

  /** The query is grouped by artifact name so Central returns an authoritative `latestVersion`. */
  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `the search is always a bare artifact-name query`() = runTest {
    val engine = MockEngine {
      respond(
        """{"response":{"docs":[${doc("org.example", "widget", "1.0.0", timestamp = 1)}]}}""",
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }
    MavenCentralClient(client = HttpClient(engine)).use { it.findLatest("org.example:widget") }
    assertEquals("a:widget", engine.requestHistory.single().url.parameters["q"])
  }

  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `docs without a version are skipped`() = runTest {
    val client = clientReturning(
      """{"g":"org.example","a":"widget","timestamp":9,"versionCount":0}""",
      doc(group = "org.other", artifact = "widget", latestVersion = "1.0.0", timestamp = 1),
    )
    assertEquals("org.other:widget", client.use { it.findLatest("widget") }.coordinate)
  }

  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `unknown fields in the response are ignored`() = runTest {
    val json = """{"response":{"numFound":1,"docs":[
      {"id":"g:a","g":"org.example","a":"widget","latestVersion":"1.0.0","p":"jar","ec":[],"timestamp":1}
    ]}}"""
    val client = MavenCentralClient(client = HttpClient(jsonEngine(json)))
    assertEquals("1.0.0", client.use { it.findLatest("widget") }.version)
  }

  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `no results is an error`() = runTest {
    val client = clientReturning()
    val failure = assertFailsWith<IllegalStateException> { client.use { it.findLatest("nope") } }
    assertTrue("nope" in failure.message.orEmpty())
  }

  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `a non-retryable failure surfaces the status code`() = runTest {
    val engine = MockEngine { respondError(HttpStatusCode.BadRequest) }
    val client = MavenCentralClient(client = HttpClient(engine))
    val failure = assertFailsWith<IllegalStateException> { client.use { it.findLatest("widget") } }
    assertTrue("400" in failure.message.orEmpty())
  }

  @Test
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  fun `a malformed term is rejected before any request`() = runTest {
    val engine = MockEngine { error("should not be called") }
    val client = MavenCentralClient(client = HttpClient(engine))
    assertFailsWith<IllegalArgumentException> { client.use { it.findLatest("a:b:c") } }
    assertEquals(0, engine.requestHistory.size)
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun `findLatestPlugin uses the marker artifact name`() = runTest {
    val engine = MockEngine {
      respond(
        """{"response":{"docs":[${doc("org.jetbrains.kotlin.jvm", "org.jetbrains.kotlin.jvm.gradle.plugin", "2.4.10", timestamp = 1)}]}}""",
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }
    val match = MavenCentralClient(client = HttpClient(engine)).use {
      it.findLatestPlugin("org.jetbrains.kotlin.jvm")
    }
    assertEquals("org.jetbrains.kotlin.jvm", match.id)
    assertEquals("2.4.10", match.version)
    assertEquals("a:org.jetbrains.kotlin.jvm.gradle.plugin", engine.requestHistory.single().url.parameters["q"])
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun `findLatestPlugin falls back to the plugin portal metadata`() = runTest {
    val engine = MockEngine { request ->
      if (request.url.encodedPath.endsWith("maven-metadata.xml")) {
        respond(
          """<metadata><versioning><latest>0.26.0</latest><release>0.26.0</release></versioning></metadata>""",
          HttpStatusCode.OK,
          headersOf(HttpHeaders.ContentType, "application/xml"),
        )
      } else {
        respond(
          """{"response":{"docs":[]}}""",
          HttpStatusCode.OK,
          headersOf(HttpHeaders.ContentType, "application/json"),
        )
      }
    }
    val match = MavenCentralClient(client = HttpClient(engine)).use {
      it.findLatestPlugin("com.ncorti.ktfmt.gradle")
    }
    assertEquals("com.ncorti.ktfmt.gradle", match.id)
    assertEquals("0.26.0", match.version)
  }

  @Test
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  fun `findLatestPlugin with no results is an error`() = runTest {
    val engine = MockEngine { request ->
      if (request.url.encodedPath.endsWith("maven-metadata.xml")) {
        respondError(HttpStatusCode.NotFound)
      } else {
        respond(
          """{"response":{"docs":[]}}""",
          HttpStatusCode.OK,
          headersOf(HttpHeaders.ContentType, "application/json"),
        )
      }
    }
    val failure = assertFailsWith<IllegalStateException> {
      MavenCentralClient(client = HttpClient(engine)).use { it.findLatestPlugin("org.example.missing") }
    }
    assertTrue("org.example.missing" in failure.message.orEmpty())
  }

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun doc(group: String, artifact: String, latestVersion: String, timestamp: Long): String =
    """{"g":"$group","a":"$artifact","latestVersion":"$latestVersion","timestamp":$timestamp,"versionCount":0}"""

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun clientReturning(vararg docs: String): MavenCentralClient =
    MavenCentralClient(client = HttpClient(jsonEngine("""{"response":{"docs":[${docs.joinToString()}]}}""")))

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun jsonEngine(json: String) = MockEngine {
    respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
  }
}
