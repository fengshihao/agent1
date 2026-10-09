package com.agent1.android.productivity.ui.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agent1.android.productivity.ui.viewmodel.ChatLine

/** 聊天气泡族：消息气泡、工具/思考折叠块、气泡外壳与待处理气泡。 */
@Composable
internal fun MessageBubble(
    line: ChatLine,
    workspacePath: String,
    markdown: Boolean,
    reasoningStateKey: String,
    onPickFiles: () -> Unit,
    pickFilesEnabled: Boolean,
    onOpenInApp: ((String) -> Unit)? = null,
) {
    val bubbles = chatBubbleColors()
    val isUser = line.role == "user" && !line.isTool
    val isTool = line.isTool
    when {
        isUser -> {
            BubbleShell(
                alignEnd = true,
                wide = false,
                background = bubbles.userBackground,
                borderColor = Color.Transparent,
            ) {
                SelectionContainer {
                    Text(
                        line.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = bubbles.userContent,
                    )
                }
            }
        }
        isTool -> {
            CollapsibleToolCallBubble(
                stateKey = reasoningStateKey,
                toolName = line.toolName ?: "工具",
                argsPreview = line.toolArgsPreview.orEmpty(),
                finished = line.toolFinished,
                isError = line.toolIsError,
                resultSummary = line.content,
                progressLines = emptyList(),
            )
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (line.reasoning.isNotBlank()) {
                    CollapsibleReasoningBlock(
                        reasoning = line.reasoning,
                        stateKey = reasoningStateKey,
                        inProgress = line.stableKey == "assistant-streaming" &&
                            line.content.isBlank(),
                    )
                }
                if (line.content.isNotBlank() || line.requestUserPickFiles) {
                    BubbleShell(
                        alignEnd = false,
                        wide = true,
                        background = bubbles.assistantBackground,
                        borderColor = bubbles.assistantBorder,
                    ) {
                        SelectionContainer {
                            Column {
                                if (line.content.isNotBlank()) {
                                    WorkspaceChatBody(
                                        content = line.content,
                                        workspaceAbsolutePath = workspacePath,
                                        markdown = markdown,
                                        workspaceFilePaths = line.workspaceFilePaths,
                                        onOpenInApp = onOpenInApp,
                                    )
                                }
                                if (line.requestUserPickFiles) {
                                    IconButton(
                                        onClick = onPickFiles,
                                        enabled = pickFilesEnabled,
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .size(32.dp),
                                    ) {
                                        Icon(
                                            AgentIcons.AttachFile,
                                            contentDescription = "选择文件",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AssistantPendingBubble(label: String) {
    val bubbles = chatBubbleColors()
    BubbleShell(
        alignEnd = false,
        wide = true,
        background = bubbles.assistantBackground,
        borderColor = bubbles.assistantBorder,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun CollapsibleToolCallBubble(
    stateKey: String,
    toolName: String,
    argsPreview: String,
    finished: Boolean,
    isError: Boolean,
    resultSummary: String,
    progressLines: List<String>,
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    val bubbles = chatBubbleColors()
    val failed = finished && isError
    CollapsibleMetaBubble(
        expanded = expanded,
        onToggle = { expanded = !expanded },
        title = toolName,
        borderColor = if (failed) MaterialTheme.colorScheme.error else bubbles.systemBorder,
        showSpinner = !finished,
        expandContentDescription = "展开工具结果",
        collapseContentDescription = "收起工具结果",
    ) {
        if (argsPreview.isNotBlank()) {
            Text(
                argsPreview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!finished && progressLines.isNotEmpty()) {
            progressLines.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (resultSummary.isNotBlank()) {
            Text(
                resultSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else if (finished && !isError) {
            Text(
                "（无输出）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CollapsibleReasoningBlock(
    reasoning: String,
    stateKey: String,
    inProgress: Boolean = false,
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    val trimmed = reasoning.trim().trimEnd('▌', ' ').trim()
    if (trimmed.isEmpty()) return
    val bubbles = chatBubbleColors()
    CollapsibleMetaBubble(
        expanded = expanded,
        onToggle = { expanded = !expanded },
        title = "思考过程",
        borderColor = bubbles.systemBorder,
        showSpinner = inProgress,
        expandContentDescription = "展开思考过程",
        collapseContentDescription = "收起思考过程",
    ) {
        Text(
            text = trimmed,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CollapsibleMetaBubble(
    expanded: Boolean,
    onToggle: () -> Unit,
    title: String,
    borderColor: Color,
    showSpinner: Boolean,
    expandContentDescription: String,
    collapseContentDescription: String,
    expandedContent: @Composable ColumnScope.() -> Unit,
) {
    val bubbles = chatBubbleColors()
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
    BubbleShell(
        alignEnd = false,
        wide = true,
        background = bubbles.systemBackground,
        borderColor = borderColor,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) collapseContentDescription else expandContentDescription,
                    modifier = Modifier.size(20.dp),
                    tint = iconTint,
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showSpinner) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = iconTint,
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                SelectionContainer {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        content = expandedContent,
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleShell(
    alignEnd: Boolean,
    wide: Boolean,
    background: Color,
    borderColor: Color,
    borderWidth: Dp = 1.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (wide) 340.dp else 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .then(
                    if (borderColor.alpha > 0f) {
                        Modifier.border(borderWidth, borderColor, RoundedCornerShape(16.dp))
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            content()
        }
    }
}
