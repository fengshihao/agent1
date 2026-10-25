package com.agent1.android.productivity.logic.business

import android.content.Context
import com.agent1.android.productivity.logic.business.platform.WeizhiAndroidScriptEngineFactory
import java.nio.file.Path

/** Weizhi readDocx 块视图与块级写回（预览 P2）。 */
object DocxPreviewWeizhiBackend {

    fun register(context: Context, agentRoot: Path) {
        val appContext = context.applicationContext
        DocxPreviewDocxOps.backend = object : DocxPreviewDocxOps.Backend {
            override fun textViewJson(workspaceRoot: Path, relativePath: String): String? =
                eval(appContext, agentRoot, workspaceRoot, textViewScript(relativePath))

            override fun setBlockText(
                workspaceRoot: Path,
                relativePath: String,
                blockIndex: Int,
                text: String,
            ): String? = eval(
                appContext,
                agentRoot,
                workspaceRoot,
                setBlockTextScript(relativePath, blockIndex, text),
            )

            override fun setBlockStyle(
                workspaceRoot: Path,
                relativePath: String,
                blockIndex: Int,
                bold: Boolean?,
                sizePt: Int?,
            ): String? = eval(
                appContext,
                agentRoot,
                workspaceRoot,
                setBlockStyleScript(relativePath, blockIndex, bold, sizePt),
            )
        }
    }

    private fun eval(
        appContext: Context,
        agentRoot: Path,
        workspaceRoot: Path,
        js: String,
    ): String? {
        return runCatching {
            WeizhiAndroidScriptEngineFactory(appContext, agentRoot).open(workspaceRoot).use { engine ->
                engine.eval(js, 60_000, com.agent1.javaagent.core.CancellationToken())
            }
        }.getOrNull()
    }

    private fun jsQuote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun textViewScript(relativePath: String): String = """
        import { readDocx } from 'weizhi:docx';
        const doc = readDocx(${jsQuote(relativePath)});
        export default doc.textView();
        """.trimIndent()

    private fun setBlockTextScript(relativePath: String, blockIndex: Int, text: String): String = """
        import { readDocx } from 'weizhi:docx';
        const doc = readDocx(${jsQuote(relativePath)});
        doc.setBlockText($blockIndex, ${jsQuote(text)});
        export default doc.save(${jsQuote(relativePath)});
        """.trimIndent()

    private fun setBlockStyleScript(
        relativePath: String,
        blockIndex: Int,
        bold: Boolean?,
        sizePt: Int?,
    ): String {
        val styleParts = buildList {
            if (bold != null) add("bold: $bold")
            if (sizePt != null) add("sizePt: $sizePt")
        }
        val styleObj = if (styleParts.isEmpty()) "{}" else "{ ${styleParts.joinToString(", ")} }"
        return """
            import { readDocx } from 'weizhi:docx';
            const doc = readDocx(${jsQuote(relativePath)});
            doc.setBlockStyle($blockIndex, $styleObj);
            export default doc.save(${jsQuote(relativePath)});
            """.trimIndent()
    }
}
