package cc.dvitski.tabyproto.desktop

import cc.dvitski.tabyproto.Animation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object AnimationResources {

    private val tempDir = File(System.getProperty("java.io.tmpdir"), "taby-desktop").also { it.mkdirs() }

    fun preloadAll(animations: List<Animation>, scope: kotlinx.coroutines.CoroutineScope) {
        animations.forEach { animation ->
            scope.launch(Dispatchers.IO) { videoPath(animation.id) }
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
