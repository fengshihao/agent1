package com.agent1.android.productivity.logic.business

/** 会话「已选文件」在 UI 上的展示名：主标题用文件名，不把 `imports/` 当标题。 */
object AccessibleFileLabels {

    private const val IMPORTS_PREFIX = "imports/"

    fun primaryLabel(workspaceRelativePath: String, displayName: String?): String {
        val fromMeta = displayName?.trim()?.takeIf { it.isNotEmpty() }
        if (fromMeta != null) return fromMeta
        val normalized = workspaceRelativePath.trim().replace('\\', '/')
        val leaf = normalized.substringAfterLast('/').ifEmpty { normalized }
        return leaf
    }

    /**
     * 次要说明：父目录（已去掉 `imports/` 前缀）。无父目录时返回 null。
     */
    fun parentHint(workspaceRelativePath: String): String? {
        val normalized = workspaceRelativePath.trim().replace('\\', '/')
        val withoutImports = normalized.removePrefix(IMPORTS_PREFIX)
        val parent = withoutImports.substringBeforeLast('/', missingDelimiterValue = "")
        return parent.takeIf { it.isNotEmpty() }
    }
}
