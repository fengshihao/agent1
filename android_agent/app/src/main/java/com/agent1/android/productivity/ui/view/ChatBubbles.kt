package com.agent1.android.productivity.ui.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agent1.android.productivity.logic.business.UserFileRequestMarkers
import com.agent1.android.productivity.ui.viewmodel.ChatLine

/** 聊天气泡族：消息气泡、工具/思考折叠块与气泡外壳。 */
@Composable
internal fun MessageBubble(
    line: ChatLine,
    workspacePath: String,
    markdown: Boolean,
    reasoningStateKey: String,
    onOpenAttachments: (UserFileRequestMarkers.Hint?) -> Unit,
    attachmentsActionEnabled: Boolean,
    onOpenInApp: ((String) -> Unit)? = null,
    /** 系统提示卡上「设置页」链接的跳转；null 时不展示链接。 */
    onOpenSettings: (() -> Unit)? = null,
    skipTrailingImagePreviews: Boolean = false,
    /** 长消息拆段时只渲染该片段；null 表示整段 [line.content]。 */
    contentSlice: String? = null,
    showReasoning: Boolean = true,
    showPickFiles: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val bodyText = contentSlice ?: line.content
    val bubbles = chatBubbleColors()
    val isUser = line.role == "user" && !line.isTool && !line.isSystemNotice
    val isTool = line.isTool
    when {
        line.isSystemNotice -> {
            SystemNoticeBubble(
                content = line.content,
                onOpenSettings = onOpenSettings,
            )
        }
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
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showReasoning && line.reasoning.isNotBlank()) {
                    CollapsibleReasoningBlock(
                        reasoning = line.reasoning,
                        stateKey = reasoningStateKey,
                        inProgress = line.stableKey == "assistant-streaming" &&
                            line.content.isBlank(),
                    )
                }
                if (bodyText.isNotBlank() || (showPickFiles && line.requestUserPickFiles)) {
                    BubbleShell(
                        alignEnd = false,
                        wide = true,
                        background = bubbles.assistantBackground,
                        borderColor = bubbles.assistantBorder,
                    ) {
                        SelectionContainer {
                            Column {
                                if (bodyText.isNotBlank()) {
                                    WorkspaceChatBody(
                                        content = bodyText,
                                        workspaceAbsolutePath = workspacePath,
                                        markdown = markdown,
                                        workspaceFilePaths = line.workspaceFilePaths,
                                        onOpenInApp = onOpenInApp,
                                        skipTrailingImagePreviews = skipTrailingImagePreviews,
                                    )
                                }
                                if (showPickFiles && line.requestUserPickFiles) {
                                    UserFilePickFilesCallToAction(
                                        hint = line.userPickFilesHint,
                                        enabled = attachmentsActionEnabled,
                                        onClick = { onOpenAttachments(line.userPickFilesHint) },
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

/** 多图消息拆出的单张预览，仍用助手气泡样式，但单独占一条 Lazy item。 */
@Composable
internal fun DetachedWorkspaceImageBubble(
    workspacePath: String,
    relativePath: String,
) {
    val bubbles = chatBubbleColors()
    BubbleShell(
        alignEnd = false,
        wide = true,
        background = bubbles.assistantBackground,
        borderColor = bubbles.assistantBorder,
    ) {
        WorkspaceImagePreview(
            workspaceAbsolutePath = workspacePath,
            workspaceRelativePath = relativePath,
        )
    }
}

/** 运行时注入的系统提示（如单轮往返上限）：整宽醒目卡片，「设置页」可点跳转。 */
@Composable
private fun SystemNoticeBubble(
    content: String,
    onOpenSettings: (() -> Unit)?,
) {
    val cs = MaterialTheme.colorScheme
    val body = content.trim().removePrefix("【系统】").trim()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = cs.primaryContainer,
        border = BorderStroke(1.dp, cs.primary),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "运行提示",
                style = MaterialTheme.typography.titleSmall,
                color = cs.primary,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onPrimaryContainer,
            )
            if (onOpenSettings != null) {
                Text(
                    "前往设置页调整单轮往返上限 →",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenSettings),
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.primary,
                    textDecoration = TextDecoration.Underline,
                )
            }
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
    title: String = toolName,
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    val bubbles = chatBubbleColors()
    val failed = finished && isError
    CollapsibleMetaBubble(
        expanded = expanded,
        onToggle = { expanded = !expanded },
        title = title,
        titleColor = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
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

/** 一轮一张卡。展开后每一段思考、每一次工具调用仍是各自的折叠项。 */
@Composable
internal fun TurnActivityBubble(
    stateKey: String,
    pieces: List<TurnPiece>,
    runInProgress: Boolean = false,
) {
    if (pieces.isEmpty() && !runInProgress) return
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    val bubbles = chatBubbleColors()
    val failed = pieces.any { it is TurnPiece.Tool && ChatToolBatch.showsFailure(it.entry) }
    val running = runInProgress || ChatToolBatch.isRunning(pieces)
    BubbleShell(
        alignEnd = false,
        wide = true,
        background = bubbles.systemBackground,
        borderColor = if (failed) MaterialTheme.colorScheme.error else bubbles.systemBorder,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "收起思考和执行" else "展开思考和执行",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    ChatToolBatch.title(pieces),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val detail = ChatToolBatch.detail(pieces)
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    modifier = Modifier.padding(start = 26.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pieces.forEach { piece ->
                        when (piece) {
                            is TurnPiece.Reasoning -> {
                                CollapsibleReasoningBlock(
                                    reasoning = piece.text,
                                    stateKey = "$stateKey::${piece.key}",
                                    inProgress = piece.inProgress,
                                    title = ChatToolBatch.pieceLabel(piece),
                                )
                            }
                            is TurnPiece.Tool -> {
                                CollapsibleToolCallBubble(
                                    stateKey = "$stateKey::${piece.entry.key}",
                                    toolName = piece.entry.toolName,
                                    argsPreview = piece.entry.argsPreview,
                                    finished = piece.entry.finished,
                                    isError = piece.entry.isError,
                                    resultSummary = piece.entry.resultSummary,
                                    progressLines = piece.entry.progressLines,
                                    title = ChatToolBatch.pieceLabel(piece),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsibleReasoningBlock(
    reasoning: String,
    stateKey: String,
    inProgress: Boolean = false,
    title: String = "思考过程",
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    val trimmed = reasoning.trim().trimEnd('▌', ' ').trim()
    if (trimmed.isEmpty()) return
    val bubbles = chatBubbleColors()
    CollapsibleMetaBubble(
        expanded = expanded,
        onToggle = { expanded = !expanded },
        title = title,
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
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
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
                    color = titleColor,
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
