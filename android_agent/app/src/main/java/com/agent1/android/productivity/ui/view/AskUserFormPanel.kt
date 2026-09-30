package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.42f).dp
    val scroll = rememberScrollState()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
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
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scroll)
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                request.questions.forEachIndexed { index, question ->
                    AskUserQuestionField(
                        index = index + 1,
                        total = request.questions.size,
                        question = question,
                        textValue = form.textAnswers[question.id].orEmpty(),
                        singleSelected = form.singleChoice[question.id],
                        multiSelected = form.multiChoice[question.id].orEmpty(),
                        enabled = enabled,
                        onTextChange = { onTextChange(question.id, it) },
                        onSingleSelect = { onSingleSelect(question.id, it) },
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
            Button(
                onClick = onSubmit,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("提交回答")
            }
        }
    }
}

@Composable
private fun AskUserQuestionField(
    index: Int,
    total: Int,
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
            text = "$index/$total · $kind${if (question.required) " · 必填" else ""}",
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
