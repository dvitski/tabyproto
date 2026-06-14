package cc.dvitski.tabyproto.desktop

import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.RawAnimation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object AnimationResources {

    private val tempDir = File(System.getProperty("java.io.tmpdir"), "taby-desktop").also { it.mkdirs() }

    val durations: Map<RawAnimation, Long> get() = _durations
    private val _durations: MutableMap<RawAnimation, Long> = ConcurrentHashMap()

    fun preloadAll(animations: List<Animation>, scope: CoroutineScope) {
        animations.forEach { animation ->
            scope.launch(Dispatchers.IO) {
                when (animation) {
                    is Animation.Once -> {
                        videoPath(animation.raw.id)?.let { storeDuration(animation.raw, it) }
                    }
                    is Animation.Looping -> {
                        val intro = animation.intro
                        if (intro != null) videoPath(intro.id)?.let { storeDuration(intro, it) }
                        videoPath(animation.body.id)?.let { storeDuration(animation.body, it) }
                    }
                }
            }
        }
    }

    private fun storeDuration(raw: RawAnimation, path: String) {
        runCatching {
            FFmpegFrameGrabber(path).use { grabber ->
                grabber.start()
                val ms = grabber.lengthInTime / 1_000L  // microseconds → milliseconds
                grabber.stop()
                if (ms > 0) _durations[raw] = ms
            }
        }
    }

    suspend fun videoPath(animationId: String): String? = withContext(Dispatchers.IO) {
        val dest = File(tempDir, "$animationId.mp4")
        if (!dest.exists()) {
            val stream = AnimationResources::class.java.getResourceAsStream("/anim/$animationId.mp4")
                ?: return@withContext null
            try {
                stream.use { it.copyTo(dest.outputStream()) }
            } catch (_: Exception) {
                dest.delete()
                return@withContext null
            }
        }
        dest.absolutePath
    }
}
