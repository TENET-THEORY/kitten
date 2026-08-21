package com.kittenmp.deps

import com.kittenmp.ai.ComprehensionDebt
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Resolves a Maven Central artifact and writes it into the nearest `gradle/libs.versions.toml`.
 *
 * Holds an HTTP client, so callers must [close] it — `DependencyInstaller().use { ... }`.
 */
@ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
class DependencyInstaller internal constructor(
  private val startDir: File,
  private val mavenCentralClient: MavenCentralClient,
  private val ktorLibsCatalog: KtorLibsCatalog = KtorLibsCatalog(
    catalogTomlLoader = CatalogTomlLoader { group, name, version ->
      runBlocking { mavenCentralClient.downloadArtifact(group, name, version, "toml") }
    },
  ),
) : AutoCloseable {

  constructor(startDir: File = File(".").absoluteFile) : this(startDir, MavenCentralClient())

  private val versionCatalogEditor = VersionCatalogEditor()
  private val buildGradleEditor = BuildGradleEditor()

  /**
   * Adds or updates [term] (`artifactId` or `group:artifactId`) and returns a one-line summary of
   * what changed. When [module] is set, also wires the library into that module's build script.
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun install(term: String, module: String? = null): String {
    val catalogFile = findCatalog(startDir)
      ?: error("Could not find gradle/libs.versions.toml (searched from ${startDir.path})")
    val match = runBlocking { mavenCentralClient.findLatest(term) }
    val alias = sanitizeAlias(match.name)
    val original = catalogFile.readText()
    val result = versionCatalogEditor.upsert(original, alias, match)
    if (result.content != original) {
      catalogFile.writeText(result.content)
    }
    val summary = summarize(result.change, alias, match)
    if (module == null) return summary
    val wired = addToModule(module, term, catalogFile)
    return "$summary\n$wired"
  }

  /**
   * Removes [term] (`artifactId` or `group:artifactId`) from the nearest version catalog and
   * returns a one-line summary.
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun uninstall(term: String): String {
    val catalogFile = findCatalog(startDir)
      ?: error("Could not find gradle/libs.versions.toml (searched from ${startDir.path})")
    val alias = sanitizeAlias(ArtifactQuery.parse(term).name)
    val original = catalogFile.readText()
    val result = versionCatalogEditor.remove(original, alias)
    if (result.change == VersionCatalogEditor.Change.UNCHANGED) {
      error("No library '$alias' in ${catalogFile.path}")
    }
    catalogFile.writeText(result.content)
    return "removed $alias"
  }

  /**
   * Adds `implementation(libs…)` or `implementation(ktorLibs…)` for [term] to [module]'s build
   * script. Local `libs.versions.toml` wins; otherwise the project's published `ktorLibs` catalog
   * is used when present.
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun addToModule(module: String, term: String): String {
    val catalogFile = findCatalog(startDir)
      ?: error("Could not find gradle/libs.versions.toml (searched from ${startDir.path})")
    return addToModule(module, term, catalogFile)
  }

  override fun close() = mavenCentralClient.close()

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun addToModule(module: String, term: String, catalogFile: File): String {
    val projectRoot = catalogFile.parentFile.parentFile
    val resolved = resolveLibrary(term, catalogFile, projectRoot)
      ?: error(missingLibraryMessage(term, catalogFile, projectRoot))
    val buildFile = findModuleBuildFile(projectRoot, module)
      ?: error("Could not find build.gradle(.kts) for module '$module' under ${projectRoot.path}")
    val original = buildFile.readText()
    val result = buildGradleEditor.addImplementation(original, resolved.alias, resolved.extension)
    if (result.content != original) {
      buildFile.writeText(result.content)
    }
    val accessor = buildGradleEditor.aliasToAccessor(resolved.alias)
    val reference = "${resolved.extension}.$accessor"
    return when (result.change) {
      BuildGradleEditor.Change.ADDED -> "added implementation($reference) to ${modulePath(module, buildFile)}"
      BuildGradleEditor.Change.UNCHANGED -> "unchanged implementation($reference) in ${modulePath(module, buildFile)}"
    }
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun resolveLibrary(
    term: String,
    catalogFile: File,
    projectRoot: File,
  ): ResolvedLibrary? {
    val alias = sanitizeAlias(ArtifactQuery.parse(term).name)
    if (versionCatalogEditor.containsLibrary(catalogFile.readText(), alias)) {
      return ResolvedLibrary(alias, BuildGradleEditor.DEFAULT_CATALOG_EXTENSION)
    }
    return resolveFromKtorLibs(term, projectRoot)
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun resolveFromKtorLibs(term: String, projectRoot: File): ResolvedLibrary? {
    val coordinate = ktorLibsCatalog.findCoordinate(projectRoot) ?: return null
    val toml = ktorLibsCatalog.loadToml(coordinate)
    val alias = ktorLibsCatalog.resolveAlias(toml, term) ?: return null
    return ResolvedLibrary(alias, KtorLibsCatalog.EXTENSION)
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun missingLibraryMessage(term: String, catalogFile: File, projectRoot: File): String {
    val alias = sanitizeAlias(ArtifactQuery.parse(term).name)
    val ktorHint = if (ktorLibsCatalog.findCoordinate(projectRoot) != null) {
      " or ${KtorLibsCatalog.EXTENSION}"
    } else {
      ""
    }
    return "No library '$alias' in ${catalogFile.path}$ktorHint"
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private data class ResolvedLibrary(val alias: String, val extension: String)

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun findModuleBuildFile(projectRoot: File, module: String): File? {
    val moduleDir = File(projectRoot, module.trimStart(':').replace(':', '/'))
    val kts = File(moduleDir, "build.gradle.kts")
    if (kts.isFile) return kts
    val groovy = File(moduleDir, "build.gradle")
    return groovy.takeIf { it.isFile }
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun modulePath(module: String, buildFile: File): String =
    "${module.trimStart(':')}/${buildFile.name}"

  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun summarize(change: VersionCatalogEditor.Change, alias: String, match: ArtifactMatch): String {
    val summary = "${change.name.lowercase()} $alias ${match.version} (${match.coordinate})"
    if (match.alternatives.isEmpty()) return summary
    return "$summary\nother artifacts named '${match.name}': ${match.alternatives.joinToString()}"
  }

  /** Walks up from [from] to the first directory containing a Gradle version catalog. */
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun findCatalog(from: File): File? = generateSequence(from) { it.parentFile }
    .map { File(it, "gradle/libs.versions.toml") }
    .firstOrNull { it.isFile }

  /** Converts an artifact name into a version catalog alias (lowercase, `-` separated). */
  @ComprehensionDebt(agent = "claude-code", model = "claude-opus-5")
  private fun sanitizeAlias(artifactId: String): String =
    artifactId
      .lowercase()
      .replace(Regex("[^a-z0-9]+"), "-")
      .trim('-')
}
