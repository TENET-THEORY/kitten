package com.kittenmp.projectGenerator

import com.kittenmp.ai.ComprehensionDebt
import java.io.File

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
class ProjectGenerator(
  private val parentDir: File,
  private val projectName: String,
  private val basePackage: String,
  private val projectType: ProjectType = ProjectType.PLAIN,
) {

  private val packagePath = basePackage.replace('.', '/')
  private val targetDir = File(parentDir, projectName)
  private val templateRoot = projectType.templateRoot

  private val templateLister = TemplateLister(templateRoot)
  private val templateEntryCopier = TemplateEntryCopier(
    templateRoot = templateRoot,
    targetDir = targetDir,
    packagePath = packagePath,
    projectName = projectName,
    basePackage = basePackage,
  )

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  fun generateNewProject() {
    if (targetDir.exists()) {
      error("Directory already exists: ${targetDir.absolutePath}")
    }
    targetDir.mkdirs()
    templateLister.listEntries().forEach { relativePath ->
      templateEntryCopier.copy(relativePath)
    }
    makeGradlewExecutable()
  }

  @ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
  private fun makeGradlewExecutable() {
    File(targetDir, "gradlew").setExecutable(true)
  }
}
