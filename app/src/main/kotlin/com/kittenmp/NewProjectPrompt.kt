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
import com.kittenmp.projectGenerator.ProjectType
import kotlinx.coroutines.awaitCancellation

internal const val DEFAULT_PROJECT_NAME = "my-project"
internal const val DEFAULT_BASE_PACKAGE = "org.example"
internal const val DEFAULT_PROJECT_PATH = "."
internal val DEFAULT_PROJECT_TYPE = ProjectType.PLAIN

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
internal data class NewProjectAnswers(
  val name: String,
  val basePackage: String,
  val path: String,
  val projectType: ProjectType,
)

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private sealed interface PromptStep {
  data class Text(val label: String, val default: String) : PromptStep
  data class Choice(val label: String, val options: List<ProjectType>) : PromptStep
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private sealed interface StepAnswer {
  data class Text(val value: String) : StepAnswer
  data class Choice(val value: ProjectType) : StepAnswer
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
internal fun resolveNewProjectAnswers(
  name: String?,
  basePackage: String?,
  path: String?,
  projectType: ProjectType?,
): NewProjectAnswers {
  if (name != null && basePackage != null && path != null && projectType != null) {
    return NewProjectAnswers(name, basePackage, path, projectType)
  }

  val steps = buildList {
    if (name == null) add(PromptStep.Text("Project name", DEFAULT_PROJECT_NAME))
    if (basePackage == null) add(PromptStep.Text("Package", DEFAULT_BASE_PACKAGE))
    if (path == null) add(PromptStep.Text("Path", DEFAULT_PROJECT_PATH))
    if (projectType == null) add(PromptStep.Choice("Project type", ProjectType.entries))
  }

  val answers = promptSteps(steps)
  var index = 0

  fun nextText(default: String, provided: String?): String {
    if (provided != null) return provided
    val answer = answers.getOrNull(index++) as? StepAnswer.Text
    return answer?.value?.ifBlank { default } ?: default
  }

  fun nextType(provided: ProjectType?): ProjectType {
    if (provided != null) return provided
    val answer = answers.getOrNull(index++) as? StepAnswer.Choice
    return answer?.value ?: DEFAULT_PROJECT_TYPE
  }

  return NewProjectAnswers(
    name = nextText(DEFAULT_PROJECT_NAME, name),
    basePackage = nextText(DEFAULT_BASE_PACKAGE, basePackage),
    path = nextText(DEFAULT_PROJECT_PATH, path),
    projectType = nextType(projectType),
  )
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private fun promptSteps(steps: List<PromptStep>): List<StepAnswer> {
  if (steps.isEmpty()) return emptyList()

  val answers = MutableList<StepAnswer?>(steps.size) { null }
  var completed = false

  val ran = runMosaicBlocking(onNonInteractive = NonInteractivePolicy.Return) {
    PromptForm(
      steps = steps,
      onComplete = { values ->
        values.forEachIndexed { i, value -> answers[i] = value }
        completed = true
      },
    )
  }

  if (!ran || !completed) {
    return steps.map { step ->
      when (step) {
        is PromptStep.Text -> StepAnswer.Text(step.default)
        is PromptStep.Choice -> StepAnswer.Choice(DEFAULT_PROJECT_TYPE)
      }
    }
  }
  return answers.map { requireNotNull(it) }
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
@Composable
private fun PromptForm(
  steps: List<PromptStep>,
  onComplete: (List<StepAnswer>) -> Unit,
) {
  var step by remember { mutableIntStateOf(0) }
  var input by remember { mutableStateOf("") }
  var choiceIndex by remember { mutableIntStateOf(0) }
  var done by remember { mutableStateOf(false) }
  val values = remember { MutableList<StepAnswer?>(steps.size) { null } }

  fun finishStep(answer: StepAnswer) {
    values[step] = answer
    input = ""
    choiceIndex = 0
    if (step + 1 >= steps.size) {
      onComplete(values.map { requireNotNull(it) })
      done = true
    } else {
      step += 1
      val next = steps[step]
      if (next is PromptStep.Choice) {
        choiceIndex = next.options.indexOf(DEFAULT_PROJECT_TYPE).coerceAtLeast(0)
      }
    }
  }

  Column(
    modifier = Modifier.onKeyEvent { event ->
      if (done) return@onKeyEvent false
      when (val current = steps[step]) {
        is PromptStep.Text -> handleTextKey(event.key, event.ctrl, event.alt, input,
          onInput = { input = it },
          onSubmit = { finishStep(StepAnswer.Text(input)) },
        )
        is PromptStep.Choice -> handleChoiceKey(event.key, choiceIndex, current.options.size,
          onMove = { choiceIndex = it },
          onSubmit = { finishStep(StepAnswer.Choice(current.options[choiceIndex])) },
        )
      }
    },
  ) {
    steps.forEachIndexed { index, promptStep ->
      when {
        index < step || done -> Text(completedLine(promptStep, values[index]))
        index == step -> activeLine(promptStep, input, choiceIndex)
        else -> pendingLine(promptStep)
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
private fun handleTextKey(
  key: String,
  ctrl: Boolean,
  alt: Boolean,
  input: String,
  onInput: (String) -> Unit,
  onSubmit: () -> Unit,
): Boolean = when {
  key == "Enter" -> {
    onSubmit()
    true
  }
  key == "Backspace" -> {
    if (input.isNotEmpty()) onInput(input.dropLast(1))
    true
  }
  isPrintableKey(key) && !ctrl && !alt -> {
    onInput(input + key)
    true
  }
  else -> false
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private fun handleChoiceKey(
  key: String,
  choiceIndex: Int,
  optionCount: Int,
  onMove: (Int) -> Unit,
  onSubmit: () -> Unit,
): Boolean = when (key) {
  "ArrowUp", "k" -> {
    onMove((choiceIndex - 1 + optionCount) % optionCount)
    true
  }
  "ArrowDown", "j" -> {
    onMove((choiceIndex + 1) % optionCount)
    true
  }
  "Enter" -> {
    onSubmit()
    true
  }
  else -> false
}

@Composable
@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private fun activeLine(step: PromptStep, input: String, choiceIndex: Int) {
  when (step) {
    is PromptStep.Text -> Text("${step.label} [${step.default}]: $input█")
    is PromptStep.Choice -> {
      Text("${step.label}:")
      step.options.forEachIndexed { index, type ->
        val marker = if (index == choiceIndex) ">" else " "
        Text("$marker ${type.label}")
      }
    }
  }
}

@Composable
@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private fun pendingLine(step: PromptStep) {
  when (step) {
    is PromptStep.Text -> Text("${step.label} [${step.default}]:")
    is PromptStep.Choice -> Text("${step.label}:")
  }
}

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private fun completedLine(step: PromptStep, answer: StepAnswer?): String =
  when (step) {
    is PromptStep.Text -> {
      val value = (answer as? StepAnswer.Text)?.value.orEmpty().ifBlank { step.default }
      "${step.label} [${step.default}]: $value"
    }
    is PromptStep.Choice -> {
      val value = (answer as? StepAnswer.Choice)?.value ?: DEFAULT_PROJECT_TYPE
      "${step.label}: ${value.label}"
    }
  }

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
private fun isPrintableKey(key: String): Boolean =
  key.length == 1 && !key[0].isISOControl()
