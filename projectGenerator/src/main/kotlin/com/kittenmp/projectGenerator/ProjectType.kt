package com.kittenmp.projectGenerator

import com.kittenmp.ai.ComprehensionDebt

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
enum class ProjectType(val templateRoot: String, val label: String) {
  PLAIN(templateRoot = "project-template", label = "Plain Kotlin"),
  KTOR(templateRoot = "project-template-ktor", label = "Ktor"),
  ;

  companion object {
    @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
    fun fromCli(value: String): ProjectType =
      when (value.lowercase()) {
        "plain", "kotlin" -> PLAIN
        "ktor" -> KTOR
        else -> error("Unknown project type: $value. Use plain or ktor.")
      }
  }
}
