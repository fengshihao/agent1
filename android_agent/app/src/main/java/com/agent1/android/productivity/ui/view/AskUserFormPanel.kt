package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agent1.android.productivity.logic.business.AskUserFormatting
import com.agent1.android.productivity.ui.viewmodel.AskUserFormState

@OptIn(ExperimentalLayoutApi::class)
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = request.title?.ifBlank { null } ?: "助手需要您确认",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "请填写后提交；也可在输入框自由回复。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            request.questions.forEach { question ->
                AskUserQuestionField(
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
            validationError?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onSubmit,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("提交回答")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AskUserQuestionField(
    question: AskUserFormatting.Question,
    textValue: String,
    singleSelected: String?,
    multiSelected: Set<String>,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    onSingleSelect: (String) -> Unit,
    onMultiToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val requiredMark = if (question.required) " *" else ""
        Text(
            text = question.prompt + requiredMark,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        when (question.type) {
            "single_choice" -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    question.options.forEach { option ->
                        FilterChip(
                            selected = singleSelected == option,
                            onClick = { if (enabled) onSingleSelect(option) },
                            enabled = enabled,
                            label = { Text(option) },
                        )
                    }
                }
            }
            "multi_choice" -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    question.options.forEach { option ->
                        FilterChip(
                            selected = option in multiSelected,
                            onClick = { if (enabled) onMultiToggle(option) },
                            enabled = enabled,
                            label = { Text(option) },
                        )
                    }
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
