package com.kittenmp.website

import com.kittenmp.ai.ComprehensionDebt
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.em
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.id
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.pre
import kotlinx.html.script
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.title

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
fun HTML.homePage() {
  head {
    meta(charset = "utf-8")
    meta(name = "viewport", content = "width=device-width, initial-scale=1")
    meta(
      name = "description",
      content =
        "Kitten is a CLI for scaffolding Kotlin projects and managing Gradle version-catalog dependencies.",
    )
    meta(name = "theme-color", content = "#f88909")
    title("Kitten — a small CLI with sharp claws")
    link(rel = "preconnect", href = "https://fonts.googleapis.com")
    link(rel = "preconnect", href = "https://fonts.gstatic.com") {
      attributes["crossorigin"] = "anonymous"
    }
    link(
      rel = "stylesheet",
      href =
        "https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500&family=Syne:wght@600;700;800&family=Work+Sans:wght@400;500;600&display=swap",
    )
    link(rel = "stylesheet", href = "/assets/site.css")
  }
  body {
    hero()
    main {
      missingTool()
      getStarted()
      commands()
      notes()
    }
    siteFooter()
    script(src = "/assets/site.js") {}
  }
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private fun FlowContent.hero() {
  header(classes = "hero") {
    div(classes = "hero-plane") {
      attributes["aria-hidden"] = "true"
      div(classes = "terminal") {
        div(classes = "terminal-chrome") {
          span(classes = "dot")
          span(classes = "dot")
          span(classes = "dot")
          span(classes = "terminal-title") { +"kitten" }
        }
        pre(classes = "terminal-body") {
          codeLines(
            listOf(
              "$ kitten new api --type ktor",
              "$ kitten install clikt --module app",
              "$ kitten add :ktor server-core",
              "$ kitten uninstall clikt",
            ),
          )
        }
      }
    }
    div(classes = "hero-copy") {
      p(classes = "badge") { +"small CLI, sharp claws" }
      p(classes = "brand") {
        +"Kitten"
        span(classes = "brand-dot") { +"." }
      }
      h1 { +"Scaffold Kotlin. Wrangle catalogs. Skip the yak shaving." }
      p(classes = "lede") {
        +"A CLI for new Kotlin projects and Gradle version-catalog dependencies — pounce on Maven Central, wire up modules, and speak fluent ktorLibs."
      }
      div(classes = "cta-row") {
        a(href = "#get-started", classes = "cta") { +"Let it loose" }
        a(href = "#commands", classes = "cta cta-quiet") { +"Poke at the commands" }
      }
    }
  }
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private fun FlowContent.missingTool() {
  section(classes = "band band-pitch") {
    id = "why"
    span(classes = "eyebrow") { +"01 — the gap" }
    h2 {
      +"The missing CLI tool for "
      em { +"Kotlin" }
    }
    p(classes = "section-lede") {
      +"Other ecosystems have rich CLI tools for starting projects and managing dependencies — "
      span(classes = "inline-code") { +"npm" }
      +" and "
      span(classes = "inline-code") { +"pip" }
      +" among them. Kotlin has had you hand-editing a version catalog and copying a build script from the last project. Kitten fills that gap."
    }
    div(classes = "ecosystems") {
      ecosystemCard("npm", "node", "npm init · npm install")
      ecosystemCard("pip", "python", "pip install")
      ecosystemCard("cargo", "rust", "cargo new · cargo add")
      ecosystemCard("kitten", "kotlin", "kitten new · kitten install")
    }
  }
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private fun FlowContent.ecosystemCard(tool: String, ecosystem: String, usage: String) {
  val highlight = if (tool == "kitten") " is-kitten" else ""
  div(classes = "ecosystem$highlight") {
    p(classes = "ecosystem-tool") { +tool }
    p(classes = "ecosystem-lang") { +ecosystem }
    p(classes = "ecosystem-usage") { +usage }
  }
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private fun FlowContent.getStarted() {
  section(classes = "band") {
    id = "get-started"
    span(classes = "eyebrow") { +"02 — feed it" }
    h2 {
      +"Get it "
      em { +"running" }
    }
    p {
      +"From the Kitten repo, invoke the app through Gradle, or install a local binary and call "
      span(classes = "inline-code") { +"kitten" }
      +" directly."
    }
    pre(classes = "snippet") {
      +"./gradlew :app:run --args='<command> ...'\n\n"
      +"./gradlew :app:installDist\n"
      +"./app/build/install/kitten/bin/kitten <command> ..."
    }
  }
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private fun FlowContent.commands() {
  section(classes = "band") {
    id = "commands"
    span(classes = "eyebrow") { +"03 — the litter" }
    h2 {
      +"Four "
      em { +"commands" }
      +", no fuss"
    }
    p(classes = "section-lede") {
      +"Scaffolding and catalog work, covered. Dependency commands walk up from the working directory to find "
      span(classes = "inline-code") { +"gradle/libs.versions.toml" }
      +"."
    }
    commandBlock(
      name = "new",
      summary = "Create a project",
      detail =
        "Interactive prompts fill in anything you omit. Defaults: name my-project, package org.example, path ., type plain Kotlin. --type accepts plain / kotlin or ktor.",
      examples =
        listOf(
          "kitten new",
          "kitten new my-app --package com.example --path ~/Code --type plain",
          "kitten new api --package com.example.api --type ktor",
        ),
    )
    commandBlock(
      name = "install",
      summary = "Add a Maven Central library to the catalog",
      detail =
        "Looks up the latest release and writes it into the nearest libs.versions.toml. Prefer group:artifactId when several groups publish the same name. With --module, also adds implementation(libs…) to that module’s build script.",
      examples =
        listOf(
          "kitten install clikt",
          "kitten install com.github.ajalt.clikt:clikt",
          "kitten install clikt --module app",
        ),
    )
    commandBlock(
      name = "add",
      summary = "Wire an existing catalog library into a module",
      detail =
        "Does not fetch from Maven Central. Resolves from the local libs.versions.toml, or from the project’s published ktorLibs catalog when present.",
      examples =
        listOf(
          "kitten add app clikt",
          "kitten add :ktor server-core",
        ),
    )
    commandBlock(
      name = "uninstall",
      summary = "Remove a library from the catalog",
      detail =
        "Removes the matching alias from libs.versions.toml. Does not edit module build scripts.",
      examples =
        listOf(
          "kitten uninstall clikt",
          "kitten uninstall com.github.ajalt.clikt:clikt",
        ),
    )
  }
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private fun FlowContent.notes() {
  section(classes = "band band-last") {
    id = "notes"
    span(classes = "eyebrow") { +"04 — house rules" }
    h2 {
      +"Small print, "
      em { +"few surprises" }
    }
    h3 { +"Modules & catalogs" }
    p {
      +"Dependency commands require a "
      span(classes = "inline-code") { +"gradle/libs.versions.toml" }
      +" somewhere above the working directory. Module paths can be "
      span(classes = "inline-code") { +"app" }
      +" or "
      span(classes = "inline-code") { +":app" }
      +" (nested modules use "
      span(classes = "inline-code") { +":" }
      +" → "
      span(classes = "inline-code") { +"/" }
      +", e.g. "
      span(classes = "inline-code") { +":feature:auth" }
      +" → "
      span(classes = "inline-code") { +"feature/auth" }
      +")."
    }
    h3 { +"Ktor version catalog" }
    p {
      +"Ktor projects (including ones from "
      span(classes = "inline-code") { +"kitten new --type ktor" }
      +") declare a published catalog in "
      span(classes = "inline-code") { +"settings.gradle.kts" }
      +":"
    }
    pre(classes = "snippet") {
      +"versionCatalogs {\n"
      +"  create(\"ktorLibs\") { from(\"io.ktor:ktor-version-catalog:3.5.2\") }\n"
      +"}"
    }
    p {
      span(classes = "inline-code") { +"kitten add" }
      +" resolves against that catalog when the library isn’t in the local "
      span(classes = "inline-code") { +"libs.versions.toml" }
      +", and writes "
      span(classes = "inline-code") { +"implementation(ktorLibs…)" }
      +". Local catalog entries always win. Pass aliases ("
      span(classes = "inline-code") { +"server-core" }
      +"), artifact ids ("
      span(classes = "inline-code") { +"ktor-server-core" }
      +"), or coordinates ("
      span(classes = "inline-code") { +"io.ktor:ktor-server-core" }
      +")."
    }
    h3 { +"AI-generated content" }
    p {
      +"This site and much of the Kitten docs were generated by AI. Several parts of the project are also AI-generated; look for "
      span(classes = "inline-code") { +"@ComprehensionDebt" }
      +" on classes and functions that have not yet been thoroughly reviewed by a human."
    }
  }
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private fun FlowContent.siteFooter() {
  div(classes = "footer") {
    p {
      +"Kitten · scaffolding and version catalogs for Kotlin · no yaks were shaved"
    }
  }
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private fun FlowContent.commandBlock(
  name: String,
  summary: String,
  detail: String,
  examples: List<String>,
) {
  div(classes = "command") {
    h3 {
      span(classes = "cmd-name") { +name }
      +" — "
      +summary
    }
    p { +detail }
    pre(classes = "snippet") { +examples.joinToString("\n") }
  }
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private fun FlowContent.codeLines(lines: List<String>) {
  lines.forEachIndexed { index, line ->
    span(classes = "line") {
      attributes["style"] = "--i: $index"
      +line
    }
    +"\n"
  }
  span(classes = "cursor") { +"█" }
}
