package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agent1.android.R
import com.agent1.android.productivity.logic.business.AccessibleFileLabels
import com.agent1.android.productivity.logic.business.AskUserFormatting
import com.agent1.android.productivity.logic.business.AskUserQuestionUi
import com.agent1.android.productivity.logic.business.SessionAccessibleFilesStore
import com.agent1.android.productivity.logic.business.WorkspaceBrowser
import java.nio.file.Paths

@Composable
fun WorkspaceFilePickerField(
    question: AskUserFormatting.Question,
    workspaceAbsolutePath: String,
    selectedPaths: Set<String>,
    enabled: Boolean,
    onTogglePath: (String) -> Unit,
    onRemoveSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (workspaceAbsolutePath.isBlank()) {
        Text(
            text = stringResource(R.string.workspace_picker_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    val root = remember(workspaceAbsolutePath) { Paths.get(workspaceAbsolutePath) }
    val maxFiles = AskUserQuestionUi.workspaceMaxFiles(question)
    val singleSelect = maxFiles == 1
    val entries = selectedPaths.map { path ->
        SessionAccessibleFilesStore.Entry(
            workspaceRelativePath = path,
            displayName = AccessibleFileLabels.primaryLabel(path, null),
            addedAt = "",
        )
    }
    Column(modifier = modifier.fillMaxWidth()) {
        if (entries.isNotEmpty()) {
            AccessibleFilesPanel(
                entries = entries,
                onRemove = onRemoveSelected,
                title = stringResource(R.string.workspace_picker_selected_title, selectedPaths.size),
                modifier = Modifier.padding(horizontal = 0.dp),
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        } else {
            Text(
                text = stringResource(R.string.workspace_picker_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        maxFiles?.let { limit ->
            Text(
                text = stringResource(R.string.workspace_picker_limit, limit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        WorkspaceTreeList(
            workspaceRoot = root,
            selectedPaths = selectedPaths,
            enabled = enabled,
            singleSelect = singleSelect,
            maxFiles = maxFiles,
            onTogglePath = onTogglePath,
        )
    }
}

@Composable
private fun WorkspaceTreeList(
    workspaceRoot: java.nio.file.Path,
    selectedPaths: Set<String>,
    enabled: Boolean,
    singleSelect: Boolean,
    maxFiles: Int?,
    onTogglePath: (String) -> Unit,
) {
    var expandedDirs by rememberSaveable { mutableStateOf(setOf("")) }
    val visibleRows = remember(expandedDirs, workspaceRoot) {
        buildVisibleRows(workspaceRoot, expandedDirs)
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(visibleRows, key = { it.relativePath + ":" + it.isDirectory }) { row ->
            if (row.isDirectory) {
                DirectoryRow(
                    row = row,
                    expanded = row.relativePath in expandedDirs,
                    enabled = enabled,
                    onToggleExpand = {
                        expandedDirs = if (row.relativePath in expandedDirs) {
                            expandedDirs - row.relativePath
                        } else {
                            expandedDirs + row.relativePath
                        }
                    },
                )
            } else {
                val checked = row.relativePath in selectedPaths
                val atLimit = !singleSelect && maxFiles != null && selectedPaths.size >= maxFiles && !checked
                FileSelectRow(
                    row = row,
                    checked = checked,
                    enabled = enabled && !atLimit,
                    singleSelect = singleSelect,
                    onToggle = { onTogglePath(row.relativePath) },
                )
            }
        }
    }
}

private data class TreeRow(
    val relativePath: String,
    val name: String,
    val isDirectory: Boolean,
    val depth: Int,
)

private fun buildVisibleRows(workspaceRoot: java.nio.file.Path, expandedDirs: Set<String>): List<TreeRow> {
    val rows = mutableListOf<TreeRow>()
    fun walk(relativeDir: String, depth: Int) {
        val children = WorkspaceBrowser.listChildren(workspaceRoot, relativeDir)
        for (child in children) {
            rows += TreeRow(child.relativePath, child.name, child.isDirectory, depth)
            if (child.isDirectory && child.relativePath in expandedDirs) {
                walk(child.relativePath, depth + 1)
            }
        }
    }
    walk("", 0)
    return rows
}

@Composable
private fun DirectoryRow(
    row: TreeRow,
    expanded: Boolean,
    enabled: Boolean,
    onToggleExpand: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggleExpand)
            .padding(start = (8 + row.depth * 14).dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            AgentIcons.Folder,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = row.name,
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun FileSelectRow(
    row: TreeRow,
    checked: Boolean,
    enabled: Boolean,
    singleSelect: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(start = (26 + row.depth * 14).dp, top = 2.dp, bottom = 2.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (singleSelect) {
            RadioButton(selected = checked, onClick = onToggle, enabled = enabled)
        } else {
            Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
        }
        Icon(
            AgentIcons.Description,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = row.name,
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
