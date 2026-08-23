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
   * With [plugin], [term] is treated as a Gradle plugin id instead of a library.
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun install(term: String, module: String? = null, plugin: Boolean = false): String {
    if (plugin) return installPlugin(term, module)
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
   * returns a one-line summary. With [plugin], removes a Gradle plugin instead of a library.
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun uninstall(term: String, plugin: Boolean = false): String {
    if (plugin) return uninstallPlugin(term)
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
   * is used when present. With [plugin], wires `alias(…plugins…)` instead.
   */
  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun addToModule(module: String, term: String, plugin: Boolean = false): String {
    val catalogFile = findCatalog(startDir)
      ?: error("Could not find gradle/libs.versions.toml (searched from ${startDir.path})")
    return if (plugin) addPluginToModule(module, term, catalogFile)
    else addToModule(module, term, catalogFile)
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun installPlugin(term: String, module: String?): String {
    val catalogFile = findCatalog(startDir)
      ?: error("Could not find gradle/libs.versions.toml (searched from ${startDir.path})")
    val match = runBlocking { mavenCentralClient.findLatestPlugin(term) }
    val original = catalogFile.readText()
    val alias = versionCatalogEditor.choosePluginAlias(original, match)
    val result = versionCatalogEditor.upsertPlugin(original, alias, match)
    if (result.content != original) {
      catalogFile.writeText(result.content)
    }
    val summary = summarizePlugin(result.change, alias, match)
    if (module == null) return summary
    val wired = addPluginToModule(module, term, catalogFile)
    return "$summary\n$wired"
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun uninstallPlugin(term: String): String {
    val catalogFile = findCatalog(startDir)
      ?: error("Could not find gradle/libs.versions.toml (searched from ${startDir.path})")
    val original = catalogFile.readText()
    val alias = versionCatalogEditor.resolvePluginAlias(original, term)
      ?: error("No plugin '$term' in ${catalogFile.path}")
    val result = versionCatalogEditor.removePlugin(original, alias)
    if (result.change == VersionCatalogEditor.Change.UNCHANGED) {
      error("No plugin '$alias' in ${catalogFile.path}")
    }
    catalogFile.writeText(result.content)
    return "removed plugin $alias"
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun addPluginToModule(module: String, term: String, catalogFile: File): String {
    val projectRoot = catalogFile.parentFile.parentFile
    val resolved = resolvePlugin(term, catalogFile, projectRoot)
      ?: error(missingPluginMessage(term, catalogFile, projectRoot))
    val buildFile = findModuleBuildFile(projectRoot, module)
      ?: error("Could not find build.gradle(.kts) for module '$module' under ${projectRoot.path}")
    val original = buildFile.readText()
    val result = buildGradleEditor.addPlugin(original, resolved.alias, resolved.extension)
    if (result.content != original) {
      buildFile.writeText(result.content)
    }
    val accessor = buildGradleEditor.aliasToAccessor(resolved.alias)
    val entry = buildGradleEditor.pluginEntry(resolved.extension, accessor, applyFalse = false)
    val moduleSummary = when (result.change) {
      BuildGradleEditor.Change.ADDED -> "added $entry to ${modulePath(module, buildFile)}"
      BuildGradleEditor.Change.UNCHANGED -> "unchanged $entry in ${modulePath(module, buildFile)}"
    }
    val rootSummary = maybeDeclareOnRoot(projectRoot, resolved)
    return if (rootSummary == null) moduleSummary else "$moduleSummary\n$rootSummary"
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun maybeDeclareOnRoot(projectRoot: File, resolved: ResolvedPlugin): String? {
    val users = moduleBuildFiles(projectRoot).count { file ->
      buildGradleEditor.hasPlugin(file.readText(), resolved.alias, resolved.extension)
    }
    if (users <= 1) return null
    val rootFile = rootBuildFile(projectRoot) ?: File(projectRoot, "build.gradle.kts")
    val original = if (rootFile.isFile) rootFile.readText() else ""
    val result = buildGradleEditor.addPlugin(
      original,
      resolved.alias,
      resolved.extension,
      applyFalse = true,
    )
    if (result.content != original) {
      rootFile.writeText(result.content)
    }
    val accessor = buildGradleEditor.aliasToAccessor(resolved.alias)
    val entry = buildGradleEditor.pluginEntry(resolved.extension, accessor, applyFalse = true)
    return when (result.change) {
      BuildGradleEditor.Change.ADDED -> "added $entry to ${rootFile.name}"
      BuildGradleEditor.Change.UNCHANGED -> null
    }
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun resolvePlugin(
    term: String,
    catalogFile: File,
    projectRoot: File,
  ): ResolvedPlugin? {
    val catalog = catalogFile.readText()
    versionCatalogEditor.resolvePluginAlias(catalog, term)?.let { alias ->
      return ResolvedPlugin(alias, BuildGradleEditor.DEFAULT_CATALOG_EXTENSION)
    }
    return resolvePluginFromKtorLibs(term, projectRoot)
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun resolvePluginFromKtorLibs(term: String, projectRoot: File): ResolvedPlugin? {
    val coordinate = ktorLibsCatalog.findCoordinate(projectRoot) ?: return null
    val toml = ktorLibsCatalog.loadToml(coordinate)
    val alias = versionCatalogEditor.resolvePluginAlias(toml, term) ?: return null
    return ResolvedPlugin(alias, KtorLibsCatalog.EXTENSION)
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun missingPluginMessage(term: String, catalogFile: File, projectRoot: File): String {
    val ktorHint = if (ktorLibsCatalog.findCoordinate(projectRoot) != null) {
      " or ${KtorLibsCatalog.EXTENSION}"
    } else {
      ""
    }
    return "No plugin '$term' in ${catalogFile.path}$ktorHint"
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private data class ResolvedPlugin(val alias: String, val extension: String)

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

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun findModuleBuildFile(projectRoot: File, module: String): File? {
    val moduleDir = File(projectRoot, module.trimStart(':').replace(':', '/'))
    return buildFileIn(moduleDir)
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun rootBuildFile(projectRoot: File): File? = buildFileIn(projectRoot)

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun buildFileIn(dir: File): File? {
    val kts = File(dir, "build.gradle.kts")
    if (kts.isFile) return kts
    val groovy = File(dir, "build.gradle")
    return groovy.takeIf { it.isFile }
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun moduleBuildFiles(projectRoot: File): List<File> {
    val rootBuild = rootBuildFile(projectRoot)
    return projectRoot.walkTopDown()
      .onEnter { it.name !in SKIP_WALK_DIRS }
      .filter { it.isFile && it.name in BUILD_FILE_NAMES }
      .filter { it != rootBuild }
      .toList()
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun modulePath(module: String, buildFile: File): String =
    "${module.trimStart(':')}/${buildFile.name}"

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.6")
  private fun summarizePlugin(
    change: VersionCatalogEditor.Change,
    alias: String,
    match: PluginMatch,
  ): String {
    val summary = "${change.name.lowercase()} plugin $alias ${match.version} (${match.id})"
    if (match.alternatives.isEmpty()) return summary
    return "$summary\nother plugins: ${match.alternatives.joinToString()}"
  }

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

  override fun close() = mavenCentralClient.close()

  private companion object {
    val SKIP_WALK_DIRS = setOf("build", ".gradle", "gradle", "src", ".git", "out")
    val BUILD_FILE_NAMES = setOf("build.gradle.kts", "build.gradle")
  }
}
