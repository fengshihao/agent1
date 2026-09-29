package com.agent1.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 启动探测页：不引用 Compose、Weizhi、加密存储。
 * 若此页都一闪而过，崩溃发生在 Application / 系统装载阶段；若此页能停住、点「进入」才退出，崩溃在主界面。
 */
class BootProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        root.addView(
            TextView(this).apply {
                text = "Agent1 已启动（探测页）\nAndroid ${android.os.Build.VERSION.SDK_INT} / ${android.os.Build.MANUFACTURER}"
                textSize = 18f
            },
        )
        root.addView(
            Button(this).apply {
                text = "进入主界面"
                setOnClickListener {
                    startActivity(Intent(this@BootProbeActivity, MainActivity::class.java))
                }
            },
        )
        setContentView(root)
    }
}
