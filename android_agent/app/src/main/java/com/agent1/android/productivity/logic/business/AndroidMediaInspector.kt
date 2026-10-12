package com.agent1.android.productivity.logic.business

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.agent1.javaagent.tool.media.ImageResizer
import com.agent1.javaagent.tool.media.MediaInfo
import com.agent1.javaagent.tool.media.MediaInspector
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Android 音视频探测与抽帧。按路径读写，不把文件读进 QuickJS。 */
class AndroidMediaInspector : MediaInspector {

    override fun probe(source: Path): MediaInfo {
        val size = Files.size(source)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(source.toString())
            return fromRetriever(retriever, size)
        } catch (e: RuntimeException) {
            return probeStillImage(source, size)
                ?: throw IllegalArgumentException("media_probe: 无法读取媒体: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            retriever.release()
        }
    }

    override fun extractFrame(source: Path, dest: Path, timeUs: Long): ImageResizer.ImageSize {
        val retriever = MediaMetadataRetriever()
        var frame: Bitmap? = null
        try {
            retriever.setDataSource(source.toString())
            frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: throw IllegalArgumentException("media_extract_frame: 没有画面（纯音频或时间超出时长）")
            dest.parent?.let { Files.createDirectories(it) }
            Files.newOutputStream(dest).use { stream ->
                if (!frame.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                    throw IOException("media_extract_frame: compress failed")
                }
            }
            return ImageResizer.ImageSize(frame.width, frame.height)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: RuntimeException) {
            throw IllegalArgumentException("media_extract_frame: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            frame?.recycle()
            retriever.release()
        }
    }

    private fun fromRetriever(retriever: MediaMetadataRetriever, sizeBytes: Long): MediaInfo {
        val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        val durationMs = longOrNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))
        val width = intOrNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
        val height = intOrNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
        val hasVideo = "yes".equals(
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO),
            ignoreCase = true,
        ) || (width != null && height != null)
        val hasAudio = "yes".equals(
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO),
            ignoreCase = true,
        )
        val kind = when {
            mime != null && mime.startsWith("image/") -> MediaInfo.KIND_IMAGE
            hasVideo -> MediaInfo.KIND_VIDEO
            hasAudio || (mime != null && mime.startsWith("audio/")) -> MediaInfo.KIND_AUDIO
            else -> MediaInfo.KIND_UNKNOWN
        }
        return MediaInfo(kind, sizeBytes, durationMs, width, height, mime)
    }

    private fun probeStillImage(source: Path, sizeBytes: Long): MediaInfo? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.toString(), bounds)
        if (bounds.outWidth < 1 || bounds.outHeight < 1) {
            return null
        }
        return MediaInfo(
            MediaInfo.KIND_IMAGE,
            sizeBytes,
            null,
            bounds.outWidth,
            bounds.outHeight,
            bounds.outMimeType,
        )
    }

    private fun longOrNull(raw: String?): Long? {
        val value = raw?.toLongOrNull() ?: return null
        return if (value < 0) null else value
    }

    private fun intOrNull(raw: String?): Int? {
        val value = raw?.toIntOrNull() ?: return null
        return if (value < 1) null else value
    }

    private companion object {
        const val JPEG_QUALITY = 85
    }
}
