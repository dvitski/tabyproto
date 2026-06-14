package cc.dvitski.tabyproto.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import cc.dvitski.tabyproto.Animation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.util.concurrent.ConcurrentHashMap

class ThumbnailCache {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cache = ConcurrentHashMap<Animation, ImageBitmap?>()

    private val _loadedCount = MutableStateFlow(0)
    val loadedCount: StateFlow<Int> = _loadedCount.asStateFlow()

    fun preloadAll(animations: List<Animation>) {
        animations.forEach { animation ->
            scope.launch {
                cache[animation] = extractFrame(animation.id)
                _loadedCount.update { it + 1 }
            }
        }
    }

    fun thumbnailFor(animation: Animation): ImageBitmap? = cache[animation]

    private fun extractFrame(animationId: String): ImageBitmap? {
        val resourcePath = "/anim/$animationId.mp4"
        val inputStream = ThumbnailCache::class.java.getResourceAsStream(resourcePath)
            ?: return null
        return try {
            FFmpegFrameGrabber(inputStream).use { grabber ->
                grabber.start()
                val frame = grabber.grabImage() ?: return null
                val bufferedImage = Java2DFrameConverter().convert(frame) ?: return null
                bufferedImage.toComposeImageBitmap()
            }
        } catch (_: Exception) {
            null
        } finally {
            inputStream.close()
        }
    }
}
