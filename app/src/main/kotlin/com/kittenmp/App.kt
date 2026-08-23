package com.kittenmp

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.kittenmp.ai.ComprehensionDebt
import com.kittenmp.ai.HumansOnly
import com.kittenmp.deps.DependencyInstaller
import com.kittenmp.projectGenerator.ProjectGenerator
import com.kittenmp.projectGenerator.ProjectType
import java.io.File

@HumansOnly
class Kitten : CliktCommand() {

  override fun run()  = Unit
}
class Install : CliktCommand() {

  val artifactId by argument()
  val module by option("--module")
  val plugin by option("--plugin", help = "Treat the argument as a Gradle plugin id").flag()

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  override fun run() {
    try {
      echo(DependencyInstaller().use { it.install(artifactId, module, plugin) })
    } catch (e: Exception) {
      throw CliktError(e.message ?: "Failed to install dependency")
    }
    return
    if (currentContext.invokedSubcommand == null) {
      throw PrintHelpMessage(currentContext)
    }
  }
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
class Add : CliktCommand() {

  val module by argument()
  val artifactId by argument()
  val plugin by option("--plugin", help = "Wire a Gradle plugin instead of a library").flag()

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  override fun run() {
    try {
      echo(DependencyInstaller().use { it.addToModule(module, artifactId, plugin) })
    } catch (e: Exception) {
      throw CliktError(e.message ?: "Failed to add dependency to module")
    }
  }
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
class Uninstall : CliktCommand() {

  val artifactId by argument()
  val plugin by option("--plugin", help = "Remove a Gradle plugin instead of a library").flag()

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  override fun run() {
    try {
      echo(DependencyInstaller().use { it.uninstall(artifactId, plugin) })
    } catch (e: Exception) {
      throw CliktError(e.message ?: "Failed to uninstall dependency")
    }
  }
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
class New : CliktCommand() {
  val name by argument().optional()
  val basePackage by option("--package")
  val path by option()
  val type by option("--type", help = "Project type: plain or ktor")
    .convert { ProjectType.fromCli(it) }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  override fun run() {
    val answers = resolveNewProjectAnswers(name, basePackage, path, type)
    ProjectGenerator(
      projectName = answers.name,
      basePackage = answers.basePackage,
      parentDir = File(answers.path),
      projectType = answers.projectType,
    ).generateNewProject()
  }
}

fun main(args: Array<String>) = Kitten()
  .subcommands(New(), Install(), Add(), Uninstall())
  .main(args)
