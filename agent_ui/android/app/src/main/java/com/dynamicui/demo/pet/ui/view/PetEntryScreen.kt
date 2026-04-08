package com.dynamicui.demo.pet.ui.view

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dynamicui.demo.R
import com.dynamicui.demo.pet.logic.business.PetEntryCoordinator
import com.dynamicui.demo.pet.ui.viewmodel.PetEntryViewModel

@Composable
fun PetEntryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current

    val viewModel = remember(app) {
        PetEntryViewModel(
            PetEntryCoordinator(app)
        )
    }
    val uiState by viewModel.uiState.collectAsState()

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val calendarPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(Unit) {
        viewModel.onStart()
        onDispose { viewModel.onStop() }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("悬浮宠物", style = MaterialTheme.typography.titleLarge)
        Text(
            "先启动前台服务，再授予悬浮窗与麦克风；长按右下角宠物说话。",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("请求麦克风权限")
        }
        Button(
            onClick = {
                calendarPermissions.launch(
                    arrayOf(
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.pet_calendar_permission))
        }
        Button(
            onClick = {
                val pkg = context.packageName
                val overlay = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$pkg")
                )
                try {
                    context.startActivity(overlay)
                } catch (_: Exception) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", pkg, null)
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("打开悬浮窗权限设置")
        }
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("打开无障碍设置")
        }
        Text(
            text = if (uiState.accessibilityEnabled) {
                "无障碍状态：已开启"
            } else {
                "无障碍状态：未开启（请在系统设置中手动开启 Pet 助手页面感知）"
            },
            style = MaterialTheme.typography.labelMedium
        )
        Button(
            onClick = { viewModel.onShowOverlayClicked() },
            enabled = uiState.serviceReady,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("显示悬浮宠物")
        }
        Button(
            onClick = { viewModel.onHideOverlayClicked() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("隐藏悬浮窗")
        }
        Text(
            text = uiState.statusText,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
