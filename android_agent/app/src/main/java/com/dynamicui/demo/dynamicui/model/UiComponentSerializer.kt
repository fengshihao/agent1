package com.dynamicui.demo.dynamicui.model

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object UiComponentSerializer : JsonContentPolymorphicSerializer<UiComponent>(UiComponent::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<UiComponent> {
        val obj = element as? JsonObject ?: return UnknownComponent.serializer()
        val type = obj["type"]?.jsonPrimitive?.contentOrNull
        return when (type) {
            "text" -> TextComponent.serializer()
            "button" -> ButtonComponent.serializer()
            "column" -> ColumnComponent.serializer()
            "row" -> RowComponent.serializer()
            "image" -> ImageComponent.serializer()
            "input" -> InputComponent.serializer()
            "select" -> SelectComponent.serializer()
            "time_picker" -> TimePickerComponent.serializer()
            "date_picker" -> DatePickerComponent.serializer()
            else -> UnknownComponent.serializer()
        }
    }
}
