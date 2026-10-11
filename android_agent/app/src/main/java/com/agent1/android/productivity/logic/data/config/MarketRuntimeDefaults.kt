package com.agent1.android.productivity.logic.data.config

import android.content.Context
import com.agent1.javaagent.config.AgentRuntimeDefaults

/** 首次安装、尚未在 App 内保存模型配置时的默认服务商与模型。 */
object MarketRuntimeDefaults {

    const val PROVIDER_DASHSCOPE = "dashscope"
    const val PROVIDER_DEEPSEEK = "deepseek"
    private const val DEEPSEEK_BASE_URL = "https://api.deepseek.com"
    private const val DEEPSEEK_MODEL = "deepseek-flash"

    fun defaultProviderId(context: Context): String = when (DeviceMarketRegion.detect(context)) {
        DeviceMarketRegion.Market.DOMESTIC_CN -> PROVIDER_DASHSCOPE
        DeviceMarketRegion.Market.OVERSEAS -> PROVIDER_DEEPSEEK
    }

    fun defaultBaseUrl(providerId: String): String = when (providerId) {
        PROVIDER_DEEPSEEK -> DEEPSEEK_BASE_URL
        else -> AgentRuntimeDefaults.DEFAULT_BASE_URL
    }

    fun defaultModelId(context: Context): String? = when (DeviceMarketRegion.detect(context)) {
        DeviceMarketRegion.Market.DOMESTIC_CN -> null
        DeviceMarketRegion.Market.OVERSEAS -> DEEPSEEK_MODEL
    }
}
