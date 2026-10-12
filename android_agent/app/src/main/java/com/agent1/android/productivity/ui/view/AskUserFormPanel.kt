package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.graphics.Color
import com.agent1.android.productivity.logic.business.AskUserFormState
import com.agent1.android.productivity.logic.business.AskUserFormatting
import com.agent1.android.productivity.logic.business.AskUserQuestionUi

@Composable
fun AskUserFormPanel(
    form: AskUserFormState,
    enabled: Boolean,
    validationError: String?,
    onTextChange: (questionId: String, value: String) -> Unit,
    onSingleSelect: (questionId: String, option: String) -> Unit,
    onMultiToggle: (questionId: String, option: String) -> Unit,
    onCustomAnswerChange: (questionId: String, value: String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val request = form.request
    val questions = request.questions
    val tabKey = questions.joinToString("|") { it.id }
    var selected by rememberSaveable(tabKey) { mutableIntStateOf(0) }
    val selectedIndex = selected.coerceIn(0, (questions.size - 1).coerceAtLeast(0))
    val cs = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(14.dp)
    val cardColor = if (isSystemInDarkTheme()) Color(0xFF3A342C) else Color(0xFFFFFCF8)
    val optionScroll = rememberScrollState()

    LaunchedEffect(selectedIndex) {
        optionScroll.scrollTo(0)
    }

    LaunchedEffect(validationError, tabKey) {
        if (validationError.isNullOrBlank()) return@LaunchedEffect
        val missing = questions.indexOfFirst { it.required && !form.isAnswered(it) }
        if (missing >= 0) selected = missing
    }

    val onLastQuestion = questions.isNotEmpty() && selectedIndex == questions.lastIndex
    val currentQuestion = questions.getOrNull(selectedIndex)
    val currentReady = currentQuestion?.let { form.canAdvance(it) } ?: false
    val missingRequiredIndex = form.firstMissingRequiredIndex()
    val primaryEnabled = when {
        !enabled -> false
        onLastQuestion -> missingRequiredIndex == null
        else -> currentReady
    }

    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, cs.primary.copy(alpha = 0.45f), cardShape),
            shape = cardShape,
            color = cardColor,
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (questions.isNotEmpty() && currentQuestion != null) {
                    if (questions.size > 1) {
                        QuestionStepSelector(
                            questions = questions,
                            selectedIndex = selectedIndex,
                            form = form,
                            onSelect = { selected = it },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = cs.outlineVariant.copy(alpha = 0.6f),
                        )
                    }
                    QuestionHeader(
                        prompt = currentQuestion.prompt,
                        required = currentQuestion.required,
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(optionScroll)
                                .padding(bottom = 8.dp),
                        ) {
                            AskUserQuestionField(
                                question = currentQuestion,
                                textValue = form.textAnswers[currentQuestion.id].orEmpty(),
                                singleSelected = form.singleChoice[currentQuestion.id],
                                multiSelected = form.multiChoice[currentQuestion.id].orEmpty(),
                                customValue = form.customAnswers[currentQuestion.id].orEmpty(),
                                enabled = enabled,
                                onTextChange = { onTextChange(currentQuestion.id, it) },
                                onSingleSelect = { onSingleSelect(currentQuestion.id, it) },
                                onMultiToggle = { onMultiToggle(currentQuestion.id, it) },
                                onCustomAnswerChange = { onCustomAnswerChange(currentQuestion.id, it) },
                            )
                        }
                        if (onLastQuestion && enabled && missingRequiredIndex != null) {
                            val missingIndex = missingRequiredIndex
                            Text(
                                text = "还有必填未完成 · 第 ${missingIndex + 1} 题",
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(end = 96.dp, bottom = 8.dp)
                                    .clickable { selected = missingIndex },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = cs.error,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            validationError?.let { err ->
                                Text(
                                    text = err,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(end = 96.dp, bottom = 8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.error,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Button(
                            onClick = {
                                if (onLastQuestion) {
                                    onSubmit()
                                } else {
                                    selected = (selectedIndex + 1).coerceAtMost(questions.lastIndex)
                                }
                            },
                            enabled = primaryEnabled,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .height(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 2.dp,
                                pressedElevation = 4.dp,
                                disabledElevation = 0.dp,
                            ),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = cs.surfaceContainerHigh,
                                disabledContentColor = cs.onSurface.copy(alpha = 0.38f),
                            ),
                        ) {
                            Text(
                                text = if (onLastQuestion) "提交回答" else "下一题",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionStepSelector(
    questions: List<AskUserFormatting.Question>,
    selectedIndex: Int,
    form: AskUserFormState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "题目",
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        questions.forEachIndexed { index, question ->
            val selected = index == selectedIndex
            val answered = form.isAnswered(question)
            val missingRequired = question.required && !answered
            val background = when {
                selected -> cs.primary
                answered -> cs.primaryContainer
                else -> cs.surfaceContainerLow
            }
            val borderColor = when {
                selected -> cs.primary
                missingRequired -> cs.error
                answered -> cs.primary.copy(alpha = 0.55f)
                else -> cs.outlineVariant
            }
            val borderWidth = if (selected || missingRequired) 2.dp else 1.dp
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(background)
                    .border(borderWidth, borderColor, CircleShape)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (answered && !selected) "✓" else "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        selected -> cs.onPrimary
                        answered -> cs.onPrimaryContainer
                        missingRequired -> cs.error
                        else -> cs.onSurfaceVariant
                    },
                )
            }
        }
        }
    }
}

@Composable
private fun QuestionHeader(
    prompt: String,
    required: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    val badgeShape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = prompt,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface,
            lineHeight = MaterialTheme.typography.titleSmall.lineHeight,
        )
        if (required) {
            Text(
                text = "必填",
                modifier = Modifier
                    .clip(badgeShape)
                    .background(cs.errorContainer.copy(alpha = 0.55f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = cs.error,
            )
        }
    }
}

@Composable
private fun AskUserQuestionField(
    question: AskUserFormatting.Question,
    textValue: String,
    singleSelected: String?,
    multiSelected: Set<String>,
    customValue: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onTextChange: (String) -> Unit,
    onSingleSelect: (String) -> Unit,
    onMultiToggle: (String) -> Unit,
    onCustomAnswerChange: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ratingScale = AskUserQuestionUi.ratingScale(question)
    val hasCustom = customValue.isNotBlank()
    val structuredEnabled = enabled && !hasCustom
    Column(modifier = modifier) {
        if (ratingScale != null && !hasCustom) {
            RatingSliderField(
                scale = ratingScale,
                value = textValue,
                enabled = structuredEnabled,
                onValue = onTextChange,
            )
        } else if (ratingScale == null) when (question.type) {
            "single_choice" -> {
                question.options.forEach { option ->
                    SingleChoiceRow(
                        label = option,
                        selected = singleSelected == option,
                        enabled = structuredEnabled,
                        onSelect = { onSingleSelect(option) },
                    )
                }
            }
            "multi_choice" -> {
                question.options.forEach { option ->
                    MultiChoiceRow(
                        label = option,
                        checked = option in multiSelected,
                        enabled = structuredEnabled,
                        onToggle = { onMultiToggle(option) },
                    )
                }
            }
            else -> {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = onTextChange,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入你的回答") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(10.dp),
                    singleLine = false,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = cs.surface,
                        unfocusedContainerColor = cs.surface,
                    ),
                )
            }
        }
        if (shouldShowCustomAnswerField(question)) {
            Text(
                text = "或",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
            AskUserCustomAnswerField(
                value = customValue,
                enabled = enabled,
                onValueChange = onCustomAnswerChange,
            )
        }
    }
}

private fun shouldShowCustomAnswerField(question: AskUserFormatting.Question): Boolean {
    return question.type != "text"
}

@Composable
private fun AskUserCustomAnswerField(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        label = { Text("自行填写") },
        placeholder = { Text("不选上面选项时，在此输入") },
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(8.dp),
        singleLine = false,
        maxLines = 3,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = cs.surface,
            unfocusedContainerColor = cs.surface,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleChoiceRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .clickable(enabled = enabled, onClick = onSelect),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                enabled = enabled,
                modifier = Modifier.size(20.dp),
                colors = RadioButtonDefaults.colors(selectedColor = cs.primary),
            )
            Text(
                text = label,
                modifier = Modifier.padding(start = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiChoiceRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .clickable(enabled = enabled, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
                enabled = enabled,
                modifier = Modifier.size(20.dp),
                colors = CheckboxDefaults.colors(checkedColor = cs.primary),
            )
            Text(
                text = label,
                modifier = Modifier.padding(start = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
            )
        }
    }
}

@Composable
private fun RatingSliderField(
    scale: AskUserQuestionUi.RatingScale,
    value: String,
    enabled: Boolean,
    onValue: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val min = scale.min
    val max = scale.max
    val selected = value.toIntOrNull()?.takeIf { it in min..max }
    val span = max - min
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (selected != null) "当前 $selected 分" else "拖动选择分值",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
        Slider(
            value = (selected ?: min).toFloat(),
            onValueChange = { raw ->
                val snapped = raw.toInt().coerceIn(min, max)
                onValue(snapped.toString())
            },
            enabled = enabled,
            valueRange = min.toFloat()..max.toFloat(),
            steps = (span - 1).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = if (selected != null) cs.primary else Color.Transparent,
                activeTrackColor = if (selected != null) cs.primary else cs.surfaceVariant,
                inactiveTrackColor = cs.outlineVariant,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(min.toString(), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Text(max.toString(), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}
