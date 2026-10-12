package com.agent1.android.productivity.logic.business

/**
 * 产物库展示分层：用户关心的交付物 vs 编排/脚本等细碎文件。
 * 纯 JVM，供 ViewModel 单测与 [ArtifactLibraryStore] 列表项联合过滤。
 */
object ArtifactLibraryFilter {

    enum class Shelf(val label: String) {
        /** HTML、Office、PDF、图片等常见交付物。 */
        PRIMARY("主要文件"),
        /** JS 片段、配置、源码等辅助产物。 */
        AUXILIARY("细碎文件"),
    }

    enum class TypeFilter(val label: String) {
        ALL("全部"),
        HTML("网页"),
        DOCUMENT("文档"),
        PRESENTATION("演示"),
        SPREADSHEET("表格"),
        PDF("PDF"),
        IMAGE("图片"),
        MARKDOWN("Markdown"),
        CODE("代码/脚本"),
        DATA("数据/配置"),
        OTHER("其他"),
    }

    fun extensionOf(workspaceRelativePath: String): String =
        workspaceRelativePath.substringAfterLast('.', "").lowercase()

    fun shelfFor(relativePath: String): Shelf =
        when (extensionOf(relativePath)) {
            "html", "htm",
            "doc", "docx", "odt", "rtf",
            "ppt", "pptx", "odp",
            "pdf",
            "xls", "xlsx", "csv", "ods",
            "md", "markdown",
            "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico", "heic", "heif",
            "mp4", "mov", "webm", "mkv", "avi",
            "zip", "rar", "7z",
            "txt",
            -> Shelf.PRIMARY
            else -> Shelf.AUXILIARY
        }

    fun typeFilterFor(relativePath: String): TypeFilter =
        when (extensionOf(relativePath)) {
            "html", "htm" -> TypeFilter.HTML
            "doc", "docx", "odt", "rtf" -> TypeFilter.DOCUMENT
            "ppt", "pptx", "odp" -> TypeFilter.PRESENTATION
            "xls", "xlsx", "csv", "ods" -> TypeFilter.SPREADSHEET
            "pdf" -> TypeFilter.PDF
            "md", "markdown" -> TypeFilter.MARKDOWN
            "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico", "heic", "heif" -> TypeFilter.IMAGE
            "js", "mjs", "cjs", "ts", "jsx", "tsx",
            "py", "rb", "go", "rs", "java", "kt", "kts", "gradle",
            "sh", "bash", "zsh", "bat", "ps1",
            "css", "scss", "less",
            -> TypeFilter.CODE
            "json", "xml", "yaml", "yml", "toml", "ini", "properties", "env", "b64", "lock",
            -> TypeFilter.DATA
            else -> TypeFilter.OTHER
        }

    /** 在当前分层下可选的类型筛选项（始终含「全部」）。 */
    fun typeFilterChoices(shelf: Shelf): List<TypeFilter> {
        val base = listOf(TypeFilter.ALL)
        val extras = when (shelf) {
            Shelf.PRIMARY -> listOf(
                TypeFilter.HTML,
                TypeFilter.DOCUMENT,
                TypeFilter.PRESENTATION,
                TypeFilter.SPREADSHEET,
                TypeFilter.PDF,
                TypeFilter.IMAGE,
                TypeFilter.MARKDOWN,
                TypeFilter.OTHER,
            )
            Shelf.AUXILIARY -> listOf(
                TypeFilter.CODE,
                TypeFilter.DATA,
                TypeFilter.OTHER,
            )
        }
        return base + extras
    }

    fun matches(
        workspaceRelativePath: String,
        shelf: Shelf,
        typeFilter: TypeFilter,
        query: String,
        sessionTitle: String,
        fileName: String,
    ): Boolean {
        if (shelfFor(workspaceRelativePath) != shelf) return false
        if (typeFilter != TypeFilter.ALL && typeFilterFor(workspaceRelativePath) != typeFilter) {
            return false
        }
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return fileName.lowercase().contains(q) ||
            sessionTitle.lowercase().contains(q) ||
            workspaceRelativePath.lowercase().contains(q)
    }
}
