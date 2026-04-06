package com.dynamicui.demo.agent.accessibility.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.os.Bundle
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.dynamicui.demo.agent.accessibility.core.PageSnapshotCollector
import com.dynamicui.demo.agent.accessibility.core.PageSnapshotStore
import java.lang.ref.WeakReference

data class ActionResult(
    val ok: Boolean,
    val reason: String,
    val matchedElement: String = ""
)

data class TapSelector(
    val text: String = "",
    val resourceId: String = "",
    val className: String = "",
    val index: Int = 0,
    val fallbackX: Float? = null,
    val fallbackY: Float? = null
)

data class InputSelector(
    val text: String = "",
    val resourceId: String = "",
    val className: String = "",
    val index: Int = 0
)

data class UiActionRequest(
    val action: String,
    val text: String = "",
    val resourceId: String = "",
    val className: String = "",
    val index: Int = 0,
    val inputText: String = "",
    val direction: String = "forward",
    val autoSubmit: Boolean = false,
    val submitTexts: List<String> = emptyList(),
    val fallbackX: Float? = null,
    val fallbackY: Float? = null
)

private val DEFAULT_SUBMIT_LABELS = listOf("发送", "送出", "提交", "确定", "搜索", "Search", "Send", "Go")

class PetAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        currentRef = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val relevant = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        if (!relevant) return
        val snapshot = PageSnapshotCollector.capture(rootInActiveWindow, event.packageName, event.className) ?: return
        PageSnapshotStore.update(snapshot)
    }

    override fun onInterrupt() = Unit

    fun tapBySelector(selector: TapSelector): ActionResult {
        val root = rootInActiveWindow ?: return ActionResult(false, "当前无可访问页面")
        val target = findMatchingNode(root, selector)
        if (target != null) {
            val clickable = nearestClickable(target)
            if (clickable != null && clickable.isEnabled && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                refreshSnapshot()
                return ActionResult(
                    ok = true,
                    reason = "点击成功",
                    matchedElement = describeNode(target)
                )
            }
        }
        if (selector.fallbackX != null && selector.fallbackY != null && tryTapCoordinate(selector.fallbackX, selector.fallbackY)) {
            refreshSnapshot()
            return ActionResult(true, "节点点击失败，已使用坐标手势点击")
        }
        return ActionResult(false, "未找到可点击目标")
    }

    fun scrollPage(direction: String): ActionResult {
        val root = rootInActiveWindow ?: return ActionResult(false, "当前无可访问页面")
        val node = findScrollableNode(root)
        if (node == null) return ActionResult(false, "未找到可滚动区域")
        val action = if (direction.equals("backward", true) || direction.equals("up", true)) {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        val ok = node.performAction(action)
        if (ok) {
            refreshSnapshot()
            return ActionResult(true, "滚动已触发", node.className?.toString().orEmpty())
        }
        return ActionResult(false, "滚动失败")
    }

    fun setInputText(
        selector: InputSelector,
        inputText: String,
        autoSubmit: Boolean = false,
        submitTexts: List<String> = DEFAULT_SUBMIT_LABELS
    ): ActionResult {
        if (inputText.isBlank()) return ActionResult(false, "输入文本不能为空")
        val root = rootInActiveWindow ?: return ActionResult(false, "当前无可访问页面")
        val target = findInputNode(root, selector) ?: return ActionResult(false, "未找到输入框")
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        val bundle = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, inputText)
        }
        val setOk = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
        if (setOk) {
            val submitResult = if (autoSubmit) tryAutoSubmit(submitTexts) else null
            refreshSnapshot()
            val reason = if (submitResult == null) {
                "输入成功"
            } else {
                "输入成功；${submitResult.reason}"
            }
            return ActionResult(true, reason, describeNode(target))
        }

        // 部分 App 不支持 ACTION_SET_TEXT，回退到剪贴板粘贴。
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("agent_input", inputText))
            val pasted = target.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            if (pasted) {
                val submitResult = if (autoSubmit) tryAutoSubmit(submitTexts) else null
                refreshSnapshot()
                val reason = if (submitResult == null) {
                    "输入成功(粘贴兜底)"
                } else {
                    "输入成功(粘贴兜底)；${submitResult.reason}"
                }
                return ActionResult(true, reason, describeNode(target))
            }
        }
        return ActionResult(false, "目标输入框不支持写入")
    }

    fun actOnUi(request: UiActionRequest): ActionResult {
        return when (request.action.lowercase()) {
            "tap", "click" -> tapBySelector(
                TapSelector(
                    text = request.text,
                    resourceId = request.resourceId,
                    className = request.className,
                    index = request.index,
                    fallbackX = request.fallbackX,
                    fallbackY = request.fallbackY
                )
            )
            "scroll" -> scrollPage(request.direction)
            "input", "set_text" -> setInputText(
                selector = InputSelector(
                    text = request.text,
                    resourceId = request.resourceId,
                    className = request.className,
                    index = request.index
                ),
                inputText = request.inputText,
                autoSubmit = request.autoSubmit,
                submitTexts = request.submitTexts
            )
            "back" -> {
                val ok = performGlobalAction(GLOBAL_ACTION_BACK)
                if (ok) refreshSnapshot()
                ActionResult(ok, if (ok) "返回成功" else "返回失败")
            }
            "home" -> {
                val ok = performGlobalAction(GLOBAL_ACTION_HOME)
                if (ok) refreshSnapshot()
                ActionResult(ok, if (ok) "回到桌面成功" else "回到桌面失败")
            }
            else -> ActionResult(false, "不支持的 action: ${request.action}")
        }
    }

    private fun tryAutoSubmit(submitTexts: List<String>): ActionResult {
        val labels = submitTexts.map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { DEFAULT_SUBMIT_LABELS }
        val root = rootInActiveWindow ?: return ActionResult(false, "自动发送失败：当前无可访问页面")
        for (label in labels) {
            val node = findMatchingNode(root, TapSelector(text = label))
            if (node != null) {
                val clickable = nearestClickable(node)
                if (clickable != null && clickable.isEnabled && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return ActionResult(true, "自动点击\"$label\"成功", describeNode(node))
                }
            }
        }
        return ActionResult(false, "自动发送失败：未找到发送/提交按钮")
    }

    private fun refreshSnapshot() {
        val snapshot = PageSnapshotCollector.capture(rootInActiveWindow, null, null) ?: return
        PageSnapshotStore.update(snapshot)
    }

    private fun findMatchingNode(root: AccessibilityNodeInfo, selector: TapSelector): AccessibilityNodeInfo? {
        val hits = ArrayList<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && hits.size < 24) {
            val node = queue.removeFirst()
            if (match(node, selector)) {
                hits.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::addLast)
            }
        }
        if (hits.isEmpty()) return null
        val idx = selector.index.coerceAtLeast(0).coerceAtMost(hits.lastIndex)
        return hits[idx]
    }

    private fun findInputNode(root: AccessibilityNodeInfo, selector: InputSelector): AccessibilityNodeInfo? {
        val hits = ArrayList<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && hits.size < 24) {
            val node = queue.removeFirst()
            if (matchInput(node, selector)) {
                hits.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::addLast)
            }
        }
        if (hits.isEmpty()) return null
        val idx = selector.index.coerceAtLeast(0).coerceAtMost(hits.lastIndex)
        return hits[idx]
    }

    private fun match(node: AccessibilityNodeInfo, selector: TapSelector): Boolean {
        if (!node.isEnabled) return false
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val id = node.viewIdResourceName.orEmpty()
        val className = node.className?.toString().orEmpty()
        val textOk = selector.text.isBlank() || text.contains(selector.text, true) || desc.contains(selector.text, true)
        val idOk = selector.resourceId.isBlank() || id.contains(selector.resourceId, true)
        val classOk = selector.className.isBlank() || className.contains(selector.className, true)
        return textOk && idOk && classOk && (node.isClickable || node.isLongClickable || className.contains("Button", true))
    }

    private fun matchInput(node: AccessibilityNodeInfo, selector: InputSelector): Boolean {
        if (!node.isEnabled) return false
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) node.hintText?.toString().orEmpty() else ""
        val id = node.viewIdResourceName.orEmpty()
        val className = node.className?.toString().orEmpty()
        val textOk = selector.text.isBlank() ||
            text.contains(selector.text, true) ||
            desc.contains(selector.text, true) ||
            hint.contains(selector.text, true)
        val idOk = selector.resourceId.isBlank() || id.contains(selector.resourceId, true)
        val classOk = selector.className.isBlank() || className.contains(selector.className, true)
        val inputLike = node.isEditable ||
            className.contains("EditText", true) ||
            className.contains("TextField", true) ||
            className.contains("AutoComplete", true)
        return textOk && idOk && classOk && inputLike
    }

    private fun nearestClickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cursor: AccessibilityNodeInfo? = node
        var depth = 0
        while (cursor != null && depth < 5) {
            if (cursor.isClickable) return cursor
            cursor = cursor.parent
            depth += 1
        }
        return null
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::addLast)
            }
        }
        return null
    }

    private fun describeNode(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val id = node.viewIdResourceName.orEmpty()
        return listOf(text, desc, id).firstOrNull { it.isNotBlank() }.orEmpty()
    }

    private fun tryTapCoordinate(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile
        private var currentRef: WeakReference<PetAccessibilityService>? = null

        fun current(): PetAccessibilityService? = currentRef?.get()
    }
}
