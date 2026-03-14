package com.dynamicui.demo.llm

import android.content.Context

data class LlmPromptBundle(
    val generationSystemPrompt: String,
    val summarySystemPrompt: String
)

private const val GENERATION_PROMPT_ASSET = "prompts/ui_generation_system_prompt.txt"
private const val SUMMARY_PROMPT_ASSET = "prompts/ui_summary_system_prompt.txt"

fun loadLlmPromptBundle(context: Context): Result<LlmPromptBundle> {
    return runCatching {
        val generation = context.assets.open(GENERATION_PROMPT_ASSET)
            .bufferedReader()
            .use { it.readText() }
            .trim()
        val summary = context.assets.open(SUMMARY_PROMPT_ASSET)
            .bufferedReader()
            .use { it.readText() }
            .trim()
        require(generation.isNotBlank()) { "生成系统提示词为空: $GENERATION_PROMPT_ASSET" }
        require(summary.isNotBlank()) { "总结系统提示词为空: $SUMMARY_PROMPT_ASSET" }
        LlmPromptBundle(
            generationSystemPrompt = generation,
            summarySystemPrompt = summary
        )
    }
}
