package com.kittenmp

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.kittenmp.ai.ComprehensionDebt
import com.kittenmp.ai.HumansOnly
import com.kittenmp.deps.DependencyInstaller
import com.kittenmp.projectGenerator.ProjectGenerator
import java.io.File

@HumansOnly
class Kitten : CliktCommand() {

  override fun run()  = Unit
}
class Install : CliktCommand() {

  val artifactId by argument()
  val module by option("--module")

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  override fun run() {
    try {
      echo(DependencyInstaller().use { it.install(artifactId, module) })
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

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  override fun run() {
    try {
      echo(DependencyInstaller().use { it.addToModule(module, artifactId) })
    } catch (e: Exception) {
      throw CliktError(e.message ?: "Failed to add dependency to module")
    }
  }
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
class Uninstall : CliktCommand() {

  val artifactId by argument()

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  override fun run() {
    try {
      echo(DependencyInstaller().use { it.uninstall(artifactId) })
    } catch (e: Exception) {
      throw CliktError(e.message ?: "Failed to uninstall dependency")
    }
  }
}

@HumansOnly
class New : CliktCommand() {
  val name by argument()
  val basePackage by option("--package").default("org.example")
  val path by option().default(".")

  override fun run() {
    ProjectGenerator(
      projectName = name,
      basePackage = basePackage,
      parentDir = File(path)
    ).generateNewProject()
  }
}

fun main(args: Array<String>) = Kitten()
  .subcommands(New(), Install(), Add(), Uninstall())
  .main(args)
