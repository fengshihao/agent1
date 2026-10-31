package com.agent1.android.productivity.logic.business.platform

/**
 * 从后台工具线程回调 UI：确认框与运行时权限。
 * [com.agent1.android.MainActivity] 在前台时注册实现。
 */
object HostUiBridge {

    @Volatile
    private var bridgeRef: ProductivityActivityBridge? = null

    fun register(bridge: ProductivityActivityBridge) {
        bridgeRef = bridge
    }

    fun unregister(bridge: ProductivityActivityBridge) {
        if (bridgeRef === bridge) {
            bridgeRef = null
        }
    }

    fun confirm(message: String): Boolean {
        val bridge = bridgeRef ?: return false
        return bridge.confirm(message)
    }

    fun requestPermissions(permissions: Array<String>): Map<String, Boolean> {
        val bridge = bridgeRef ?: return permissions.associateWith { false }
        return bridge.requestPermissions(permissions)
    }

    /** SAF 选目录树；取消或失败返回 null。 */
    fun pickDocumentTree(): String? {
        val bridge = bridgeRef ?: return null
        return bridge.pickDocumentTree()
    }
}

interface ProductivityActivityBridge {
    fun confirm(message: String): Boolean

    fun requestPermissions(permissions: Array<String>): Map<String, Boolean>

    /** content URI 字符串，供 {@code android.files.pickDirectory}。 */
    fun pickDocumentTree(): String?
}
