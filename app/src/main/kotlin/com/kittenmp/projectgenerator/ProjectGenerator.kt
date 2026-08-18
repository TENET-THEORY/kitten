package com.kittenmp.projectgenerator

import java.io.File

const val TEMPLATE_ROOT = "project-template"

class ProjectGenerator(
  private val parentDir: File,
  private val projectName: String,
  private val basePackage: String
) {

  private val packagePath = basePackage.replace('.', '/')
  private val targetDir = File(parentDir, projectName)

  private val templateLister = TemplateLister()
  private val templateEntryCopier = TemplateEntryCopier(
    targetDir = targetDir,
    packagePath = packagePath,
    projectName = projectName,
    basePackage = basePackage
  )

  fun generateNewProject() {
    val targetDir = File(parentDir, projectName)
    if (targetDir.exists()) {
      error("Directory already exists: ${targetDir.absolutePath}")
    }
    targetDir.mkdirs()
    templateLister.listEntries().forEach { relativePath ->
      templateEntryCopier.copy(relativePath)
    }
  }
}

