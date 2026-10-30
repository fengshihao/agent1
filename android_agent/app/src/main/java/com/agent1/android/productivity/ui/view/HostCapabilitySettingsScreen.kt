package com.agent1.android.productivity.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.agent1.android.R
import com.agent1.android.productivity.ui.viewmodel.HostCapabilitySettingsViewModel

@Composable
fun HostCapabilitySettingsScreen(
    viewModel: HostCapabilitySettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        AgentTopBar(
            title = stringResource(R.string.host_caps_settings_title),
            subtitle = stringResource(R.string.host_caps_settings_subtitle),
            leading = {
                TopBarIconButton(
                    icon = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = onBack,
                )
            },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CapabilityToggleRow(
                title = stringResource(R.string.host_caps_calendar),
                description = stringResource(R.string.host_caps_calendar_hint),
                checked = state.calendarEnabled,
                onCheckedChange = viewModel::setCalendarEnabled,
            )
            CapabilityToggleRow(
                title = stringResource(R.string.host_caps_location),
                description = stringResource(R.string.host_caps_location_hint),
                checked = state.locationEnabled,
                onCheckedChange = viewModel::setLocationEnabled,
            )
            CapabilityToggleRow(
                title = stringResource(R.string.host_caps_contacts),
                description = stringResource(R.string.host_caps_contacts_hint),
                checked = state.contactsEnabled,
                onCheckedChange = viewModel::setContactsEnabled,
            )
            Text(
                text = stringResource(R.string.host_caps_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CapabilityToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
