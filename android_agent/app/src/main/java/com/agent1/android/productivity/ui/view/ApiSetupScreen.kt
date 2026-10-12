package com.agent1.android.productivity.ui.view

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.agent1.android.R
import com.agent1.android.productivity.logic.business.DeepSeekOfficialLinks
import com.agent1.android.productivity.ui.viewmodel.ApiSetupViewModel

/** 没有 API Key 时挡住对话：官网入口 + 粘贴设置。 */
@Composable
fun ApiSetupScreen(
    viewModel: ApiSetupViewModel,
    onConfigured: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.readyToEnter) {
        if (!state.readyToEnter) return@LaunchedEffect
        val notice = state.notice
        if (!notice.isNullOrBlank()) {
            Toast.makeText(context, notice, Toast.LENGTH_LONG).show()
        }
        viewModel.consumeReadyToEnter()
        onConfigured()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AgentTopBar(
            title = stringResource(R.string.setup_title),
            subtitle = state.notice?.takeIf { state.isSaving },
            leading = null,
            actions = {},
        )
        AgentHairline()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SetupStep(
                number = 1,
                cta = stringResource(R.string.setup_step1_cta),
                onClick = { openExternalUrl(context, DeepSeekOfficialLinks.HOME) },
                note = stringResource(R.string.setup_step1),
            )
            SetupStep(
                number = 2,
                cta = stringResource(R.string.setup_step2_cta),
                onClick = { openExternalUrl(context, DeepSeekOfficialLinks.TOP_UP) },
                note = stringResource(R.string.setup_step2),
                noteLink = stringResource(R.string.setup_step2_price),
                onNoteLink = { openExternalUrl(context, DeepSeekOfficialLinks.PRICING) },
            )
            SetupStep(
                number = 3,
                cta = stringResource(R.string.setup_step3_cta),
                onClick = { openExternalUrl(context, DeepSeekOfficialLinks.API_KEYS) },
                note = stringResource(R.string.setup_step3),
                warnNote = true,
            )
            SetupKeyStep(
                draft = state.draft,
                isSaving = state.isSaving,
                notice = state.notice?.takeUnless { state.isSaving },
                noticeIsError = state.noticeIsError,
                onDraftChange = viewModel::onDraftChange,
                onApply = viewModel::apply,
            )
        }
    }
}

@Composable
private fun SetupStep(
    number: Int,
    cta: String,
    onClick: () -> Unit,
    note: String,
    noteLink: String? = null,
    onNoteLink: (() -> Unit)? = null,
    warnNote: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SetupIndex(number)
        Column(modifier = Modifier.weight(1f)) {
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(cta)
            }
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    note,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (warnNote) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (noteLink != null && onNoteLink != null) {
                    TextButton(onClick = onNoteLink) {
                        Text(noteLink)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupKeyStep(
    draft: String,
    isSaving: Boolean,
    notice: String?,
    noticeIsError: Boolean,
    onDraftChange: (String) -> Unit,
    onApply: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SetupIndex(4)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.setup_step4_label),
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSaving,
                placeholder = { Text(stringResource(R.string.setup_key_placeholder)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onApply() }),
            )
            Button(
                onClick = onApply,
                enabled = !isSaving && draft.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(999.dp),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.setup_apply))
                }
            }
            if (!notice.isNullOrBlank()) {
                Text(
                    notice,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (noticeIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                )
            }
            Text(
                stringResource(R.string.setup_step4),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetupIndex(number: Int) {
    Surface(
        modifier = Modifier.size(28.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun openExternalUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }
}
