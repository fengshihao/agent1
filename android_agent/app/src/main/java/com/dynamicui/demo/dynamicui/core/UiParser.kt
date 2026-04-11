package com.dynamicui.demo.dynamicui.core

import android.content.Context
import com.dynamicui.demo.dynamicui.model.ButtonComponent
import com.dynamicui.demo.dynamicui.model.ColumnComponent
import com.dynamicui.demo.dynamicui.model.DatePickerComponent
import com.dynamicui.demo.dynamicui.model.InputComponent
import com.dynamicui.demo.dynamicui.model.RowComponent
import com.dynamicui.demo.dynamicui.model.SelectComponent
import com.dynamicui.demo.dynamicui.model.TextComponent
import com.dynamicui.demo.dynamicui.model.TimePickerComponent
import com.dynamicui.demo.dynamicui.model.UiComponent
import com.dynamicui.demo.dynamicui.model.UiDocument
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json

data class ParseResult(
    val document: UiDocument? = null,
    val errors: List<String> = emptyList()
) {
    val isSuccess: Boolean get() = document != null && errors.isEmpty()
}

class UiParser(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }
) {
    fun parse(raw: String): ParseResult {
        return try {
            val doc = json.decodeFromString(UiDocument.serializer(), raw)
            val errors = validateComponent(doc.root, "root")
            ParseResult(document = doc, errors = errors)
        } catch (e: Exception) {
            ParseResult(errors = listOf("JSON 解析失败: ${e.message}"))
        }
    }

    private fun validateComponent(component: UiComponent, path: String): List<String> {
        val current = mutableListOf<String>()
        when (component) {
            is TextComponent -> {
                if (component.content.isBlank()) {
                    current += "$path.content 不能为空"
                }
            }
            is ButtonComponent -> {
                if (component.text.isBlank()) {
                    current += "$path.text 不能为空"
                }
                val action = component.action
                if (action != null && action.type == "navigate" && action.route.isNullOrBlank()) {
                    current += "$path.action.route 不能为空"
                }
            }
            is ColumnComponent -> {
                component.children.forEachIndexed { index, child ->
                    current += validateComponent(child, "$path.children[$index]")
                }
            }
            is RowComponent -> {
                component.children.forEachIndexed { index, child ->
                    current += validateComponent(child, "$path.children[$index]")
                }
            }
            is InputComponent -> Unit
            is SelectComponent -> {
                if (component.options.isEmpty()) {
                    current += "$path.options 不能为空"
                }
                component.options.forEachIndexed { index, option ->
                    if (option.label.isBlank()) {
                        current += "$path.options[$index].label 不能为空"
                    }
                    if (option.value.isBlank()) {
                        current += "$path.options[$index].value 不能为空"
                    }
                }
            }
            is DatePickerComponent -> {
                if (!component.value.isNullOrBlank() && !isDate(component.value)) {
                    current += "$path.value 日期格式应为 yyyy-MM-dd"
                }
            }
            is TimePickerComponent -> {
                if (!component.value.isNullOrBlank() && !isTime(component.value)) {
                    current += "$path.value 时间格式应为 HH:mm"
                }
            }
            else -> Unit
        }
        return current
    }

    private fun isDate(value: String): Boolean {
        return runCatching {
            LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
        }.isSuccess
    }

    private fun isTime(value: String): Boolean {
        return runCatching {
            LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm"))
        }.isSuccess
    }
}

fun readAsset(context: Context, assetPath: String): String {
    return context.assets.open(assetPath).bufferedReader().use { it.readText() }
}
