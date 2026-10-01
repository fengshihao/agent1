package com.agent1.android.productivity.ui.view

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.decode.ImageSource
import coil.fetch.SourceResult
import coil.request.Options
import com.agent1.android.productivity.logic.business.WorkspaceImageBytes

/** 把 webview_exec 落盘的 Base64 文本解码成位图。二进制图片仍走 Coil 默认解码器。 */
class WorkspaceBase64ImageDecoder(
    private val source: ImageSource,
    private val options: Options,
) : Decoder {

    override suspend fun decode(): DecodeResult {
        val path = source.file().toString()
        val bytes = WorkspaceImageBytes.decodeBase64Payload(path)
            ?: error("不是 Base64 图片")
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Base64 内容不是有效图片")
        return DecodeResult(
            drawable = BitmapDrawable(options.context.resources, bitmap),
            isSampled = false,
        )
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceResult, options: Options, imageLoader: ImageLoader): Decoder? {
            val path = runCatching { result.source.file().toString() }.getOrNull() ?: return null
            if (!WorkspaceImageBytes.isBase64ImageText(path)) {
                return null
            }
            return WorkspaceBase64ImageDecoder(result.source, options)
        }
    }
}
