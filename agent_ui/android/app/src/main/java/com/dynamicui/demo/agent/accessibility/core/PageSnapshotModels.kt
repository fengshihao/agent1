package com.dynamicui.demo.pet.logic.data.accessibility.core

data class PageInteractiveElement(
    val text: String,
    val contentDescription: String,
    val resourceId: String,
    val className: String,
    val packageName: String,
    val clickable: Boolean,
    val enabled: Boolean,
    val bounds: String
)

data class PageSnapshot(
    val packageName: String,
    val className: String,
    val titleCandidates: List<String>,
    val textBlocks: List<String>,
    val interactiveElements: List<PageInteractiveElement>,
    val scrollableHints: List<String>,
    val capturedAtMs: Long
) {
    fun digest(): String {
        val title = titleCandidates.firstOrNull().orEmpty()
        val text = textBlocks.take(5).joinToString("|")
        val elements = interactiveElements.take(8).joinToString("|") { it.text.ifBlank { it.contentDescription } }
        return listOf(packageName, className, title, text, elements).joinToString("#")
    }
}
