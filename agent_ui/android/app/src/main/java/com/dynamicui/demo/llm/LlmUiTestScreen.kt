package com.dynamicui.demo.llm

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dynamicui.demo.BuildConfig
import com.dynamicui.demo.dynamicui.core.UiParser
import com.dynamicui.demo.dynamicui.model.UiDocument
import com.dynamicui.demo.dynamicui.ui.DynamicScreenFromDocument
import kotlinx.coroutines.launch

private const val UI_LOG_TAG = "LlmUiTestScreen"

@Composable
fun LlmUiTestScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val parser = remember { UiParser() }
    val formState = remember { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()
    val promptsResult = remember(context) { loadLlmPromptBundle(context) }
    val promptBundle = promptsResult.getOrElse {
        LlmPromptBundle(
            generationSystemPrompt = "",
            summarySystemPrompt = ""
        )
    }
    val agent = remember(promptsResult) {
        JavaBackedLlmUiAgent(
            config = JavaAgentClientConfig(
                apiKey = BuildConfig.DASHSCOPE_API_KEY,
                baseUrl = BuildConfig.DASHSCOPE_BASE_URL,
                model = "qwen3.5-flash"
            ),
            prompts = promptBundle
        )
    }

    var intent by remember {
        mutableStateOf(
            """
            每月3日上午九点，提醒我开会。
            根据以上内容生成一个UI界面，UI中针对以上内容的每个细节部分都有体现，并可以调节所有的可能选项。
            你可以扩展想想一下用户可能会对哪些未提及的选项感兴趣，并添加到UI中，或者手动修改内容每一项内容。
            """.trimIndent()
        )
    }
    var rawJson by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf<String?>(null) }
    var document by remember { mutableStateOf<UiDocument?>(null) }
    var loadingUi by remember { mutableStateOf(false) }
    var loadingSummary by remember { mutableStateOf(false) }
    var apiError by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf("") }
    var debugLog by remember { mutableStateOf("") }
    var lastCrashReport by remember { mutableStateOf<String?>(null) }

    fun appendLog(message: String) {
        debugLog = (debugLog + "\n" + message).trim()
        Log.d(UI_LOG_TAG, message)
    }

    fun appendCrash(step: String, throwable: Throwable) {
        val stack = Log.getStackTraceString(throwable)
        val brief = throwable.message ?: throwable::class.java.simpleName
        appendLog("异常[$step]: $brief")
        appendLog(stack.take(1600))
        Log.e(UI_LOG_TAG, "Crash at step=$step", throwable)
    }

    LaunchedEffect(context) {
        lastCrashReport = CrashReporter.getLastCrash(context)
        if (!lastCrashReport.isNullOrBlank()) {
            appendLog("检测到上次崩溃报告，已加载到页面")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Qwen 动态 UI 测试",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "当前后端: java-agent-core (artifact)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = intent,
            onValueChange = { intent = it },
            label = { Text("需求描述") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        loadingUi = true
                        try {
                            apiError = null
                            parseError = null
                            document = null
                            summary = ""
                            debugLog = ""
                            formState.clear()
                            appendLog("生成流程启动")
                            if (promptsResult.isFailure) {
                                apiError = promptsResult.exceptionOrNull()?.message ?: "读取提示词失败"
                                appendLog("读取 assets 提示词失败")
                                return@launch
                            }
                            appendLog("开始调用 java-agent-core 生成 UI")

                            agent.generateUiJson(intent)
                                .onSuccess { generated ->
                                    rawJson = generated
                                    appendLog("模型返回成功，开始解析 JSON")
                                    val result = parser.parse(generated)
                                    if (result.isSuccess) {
                                        document = result.document
                                        appendLog("JSON 解析成功，已渲染")
                                    } else {
                                        appendLog("首次解析失败，开始自动修复")
                                        agent.repairUiJson(
                                            intent = intent,
                                            badJson = generated,
                                            parserErrors = result.errors
                                        ).onSuccess { repaired ->
                                            rawJson = repaired
                                            val repairResult = parser.parse(repaired)
                                            if (repairResult.isSuccess) {
                                                document = repairResult.document
                                                appendLog("修复后解析成功")
                                            } else {
                                                parseError = repairResult.errors.joinToString("\n")
                                                appendLog("修复后仍失败: ${repairResult.errors.joinToString("; ")}")
                                            }
                                        }.onFailure { repairError ->
                                            parseError = result.errors.joinToString("\n")
                                            appendCrash("repairUiJson", repairError)
                                        }
                                    }
                                }
                                .onFailure { error ->
                                    apiError = error.message ?: "请求失败"
                                    appendCrash("generateUiJson", error)
                                }
                        } catch (t: Throwable) {
                            apiError = t.message ?: "生成过程发生未知异常"
                            appendCrash("generateFlow", t)
                        } finally {
                            loadingUi = false
                            appendLog("生成流程结束")
                        }
                    }
                },
                enabled = !loadingUi && intent.isNotBlank()
            ) {
                if (loadingUi) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text("生成 UI")
            }

            Button(
                onClick = {
                    scope.launch {
                        loadingSummary = true
                        try {
                            apiError = null
                            summary = ""
                            val currentDoc = document
                            if (currentDoc == null) {
                                apiError = "请先生成并解析成功 UI"
                                appendLog("提交失败: 未生成可渲染 UI")
                                return@launch
                            }
                            appendLog("开始提交用户选择")
                            agent.summarizeSelection(
                                intent = intent,
                                generatedUiJson = rawJson,
                                selection = formState.toMap()
                            ).onSuccess {
                                summary = it
                                appendLog("用户选择总结成功")
                            }.onFailure { error ->
                                apiError = error.message ?: "提交失败"
                                appendCrash("summarizeSelection", error)
                            }
                        } catch (t: Throwable) {
                            apiError = t.message ?: "提交过程发生未知异常"
                            appendCrash("summaryFlow", t)
                        } finally {
                            loadingSummary = false
                            appendLog("提交流程结束")
                        }
                    }
                },
                enabled = !loadingSummary && document != null
            ) {
                if (loadingSummary) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text("提交用户选择")
            }
        }

        val hasDiagnostics = apiError != null || parseError != null || debugLog.isNotBlank() || !lastCrashReport.isNullOrBlank()
        if (hasDiagnostics) {
            Button(
                onClick = {
                    val report = buildString {
                        appendLine("=== LLM UI Diagnostic Report ===")
                        appendLine("intent: $intent")
                        appendLine("apiError: ${apiError ?: "<none>"}")
                        appendLine("parseError: ${parseError ?: "<none>"}")
                        appendLine("debugLog:")
                        appendLine(debugLog.ifBlank { "<none>" })
                        appendLine("rawJsonPreview:")
                        appendLine(rawJson.take(2000).ifBlank { "<none>" })
                        appendLine("lastCrashReport:")
                        appendLine(lastCrashReport ?: "<none>")
                    }
                    clipboardManager.setText(AnnotatedString(report))
                    Toast.makeText(context, "已复制错误详情", Toast.LENGTH_SHORT).show()
                    appendLog("已复制错误详情到剪贴板")
                }
            ) {
                Text("复制错误详情")
            }
        }

        apiError?.let {
            Text(
                text = "请求错误: $it",
                color = MaterialTheme.colorScheme.error
            )
        }
        parseError?.let {
            Text(
                text = "解析错误:\n$it",
                color = MaterialTheme.colorScheme.error
            )
        }

        OutlinedTextField(
            value = rawJson,
            onValueChange = {},
            readOnly = true,
            label = { Text("模型返回 JSON") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("动态渲染预览", style = MaterialTheme.typography.titleMedium)
                if (document != null) {
                    DynamicScreenFromDocument(
                        document = document!!,
                        formState = formState,
                        scrollable = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp)
                    )
                } else {
                    Text("尚未生成可渲染内容")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("当前用户选择", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (formState.isEmpty()) "{}" else formState.entries.joinToString(
                        prefix = "{",
                        postfix = "}"
                    ) { "\"${it.key}\": \"${it.value}\"" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (summary.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("模型总结", style = MaterialTheme.typography.titleMedium)
                    Text(summary)
                }
            }
        }

        if (debugLog.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("调试日志", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = debugLog,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        if (!lastCrashReport.isNullOrBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("上次崩溃报告", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = lastCrashReport.orEmpty().take(2500),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(lastCrashReport.orEmpty()))
                                Toast.makeText(context, "已复制崩溃报告", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("复制崩溃报告")
                        }
                        Button(
                            onClick = {
                                CrashReporter.clearLastCrash(context)
                                lastCrashReport = null
                                appendLog("已清除上次崩溃报告")
                            }
                        ) {
                            Text("清除崩溃报告")
                        }
                        Button(
                            onClick = {
                                CrashReporter.clearAllReports(context)
                                lastCrashReport = null
                                appendLog("已删除所有崩溃报告")
                                Toast.makeText(context, "已删除所有报告", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("删除所有报告")
                        }
                    }
                }
            }
        }
    }
}
