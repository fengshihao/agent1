package com.dynamicui.demo.dynamicui.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class UiDocument(
    val version: String = "1.0",
    val root: UiComponent
)

@Serializable(with = UiComponentSerializer::class)
sealed interface UiComponent {
    val type: String
    val key: String?
    val style: UiStyle?
    val action: UiAction?
}

@Serializable
@SerialName("text")
data class TextComponent(
    override val type: String = "text",
    override val key: String? = null,
    override val style: UiStyle? = null,
    override val action: UiAction? = null,
    val content: String
) : UiComponent

@Serializable
@SerialName("button")
data class ButtonComponent(
    override val type: String = "button",
    override val key: String? = null,
    override val style: UiStyle? = null,
    override val action: UiAction? = null,
    val text: String
) : UiComponent

@Serializable
@SerialName("column")
data class ColumnComponent(
    override val type: String = "column",
    override val key: String? = null,
    override val style: UiStyle? = null,
    override val action: UiAction? = null,
    val children: List<UiComponent> = emptyList()
) : UiComponent

@Serializable
@SerialName("row")
data class RowComponent(
    override val type: String = "row",
    override val key: String? = null,
    override val style: UiStyle? = null,
    override val action: UiAction? = null,
    val children: List<UiComponent> = emptyList()
) : UiComponent

@Serializable
@SerialName("image")
data class ImageComponent(
    override val type: String = "image",
    override val key: String? = null,
    override val style: UiStyle? = null,
    override val action: UiAction? = null,
    val drawableName: String
) : UiComponent

@Serializable
@SerialName("input")
data class InputComponent(
    override val type: String = "input",
    override val key: String? = null,
    override val style: UiStyle? = null,
    override val action: UiAction? = null,
    val label: String? = null,
    val placeholder: String? = null,
    val value: String? = null
) : UiComponent

@Serializable
data class SelectOption(
    val label: String,
    val value: String
)

@Serializable
@SerialName("select")
data class SelectComponent(
    override val type: String = "select",
    override val key: String? = null,
    override val style: UiStyle? = null,
    override val action: UiAction? = null,
    val label: String? = null,
    val placeholder: String? = null,
    val options: List<SelectOption> = emptyList(),
    val selectedValue: String? = null
) : UiComponent

@Serializable
@SerialName("time_picker")
data class TimePickerComponent(
    override val type: String = "time_picker",
    override val key: String? = null,
    override val style: UiStyle? = null,
    override val action: UiAction? = null,
    val label: String? = null,
    val value: String? = null
) : UiComponent

@Serializable
@SerialName("date_picker")
data class DatePickerComponent(
    override val type: String = "date_picker",
    override val key: String? = null,
    override val style: UiStyle? = null,
    override val action: UiAction? = null,
    val label: String? = null,
    val value: String? = null
) : UiComponent

@Serializable
data class UnknownComponent(
    override val type: String = "unknown",
    override val key: String? = null,
    override val style: UiStyle? = null,
    override val action: UiAction? = null,
    val raw: JsonObject = JsonObject(emptyMap())
) : UiComponent

@Serializable
data class UiStyle(
    val padding: Int? = null,
    val backgroundColor: String? = null,
    val textColor: String? = null,
    val fontSize: Int? = null,
    val fontWeight: String? = null
)

@Serializable
data class UiAction(
    val type: String,
    val route: String? = null,
    val params: Map<String, String> = emptyMap()
)
