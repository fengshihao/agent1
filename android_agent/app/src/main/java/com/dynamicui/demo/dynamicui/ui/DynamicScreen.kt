package com.dynamicui.demo.dynamicui.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicui.demo.dynamicui.core.UiParser
import com.dynamicui.demo.dynamicui.core.readAsset
import com.dynamicui.demo.dynamicui.model.ButtonComponent
import com.dynamicui.demo.dynamicui.model.ColumnComponent
import com.dynamicui.demo.dynamicui.model.DatePickerComponent
import com.dynamicui.demo.dynamicui.model.ImageComponent
import com.dynamicui.demo.dynamicui.model.InputComponent
import com.dynamicui.demo.dynamicui.model.RowComponent
import com.dynamicui.demo.dynamicui.model.SelectComponent
import com.dynamicui.demo.dynamicui.model.TextComponent
import com.dynamicui.demo.dynamicui.model.TimePickerComponent
import com.dynamicui.demo.dynamicui.model.UiAction
import com.dynamicui.demo.dynamicui.model.UiComponent
import com.dynamicui.demo.dynamicui.model.UiDocument
import com.dynamicui.demo.dynamicui.model.UiStyle
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun DynamicScreenFromAsset(
    assetPath: String,
    modifier: Modifier = Modifier,
    onNavigate: (String, Map<String, String>) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val parser = remember { UiParser() }
    val formState = remember(assetPath) { mutableStateMapOf<String, String>() }
    var parseError by remember { mutableStateOf<String?>(null) }
    var root by remember { mutableStateOf<UiComponent?>(null) }

    LaunchedEffect(assetPath) {
        val raw = runCatching { readAsset(context, assetPath) }.getOrElse {
            parseError = "读取资源失败: $assetPath"
            root = null
            return@LaunchedEffect
        }
        val result = parser.parse(raw)
        if (result.isSuccess) {
            root = result.document?.root
            parseError = null
        } else {
            root = null
            parseError = result.errors.joinToString("\n")
        }
    }

    when {
        parseError != null -> ErrorFallback(parseError!!)
        root != null -> DynamicScreenFromDocument(
            document = UiDocument(version = "1.0", root = root!!),
            modifier = modifier,
            onNavigate = onNavigate,
            formState = formState
        )
        else -> Text("加载中...", modifier = modifier.padding(16.dp))
    }
}

@Composable
fun DynamicScreenFromDocument(
    document: UiDocument,
    modifier: Modifier = Modifier,
    onNavigate: (String, Map<String, String>) -> Unit = { _, _ -> },
    formState: MutableMap<String, String> = remember { mutableStateMapOf() },
    scrollable: Boolean = true
) {
    val boxModifier = if (scrollable) {
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    } else {
        modifier.fillMaxSize()
    }
    Box(modifier = boxModifier) {
        RenderComponent(
            document.root,
            modifier = Modifier.fillMaxWidth(),
            onNavigate = onNavigate,
            formState = formState
        )
    }
}

