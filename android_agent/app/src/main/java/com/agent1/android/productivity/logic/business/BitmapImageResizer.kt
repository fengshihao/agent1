package com.agent1.android.productivity.logic.business

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.agent1.javaagent.tool.media.ImageFit
import com.agent1.javaagent.tool.media.ImageResizer
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Android 图片缩放：Bitmap 解码与 JPEG 压缩，不经过 QuickJS，也不调用微智。 */
class BitmapImageResizer : ImageResizer {

    override fun resize(source: Path, dest: Path, maxEdge: Int): ImageResizer.ImageSize {
        ImageFit.checkMaxEdge(maxEdge)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.toString(), bounds)
        if (bounds.outWidth < 1 || bounds.outHeight < 1) {
            throw IllegalArgumentException("bad argument: media_resize: not an image")
        }
        val fitted = ImageFit.fit(bounds.outWidth, bounds.outHeight, maxEdge)
        val decoded = BitmapFactory.decodeFile(
            source.toString(),
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, fitted[0], fitted[1])
            },
        ) ?: throw IllegalArgumentException("bad argument: media_resize: not an image")
        val scaled = if (decoded.width == fitted[0] && decoded.height == fitted[1]) {
            decoded
        } else {
            Bitmap.createScaledBitmap(decoded, fitted[0], fitted[1], true).also { created ->
                if (created !== decoded) decoded.recycle()
            }
        }
        try {
            dest.parent?.let { Files.createDirectories(it) }
            Files.newOutputStream(dest).use { stream ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                    throw IOException("media_resize: compress failed")
                }
            }
        } finally {
            scaled.recycle()
        }
        return ImageResizer.ImageSize(fitted[0], fitted[1])
    }

    private fun sampleSize(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth && sourceHeight / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        return sample
    }

    private companion object {
        const val JPEG_QUALITY = 85
    }
}
