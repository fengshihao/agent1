package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent1.android.productivity.logic.business.AskUserFormatting
import com.agent1.android.productivity.ui.viewmodel.AskUserFormState

@Composable
fun AskUserFormPanel(
    form: AskUserFormState,
    enabled: Boolean,
    validationError: String?,
    onTextChange: (questionId: String, value: String) -> Unit,
    onSingleSelect: (questionId: String, option: String) -> Unit,
    onMultiToggle: (questionId: String, option: String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val request = form.request
    val questions = request.questions
    val tabKey = questions.joinToString("|") { it.id }
    var selected by rememberSaveable(tabKey) { mutableIntStateOf(0) }
    val selectedIndex = selected.coerceIn(0, (questions.size - 1).coerceAtLeast(0))
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.42f).dp
    val optionScroll = rememberScrollState()

    LaunchedEffect(validationError, tabKey) {
        if (validationError.isNullOrBlank()) return@LaunchedEffect
        val missing = questions.indexOfFirst { it.required && !form.isAnswered(it) }
        if (missing >= 0) selected = missing
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = request.title?.ifBlank { null } ?: "需要确认",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (questions.size > 1) {
                QuestionTabs(
                    questions = questions,
                    form = form,
                    selectedIndex = selectedIndex,
                    onSelect = { selected = it },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (questions.isNotEmpty()) {
                val question = questions[selectedIndex]
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(optionScroll)
                        .padding(top = 8.dp, bottom = 4.dp),
                ) {
                    AskUserQuestionField(
                        index = selectedIndex + 1,
                        total = questions.size,
                        showIndex = questions.size == 1,
                        question = question,
                        textValue = form.textAnswers[question.id].orEmpty(),
                        singleSelected = form.singleChoice[question.id],
                        multiSelected = form.multiChoice[question.id].orEmpty(),
                        enabled = enabled,
                        onTextChange = { onTextChange(question.id, it) },
                        onSingleSelect = { option ->
                            onSingleSelect(question.id, option)
                            if (selectedIndex < questions.lastIndex) {
                                selected = selectedIndex + 1
                            }
                        },
                        onMultiToggle = { onMultiToggle(question.id, it) },
                    )
                }
            }
            validationError?.let { err ->
                Text(
                    text = err,
                    modifier = Modifier.padding(bottom = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(32.dp)
                    .align(Alignment.End)
                    .clip(CircleShape)
                    .background(
                        if (enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        },
                    )
                    .clickable(enabled = enabled, onClick = onSubmit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "提交回答",
                    modifier = Modifier.size(16.dp),
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
            }
        }
    }
}

@Composable
private fun QuestionTabs(
    questions: List<AskUserFormatting.Question>,
    form: AskUserFormState,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        questions.forEachIndexed { index, question ->
            val selected = index == selectedIndex
            val answered = form.isAnswered(question)
            val shape = RoundedCornerShape(999.dp)
            val background = when {
                selected -> MaterialTheme.colorScheme.primary
                answered -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
            val content = when {
                selected -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            }
            val border = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
            Row(
                modifier = Modifier
                    .widthIn(max = 148.dp)
                    .clip(shape)
                    .background(background)
                    .border(1.dp, border, shape)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1} ${question.prompt}",
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun AskUserFormState.isAnswered(question: AskUserFormatting.Question): Boolean {
    return when (question.type) {
        "single_choice" -> !singleChoice[question.id].isNullOrBlank()
        "multi_choice" -> !multiChoice[question.id].isNullOrEmpty()
        else -> !textAnswers[question.id].isNullOrBlank()
    }
}

@Composable
private fun AskUserQuestionField(
    index: Int,
    total: Int,
    showIndex: Boolean,
    question: AskUserFormatting.Question,
    textValue: String,
    singleSelected: String?,
    multiSelected: Set<String>,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    onSingleSelect: (String) -> Unit,
    onMultiToggle: (String) -> Unit,
) {
    val kind = when (question.type) {
        "single_choice" -> "单选"
        "multi_choice" -> "多选"
        else -> "填写"
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = buildString {
                if (showIndex) append("$index/$total · ")
                append(kind)
                if (question.required) append(" · 必填")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = question.prompt,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        when (question.type) {
            "single_choice" -> {
                question.options.forEach { option ->
                    OptionRow(
                        label = option,
                        selected = singleSelected == option,
                        enabled = enabled,
                        onClick = { onSingleSelect(option) },
                    )
                }
            }
            "multi_choice" -> {
                question.options.forEach { option ->
                    OptionRow(
                        label = option,
                        selected = option in multiSelected,
                        enabled = enabled,
                        onClick = { onMultiToggle(option) },
                    )
                }
            }
            else -> {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = onTextChange,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入回答…") },
                    shape = RoundedCornerShape(10.dp),
                    singleLine = false,
                    maxLines = 3,
                )
            }
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val border = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
