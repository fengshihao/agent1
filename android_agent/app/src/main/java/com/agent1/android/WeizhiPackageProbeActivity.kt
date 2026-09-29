package com.agent1.android

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/**
 * 不在启动时加载 [com.weizhi.WeizhiEngine]。
 * 先展示包内 SO 与打包戳记；只有点按钮才 loadLibrary / 初始化类。
 * 若点「初始化引擎类」后进程直接消失且没有异常文字，就是 native 崩溃（常见于 AAR 与 SO 不配套）。
 */
class WeizhiPackageProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = TextView(this).apply {
            textSize = 14f
            text = buildReport()
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        root.addView(Button(this).apply {
            text = "1. 只加载 libweizhijni.so"
            setOnClickListener {
                body.append("\n\n--- loadLibrary ---\n")
                body.append(loadNative())
            }
        })
        root.addView(Button(this).apply {
            text = "2. 初始化 WeizhiEngine 类（可能闪退）"
            setOnClickListener {
                body.append("\n\n--- Class.forName WeizhiEngine ---\n")
                body.append(initEngineClass())
            }
        })
        val scroll = ScrollView(this)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(root)
        column.addView(body)
        scroll.addView(column)
        setContentView(scroll)
    }

    private fun buildReport(): String {
        val stamp = runCatching {
            assets.open("weizhi-package-stamp.txt").bufferedReader().use { it.readText() }
        }.getOrElse { "stamp=missing (${it.message})" }
        val lib = File(applicationInfo.nativeLibraryDir, "libweizhijni.so")
        return buildString {
            appendLine("Weizhi 包检查（尚未加载引擎）")
            appendLine("nativeLibraryDir=${applicationInfo.nativeLibraryDir}")
            appendLine("libweizhijni.so exists=${lib.isFile} bytes=${if (lib.isFile) lib.length() else 0}")
            appendLine()
            append(stamp.trim())
            appendLine()
            appendLine()
            appendLine("java_and_so_same_build=NO：jniLibs 里的 SO 和本次 build-android 产物不是同一个文件。")
            appendLine("exists=false：这个 APK 只有 Java、没有 SO，一进主界面就会 UnsatisfiedLinkError 或直接退出。")
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadNative(): String {
        return try {
            System.loadLibrary("weizhijni")
            "loadLibrary 成功。若随后初始化类才退出，问题在 JNI_OnLoad / native 与 Java 方法不一致。"
        } catch (error: UnsatisfiedLinkError) {
            "UnsatisfiedLinkError: ${error.message}"
        } catch (error: Exception) {
            "${error.javaClass.name}: ${error.message}"
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun initEngineClass(): String {
        return try {
            val clazz = Class.forName("com.weizhi.WeizhiEngine")
            val natives = clazz.declaredMethods
                .filter { java.lang.reflect.Modifier.isNative(it.modifiers) }
                .joinToString("\n") { it.name }
            "类已初始化: ${clazz.name}\nnative 方法:\n$natives"
        } catch (error: ExceptionInInitializerError) {
            "ExceptionInInitializerError: ${error.cause?.message ?: error.message}"
        } catch (error: ClassNotFoundException) {
            "ClassNotFoundException: 这个包没有打进 weizhi Java（诊断包或未联编）。"
        } catch (error: UnsatisfiedLinkError) {
            "UnsatisfiedLinkError: ${error.message}"
        } catch (error: Exception) {
            "${error.javaClass.name}: ${error.message}"
        }
    }
}
