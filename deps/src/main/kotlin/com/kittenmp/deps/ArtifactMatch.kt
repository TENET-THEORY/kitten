package com.kittenmp.deps

import com.kittenmp.ai.ComprehensionDebt

/**
 * A resolved Maven Central artifact, plus any other artifacts that matched the search just as
 * well. [alternatives] is non-empty when the search term was ambiguous (several groups publish an
 * artifact under the same name); callers can surface it so the user knows a different artifact was
 * available.
 */
@ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
internal data class ArtifactMatch(
  val group: String,
  val name: String,
  val version: String,
  val alternatives: List<String> = emptyList(),
) {
  val coordinate: String get() = "$group:$name"
}

/** A `name` or `group:name` search term typed by the user. */
@ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
internal data class ArtifactQuery(val group: String?, val name: String) {
  companion object {
    @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
    fun parse(term: String): ArtifactQuery {
      val parts = term.trim().split(':')
      require(parts.size <= 2 && parts.none { it.isBlank() }) {
        "Expected 'artifactId' or 'group:artifactId', got '$term'"
      }
      return if (parts.size == 2) ArtifactQuery(parts[0], parts[1]) else ArtifactQuery(null, parts[0])
    }
  }
}
