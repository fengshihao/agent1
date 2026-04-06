package com.dynamicui.demo

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dynamicui.demo.llm.LlmUiTestScreen
import com.dynamicui.demo.agent.PetEntryScreen
import com.dynamicui.demo.llm.CrashReporter
import com.dynamicui.demo.dynamicui.ui.DynamicScreenFromAsset

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DemoScreen(
                        onNavigate = { route, params ->
                            Toast.makeText(
                                this,
                                "navigate -> $route, params=$params",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        }
    }
}

private val sampleAssets = listOf(
    "ui/simple_text.json",
    "ui/button_nav.json",
    "ui/mixed_layout.json",
    "ui/form_controls.json",
    "ui/survey_form.json"
)

private fun sampleTitle(asset: String): String {
    return asset
        .substringAfterLast("/")
        .substringBefore(".")
        .replace("_", " ")
}

@Composable
private fun DemoScreen(onNavigate: (String, Map<String, String>) -> Unit) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf("local") } // local | llm | pet
    var selected by rememberSaveable { mutableStateOf(sampleAssets.first()) }
    var hasCrash by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(mode) {
        hasCrash = !CrashReporter.getLastCrash(context).isNullOrBlank()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (hasCrash) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "检测到上次崩溃报告", color = Color(0xFFB00020))
                Button(onClick = { mode = "llm" }) {
                    Text("查看报告")
                }
            }
        }
        TabRow(selectedTabIndex = when (mode) { "local" -> 0; "llm" -> 1; else -> 2 }) {
            Tab(
                selected = mode == "local",
                onClick = { mode = "local" },
                text = { Text("本地样例") }
            )
            Tab(
                selected = mode == "llm",
                onClick = { mode = "llm" },
                text = { Text("Qwen 生成") }
            )
            Tab(
                selected = mode == "pet",
                onClick = { mode = "pet" },
                text = { Text("悬浮宠物") }
            )
        }

        if (mode == "local") {
            Text(
                text = "Dynamic UI v1 (Local JSON)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sampleAssets.forEach { asset ->
                    Button(
                        onClick = { selected = asset }
                    ) {
                        Text(text = sampleTitle(asset))
                    }
                }
            }

            DynamicScreenFromAsset(
                assetPath = selected,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                onNavigate = onNavigate
            )
        } else if (mode == "llm") {
            LlmUiTestScreen(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            PetEntryScreen(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}
