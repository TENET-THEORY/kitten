# Kitten

CLI for scaffolding Kotlin projects and managing Gradle version-catalog dependencies and plugins.

## Install

```bash
brew install tenet-theory/tap/kitten
```

Requires Java 21, which the formula pulls in via `openjdk@21`. See [releases](https://github.com/TENET-THEORY/kitten/releases) for the raw distribution tarball if you'd rather skip Homebrew.

## Build from source

```bash
./gradlew :app:run --args='<command> ...'
```

Or install a local binary:

```bash
./gradlew :app:installDist
./app/build/install/kitten/bin/kitten <command> ...
```

## Commands

### `new` — create a project

Interactive prompts fill in anything you omit. Defaults: name `my-project`, package `org.example`, path `.`, type plain Kotlin.

```bash
kitten new
kitten new my-app --package com.example --path ~/Code --type plain
kitten new api --package com.example.api --type ktor
```

`--type` accepts `plain` / `kotlin` or `ktor`.

### `install` — add a Maven Central library or Gradle plugin to the catalog

Looks up the latest release on Maven Central and writes it into the nearest `gradle/libs.versions.toml` (walking up from the current directory). Pass `--plugin` to treat the argument as a Gradle plugin id (Plugin Portal is used when Central has no marker artifact).

```bash
kitten install clikt
kitten install com.github.ajalt.clikt:clikt
kitten install clikt --module app
kitten install org.jetbrains.kotlin.jvm --plugin
kitten install org.jetbrains.kotlin.jvm --plugin --module app
```

- Prefer `group:artifactId` when several groups publish the same name; otherwise Kitten picks the newest and lists alternatives.
- With `--module`, also adds `implementation(libs…)` to that module’s `build.gradle(.kts)`, or `alias(libs.plugins…)` when `--plugin` is set.
- Kotlin Gradle plugins (`org.jetbrains.kotlin.*`) share `[versions].kotlin` when it already exists, and use `kotlin-*` aliases (`org.jetbrains.kotlin.plugin.compose` → `kotlin-compose`). Other plugins use the last two segments of the id.

### `add` — wire an existing catalog library or plugin into a module

Does not fetch from Maven Central. Resolves the library from the local `libs.versions.toml`, or from the project’s published `ktorLibs` catalog when present. Pass `--plugin` to wire `alias(…plugins…)` instead of `implementation`.

```bash
kitten add app clikt
kitten add :ktor server-core
kitten add app kotlin-jvm --plugin
kitten add lib org.jetbrains.kotlin.jvm --plugin
```

When a plugin is applied in more than one module, Kitten also declares it on the root `build.gradle.kts` with `apply false` (creating that file if needed).

### `uninstall` — remove a library or plugin from the catalog

Removes the matching alias from `gradle/libs.versions.toml`. Does not edit module build scripts. Pass `--plugin` to remove a `[plugins]` entry (a shared `[versions]` entry is left in place if something else still references it).

```bash
kitten uninstall clikt
kitten uninstall com.github.ajalt.clikt:clikt
kitten uninstall kotlin-jvm --plugin
kitten uninstall org.jetbrains.kotlin.jvm --plugin
```

## Roadmap

Not built yet. Next up is richer project init and creating modules, then Phoenix-style generators that cut across the server, website, and a Kotlin Multiplatform app.

### Init

**Ktor library picker.** When you run `kitten new --type ktor`, a checkbox menu lets you pick the Ktor libraries you actually want — auth, websockets, content negotiation, and so on — instead of always getting the same default template.

**Kotlin Multiplatform.** A project type for KMP apps, so `kitten new` can scaffold a multiplatform project the same way it currently does plain Kotlin and Ktor.

**Creating modules.** Add a Gradle module to an existing project — directory, `build.gradle.kts`, and `settings.gradle.kts` include — instead of wiring that by hand.

**Kotlin toolchain support.** `kitten new` and the dependency commands currently assume a Gradle project. Support the plain Kotlin toolchain (`kotlinc`/`kotlin` CLI, no build tool) as a project type alongside Gradle-based ones, so both are first-class.

### Phoenix-like scaffolding

Inspired by Phoenix generators: one command writes the boring layers, consistently, across the stack.

**CRUD generators.** Scaffold a resource end to end — not only Ktor routes and HTML, but the matching pieces in the website and the KMP app (models, clients, screens).

**Feature packs.** One command to drop auth into an application. Same shape for Stripe, RevenueCat, and further integrations later: wire dependencies, config, and the call sites instead of copying a gist.

## Notes

Dependency commands require a `gradle/libs.versions.toml` somewhere above the working directory. Module paths can be `app` or `:app` (nested modules use `:` → `/`, e.g. `:feature:auth` → `feature/auth`).

### Ktor version catalog

Ktor projects (including ones from `kitten new --type ktor`) declare a published catalog in `settings.gradle.kts`:

```kotlin
versionCatalogs {
  create("ktorLibs") { from("io.ktor:ktor-version-catalog:3.5.2") }
}
```

`kitten add` resolves against that catalog when the library isn’t in the local `libs.versions.toml`, and writes `implementation(ktorLibs…)` into the module build script. Local catalog entries always win. You can pass aliases (`server-core`), artifact ids (`ktor-server-core`), or coordinates (`io.ktor:ktor-server-core`). The same lookup applies to plugins with `--plugin` (`kitten add ktor ktor --plugin` → `alias(ktorLibs.plugins.ktor)`).

### AI-generated content

This README was generated by AI. Several parts of the project are also AI-generated; look for `@ComprehensionDebt` on classes and functions that have not yet been thoroughly reviewed by a human.
