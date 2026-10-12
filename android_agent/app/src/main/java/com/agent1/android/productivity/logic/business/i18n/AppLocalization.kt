package com.agent1.android.productivity.logic.business.i18n

import android.content.Context
import androidx.annotation.StringRes
import com.agent1.android.R

/**
 * 应用内用户可见文案：跟随系统 [android.content.res.Configuration] 语言（values-*）。
 * 在 [com.agent1.android.Agent1Application] 中初始化；单测可 [install] 假 Context。
 */
object AppLocalization {

    @Volatile
    private var appContext: Context? = null

    fun attach(applicationContext: Context) {
        appContext = applicationContext.applicationContext
    }

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    fun requireContext(): Context =
        checkNotNull(appContext) { "AppLocalization.attach() not called" }

    fun getString(@StringRes id: Int, vararg formatArgs: Any): String =
        requireContext().getString(id, *formatArgs)
}
