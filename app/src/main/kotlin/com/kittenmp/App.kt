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

  @ComprehensionDebt
  override fun run() {
    try {
      echo(DependencyInstaller().use { it.install(artifactId) })
    } catch (e: Exception) {
      throw CliktError(e.message ?: "Failed to install dependency")
    }
    return
    if (currentContext.invokedSubcommand == null) {
      throw PrintHelpMessage(currentContext)
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
  .subcommands(New(), Install())
  .main(args)
