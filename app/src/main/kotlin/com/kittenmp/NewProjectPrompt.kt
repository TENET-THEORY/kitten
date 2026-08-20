package com.kittenmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.NonInteractivePolicy
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.runMosaicBlocking
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Text
import com.kittenmp.ai.ComprehensionDebt
import kotlinx.coroutines.awaitCancellation

internal const val DEFAULT_PROJECT_NAME = "my-project"
internal const val DEFAULT_BASE_PACKAGE = "org.example"
internal const val DEFAULT_PROJECT_PATH = "."

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
internal data class NewProjectAnswers(
  val name: String,
  val basePackage: String,
  val path: String,
)

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
internal fun resolveNewProjectAnswers(
  name: String?,
  basePackage: String?,
  path: String?,
): NewProjectAnswers {
  if (name != null && basePackage != null && path != null) {
    return NewProjectAnswers(name, basePackage, path)
  }

  val prompts = buildList {
    if (name == null) add(PromptField("Project name", DEFAULT_PROJECT_NAME))
    if (basePackage == null) add(PromptField("Package", DEFAULT_BASE_PACKAGE))
    if (path == null) add(PromptField("Path", DEFAULT_PROJECT_PATH))
  }

  val answers = promptFields(prompts)
  var index = 0
  fun nextOr(default: String, provided: String?): String =
    provided ?: answers.getOrElse(index++) { default }.ifBlank { default }

  return NewProjectAnswers(
    name = nextOr(DEFAULT_PROJECT_NAME, name),
    basePackage = nextOr(DEFAULT_BASE_PACKAGE, basePackage),
    path = nextOr(DEFAULT_PROJECT_PATH, path),
  )
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private data class PromptField(val label: String, val default: String)

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private fun promptFields(fields: List<PromptField>): List<String> {
  if (fields.isEmpty()) return emptyList()

  val answers = MutableList(fields.size) { "" }
  var completed = false

  val ran = runMosaicBlocking(onNonInteractive = NonInteractivePolicy.Return) {
    PromptForm(
      fields = fields,
      onComplete = { values ->
        values.forEachIndexed { i, value -> answers[i] = value }
        completed = true
      },
    )
  }

  if (!ran || !completed) {
    return fields.map { it.default }
  }
  return answers
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
@Composable
private fun PromptForm(
  fields: List<PromptField>,
  onComplete: (List<String>) -> Unit,
) {
  var step by remember { mutableIntStateOf(0) }
  var input by remember { mutableStateOf("") }
  var done by remember { mutableStateOf(false) }
  val values = remember { MutableList(fields.size) { "" } }

  Column(
    modifier = Modifier.onKeyEvent { event ->
      if (done) return@onKeyEvent false
      when {
        event.key == "Enter" -> {
          values[step] = input
          input = ""
          if (step + 1 >= fields.size) {
            onComplete(values.toList())
            done = true
          } else {
            step += 1
          }
          true
        }
        event.key == "Backspace" -> {
          if (input.isNotEmpty()) {
            input = input.dropLast(1)
          }
          true
        }
        isPrintableKey(event.key) && !event.ctrl && !event.alt -> {
          input += event.key
          true
        }
        else -> false
      }
    },
  ) {
    fields.forEachIndexed { index, completedField ->
      when {
        index < step || done -> Text(completedLine(completedField, values[index]))
        index == step -> Text("${completedField.label} [${completedField.default}]: $input█")
        else -> Text("${completedField.label} [${completedField.default}]:")
      }
    }
  }

  if (!done) {
    LaunchedEffect(Unit) {
      awaitCancellation()
    }
  }
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private fun completedLine(field: PromptField, value: String): String {
  val resolved = value.ifBlank { field.default }
  return "${field.label} [${field.default}]: $resolved"
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private fun isPrintableKey(key: String): Boolean =
  key.length == 1 && !key[0].isISOControl()
