package com.kittenmp

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands

class Kitten : CliktCommand() {
  override fun run() = Unit
}

class Example(): CliktCommand() {
  override fun run() {
    println("Hello world")
  }
}

fun main(args: Array<String>) = Kitten()
  .subcommands(Example())
  .main(args)
