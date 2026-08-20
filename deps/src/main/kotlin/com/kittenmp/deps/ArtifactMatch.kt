package com.kittenmp.deps

import com.kittenmp.ai.ComprehensionDebt

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
internal data class ArtifactMatch(
  val group: String,
  val name: String,
  val version: String,
)
