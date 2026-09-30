package com.agent1.android.productivity.logic.business

import android.content.Context
import com.agent1.android.llm.CrashReporter
import com.agent1.android.productivity.logic.data.AndroidAgentRuntimeConfig
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

/** 进程内单例 Gateway（logic.business 编排，不含 UI）。 */
object ProductivityGatewayProvider {

    private val ref = AtomicReference<ProductivityAgentGateway?>(null)

    fun get(context: Context): ProductivityAgentGateway {
        ref.get()?.let { return it }
        synchronized(this) {
            ref.get()?.let { return it }
            val root = agentRoot(context)
            @Suppress("TooGenericExceptionCaught")
            val gateway = try {
                ProductivityAgentGateway(
                    context.applicationContext,
                    root,
                    AndroidAgentRuntimeConfig.load(context),
                )
            } catch (t: Exception) {
                CrashReporter.recordHandledFailure(
                    context.applicationContext,
                    "ProductivityAgentGateway.init",
                    t,
                )
                throw t
            }
            ref.set(gateway)
            return gateway
        }
    }

    /** 保存模型配置后重建 Gateway（会关闭旧会话 Host，新 Run 使用新 Key/模型）。 */
    fun reload(context: Context): ProductivityAgentGateway {
        synchronized(this) {
            ref.getAndSet(null)?.close()
            return get(context)
        }
    }

    fun agentRoot(context: Context): Path {
        return context.filesDir.toPath().resolve("agent1")
    }

    fun closeAll() {
        synchronized(this) {
            ref.getAndSet(null)?.close()
        }
    }
}
