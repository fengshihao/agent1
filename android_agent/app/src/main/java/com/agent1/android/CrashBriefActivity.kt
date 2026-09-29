package com.agent1.android

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

/** 未捕获崩溃时由 [com.agent1.android.llm.CrashReporter] 拉起，便于在闪退前看到摘要。 */
class CrashBriefActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        val scroll = ScrollView(this)
        scroll.addView(
            TextView(this).apply {
                text = message
                textSize = 14f
                setPadding(40, 40, 40, 40)
            },
        )
        setContentView(scroll)
    }

    companion object {
        const val EXTRA_MESSAGE = "com.agent1.android.crash_brief_message"
    }
}
