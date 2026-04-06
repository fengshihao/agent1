package com.dynamicui.demo.agent.accessibility.core

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.LinkedHashSet

object PageSnapshotCollector {
    private const val MAX_NODES = 320
    private const val MAX_TEXT_BLOCKS = 120
    private const val MAX_INTERACTIVE = 80

    fun capture(
        root: AccessibilityNodeInfo?,
        eventPackageName: CharSequence?,
        eventClassName: CharSequence?
    ): PageSnapshot? {
        if (root == null) return null
        val titleCandidates = LinkedHashSet<String>()
        val textBlocks = LinkedHashSet<String>()
        val interactive = ArrayList<PageInteractiveElement>()
        val scrollableHints = LinkedHashSet<String>()
        val packageName = root.packageName?.toString().orEmpty().ifBlank { eventPackageName?.toString().orEmpty() }
        val className = root.className?.toString().orEmpty().ifBlank { eventClassName?.toString().orEmpty() }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited += 1

            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val nodeClassName = node.className?.toString().orEmpty()
            val nodePackage = node.packageName?.toString().orEmpty().ifBlank { packageName }

            if (text.isNotEmpty() && textBlocks.size < MAX_TEXT_BLOCKS) {
                textBlocks.add(text)
                if (looksLikeTitle(nodeClassName, text)) {
                    titleCandidates.add(text)
                }
            }
            if (desc.isNotEmpty() && textBlocks.size < MAX_TEXT_BLOCKS) {
                textBlocks.add(desc)
            }

            if (node.isScrollable) {
                scrollableHints.add(nodeClassName.ifBlank { "scrollable" })
            }

            val isInteractive = node.isClickable || node.isLongClickable || nodeClassName.contains("Button", true)
            if (isInteractive && interactive.size < MAX_INTERACTIVE) {
                interactive.add(
                    PageInteractiveElement(
                        text = text,
                        contentDescription = desc,
                        resourceId = node.viewIdResourceName.orEmpty(),
                        className = nodeClassName,
                        packageName = nodePackage,
                        clickable = node.isClickable,
                        enabled = node.isEnabled,
                        bounds = nodeBounds(node)
                    )
                )
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::addLast)
            }
        }

        return PageSnapshot(
            packageName = packageName,
            className = className,
            titleCandidates = titleCandidates.toList().take(12),
            textBlocks = textBlocks.toList(),
            interactiveElements = interactive,
            scrollableHints = scrollableHints.toList().take(8),
            capturedAtMs = System.currentTimeMillis()
        )
    }

    private fun nodeBounds(node: AccessibilityNodeInfo): String {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return "${rect.left},${rect.top},${rect.right},${rect.bottom}"
    }

    private fun looksLikeTitle(className: String, text: String): Boolean {
        if (text.length > 48) return false
        return className.contains("Toolbar", true) ||
            className.contains("Title", true) ||
            className.contains("ActionBar", true) ||
            text.length in 4..22
    }
}