@Composable
private fun RenderComponent(
    component: UiComponent,
    modifier: Modifier = Modifier,
    onNavigate: (String, Map<String, String>) -> Unit,
    formState: MutableMap<String, String>
) {
    when (component) {
        is TextComponent -> {
            val textColor = parseHexColor(component.style?.textColor) ?: Color.Unspecified
            val textStyle = TextStyle(
                fontSize = (component.style?.fontSize ?: 16).sp,
                fontWeight = parseFontWeight(component.style?.fontWeight)
            )
            Text(
                text = component.content,
                color = textColor,
                style = textStyle,
                modifier = modifier.applyStyle(component.style)
            )
        }
        is ButtonComponent -> {
            Button(
                onClick = { handleAction(component.action, onNavigate, formState) },
                modifier = modifier.applyStyle(component.style)
            ) {
                Text(text = component.text)
            }
        }
        is ColumnComponent -> {
            Column(
                modifier = modifier.applyStyle(component.style),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                component.children.forEach { child ->
                    RenderComponent(
                        component = child,
                        modifier = Modifier.fillMaxWidth(),
                        onNavigate = onNavigate,
                        formState = formState
                    )
                }
            }
        }
        is RowComponent -> {
            Row(
                modifier = modifier.applyStyle(component.style),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                component.children.forEach { child ->
                    RenderComponent(
                        component = child,
                        modifier = Modifier.weight(1f),
                        onNavigate = onNavigate,
                        formState = formState
                    )
                }
            }
        }
        is ImageComponent -> {
            val painter = resolveDrawablePainter(component.drawableName)
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = component.drawableName,
                    contentScale = ContentScale.Crop,
                    modifier = modifier.applyStyle(component.style)
                )
            } else {
                Text(
                    text = "图片未找到: ${component.drawableName}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = modifier.padding(8.dp)
                )
            }
        }
        is InputComponent -> {
            var value by remember(component.key, component.value) {
                mutableStateOf(component.value.orEmpty())
            }
            component.key?.let { key ->
                LaunchedEffect(key, component.value) {
                    formState[key] = value
                }
            }
            Column(modifier = modifier.applyStyle(component.style)) {
                component.label?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, modifier = Modifier.padding(bottom = 4.dp))
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        component.key?.let { key -> formState[key] = it }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        component.placeholder?.let { hint -> Text(hint) }
                    }
                )
            }
        }
        is SelectComponent -> {
            var expanded by remember(component.key) { mutableStateOf(false) }
            var selectedValue by remember(component.key, component.selectedValue) {
                mutableStateOf(component.selectedValue)
            }
            component.key?.let { key ->
                LaunchedEffect(key, component.selectedValue) {
                    selectedValue?.let { formState[key] = it }
                }
            }
            val selectedLabel = component.options
                .firstOrNull { it.value == selectedValue }
                ?.label
                .orEmpty()
            Column(modifier = modifier.applyStyle(component.style)) {
                component.label?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, modifier = Modifier.padding(bottom = 4.dp))
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier
                            .fillMaxWidth(),
                    ) {
                        Text(
                            text = when {
                                selectedLabel.isNotBlank() -> selectedLabel
                                !component.placeholder.isNullOrBlank() -> component.placeholder
                                else -> "请选择"
                            }
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        component.options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    selectedValue = option.value
                                    component.key?.let { key -> formState[key] = option.value }
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
        is TimePickerComponent -> {
            val context = LocalContext.current
            var value by remember(component.key, component.value) {
                mutableStateOf(component.value.orEmpty())
            }
            component.key?.let { key ->
                LaunchedEffect(key, component.value) {
                    formState[key] = value
                }
            }
            Column(modifier = modifier.applyStyle(component.style)) {
                component.label?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, modifier = Modifier.padding(bottom = 4.dp))
                }
                OutlinedButton(
                    onClick = {
                        val init = parseTimeOrNow(value)
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                value = String.format("%02d:%02d", hour, minute)
                                component.key?.let { key -> formState[key] = value }
                            },
                            init.hour,
                            init.minute,
                            true
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (value.isBlank()) "请选择时间" else value)
                }
            }
        }
        is DatePickerComponent -> {
            val context = LocalContext.current
            var value by remember(component.key, component.value) {
                mutableStateOf(component.value.orEmpty())
            }
            component.key?.let { key ->
                LaunchedEffect(key, component.value) {
                    formState[key] = value
                }
            }
            Column(modifier = modifier.applyStyle(component.style)) {
                component.label?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, modifier = Modifier.padding(bottom = 4.dp))
                }
                OutlinedButton(
                    onClick = {
                        val init = parseDateOrToday(value)
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                value = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                                component.key?.let { key -> formState[key] = value }
                            },
                            init.year,
                            init.monthValue - 1,
                            init.dayOfMonth
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (value.isBlank()) "请选择日期" else value)
                }
            }
        }
        else -> {
            Text(
                text = "未知组件: ${component.type}",
                color = MaterialTheme.colorScheme.error,
                modifier = modifier.padding(8.dp)
            )
        }
    }
}

@Composable
private fun resolveDrawablePainter(drawableName: String): Painter? {
    val context = LocalContext.current
    val id = remember(drawableName) {
        context.resources.getIdentifier(drawableName, "drawable", context.packageName)
    }
    return if (id != 0) painterResource(id = id) else null
}

private fun handleAction(
    action: UiAction?,
    onNavigate: (String, Map<String, String>) -> Unit,
    formState: Map<String, String>
) {
    if (action?.type == "navigate" && !action.route.isNullOrBlank()) {
        onNavigate(action.route, action.params + formState)
    }
}

@Composable
private fun ErrorFallback(message: String) {
    Text(
        text = "渲染失败\n$message",
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    )
}

private fun Modifier.applyStyle(style: UiStyle?): Modifier {
    if (style == null) return this
    var next = this
    style.padding?.let { next = next.padding(it.dp) }
    parseHexColor(style.backgroundColor)?.let { next = next.background(it) }
    return next
}

private fun parseHexColor(raw: String?): Color? {
    if (raw.isNullOrBlank()) return null
    return runCatching { Color(android.graphics.Color.parseColor(raw)) }.getOrNull()
}

private fun parseFontWeight(raw: String?): FontWeight {
    return when (raw?.lowercase()) {
        "bold", "700" -> FontWeight.Bold
        "medium", "500" -> FontWeight.Medium
        else -> FontWeight.Normal
    }
}

private fun parseDateOrToday(value: String): LocalDate {
    return runCatching { LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE) }
        .getOrElse { LocalDate.now() }
}

private fun parseTimeOrNow(value: String): LocalTime {
    return runCatching { LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm")) }
        .getOrElse {
            val now = LocalTime.now()
            LocalTime.of(now.hour, now.minute)
        }
}
