package com.kittenmp

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.kittenmp.projectGenerator.ProjectGenerator
import java.io.File

class Kitten : CliktCommand() {
  override fun run() = Unit
}

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
  .subcommands(New())
  .main(args)
