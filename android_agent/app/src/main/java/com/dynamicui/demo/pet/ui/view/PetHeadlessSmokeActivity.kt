package com.dynamicui.demo.pet.ui.view

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.dynamicui.demo.pet.logic.business.entry.PetPlatformGateway

/**
 * 无 UI 壳验证：仅启动并绑定前台 Agent 服务（不加载 Compose 浮层）。
 * 调试：`adb shell am start -n com.dynamicui.demo/.pet.ui.view.PetHeadlessSmokeActivity`
 */
class PetHeadlessSmokeActivity : ComponentActivity() {

    private val gateway by lazy { PetPlatformGateway(applicationContext) }
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gateway.connect()
        Toast.makeText(this, "Pet headless: FGS + bind（无 Compose）", Toast.LENGTH_SHORT).show()
        mainHandler.postDelayed({ finish() }, 350)
    }

    override fun onDestroy() {
        gateway.disconnect()
        super.onDestroy()
    }
}
