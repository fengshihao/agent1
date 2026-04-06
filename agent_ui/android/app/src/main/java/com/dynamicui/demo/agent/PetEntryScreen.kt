package com.dynamicui.demo.agent

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.IBinder
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dynamicui.demo.agent.overlay.PetOverlayManager
import com.dynamicui.demo.agent.service.AgentForegroundService

@Composable
fun PetEntryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext
    var boundService by remember { mutableStateOf<AgentForegroundService?>(null) }
    var overlayVisible by remember { mutableStateOf(false) }
    val overlay = remember(app) { PetOverlayManager(app) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(context) {
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val b = binder as? AgentForegroundService.LocalBinder ?: return
                boundService = b.service
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
            }
        }
        val intent = Intent(context, AgentForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            @Suppress("DEPRECATION")
            context.startService(intent)
        }
        context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        onDispose {
            context.unbindService(conn)
        }
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
            onClick = {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("请求麦克风权限")
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
                    // 部分 ROM 不响应；退到应用详情页，用户再找「显示在其他应用上层」
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
        val svc = boundService
        Button(
            onClick = {
                if (svc == null) return@Button
                if (!Settings.canDrawOverlays(app)) return@Button
                overlay.show(svc)
                overlayVisible = true
            },
            enabled = svc != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("显示悬浮宠物")
        }
        Button(
            onClick = {
                overlay.hide()
                overlayVisible = false
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("隐藏悬浮窗")
        }
        Text(
            text = when {
                svc == null -> "状态：正在连接 Agent 服务…"
                overlayVisible -> "状态：悬浮层已显示"
                else -> "状态：服务已绑定，可显示悬浮层"
            },
            style = MaterialTheme.typography.labelMedium
        )
    }
}
