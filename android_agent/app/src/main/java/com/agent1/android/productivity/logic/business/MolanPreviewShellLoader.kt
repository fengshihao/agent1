package com.agent1.android.productivity.logic.business

import android.content.Context

/**
 * 墨览（molan）Markdown 预览壳：APK 内仅保留壳 HTML（DOM + 注入桥），
 * 编辑器与 vditor vendor 等大库全部引用线上 try 页
 * （https://fengshihao.github.io/molan/try/），与 docx 壳模式一致。
 */
object MolanPreviewShellLoader {

    const val ASSET_NAME = "molan-md-shell.html"

    /**
     * `loadDataWithBaseURL` 的 base：与线上 try 页同源，
     * 使 studio 的 localStorage / IndexedDB / fetch 语义与浏览器一致。
     */
    const val BASE_URL = "https://fengshihao.github.io/molan/try/"

    fun loadHtml(context: Context): String? {
        return runCatching {
            context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
