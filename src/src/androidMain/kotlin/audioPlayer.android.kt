import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import info.bitcoinunlimited.www.wally.ui.soundEnabled
import info.bitcoinunlimited.www.wally.wallyAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.nexa.libnexakotlin.GetLog
import wpw.src.generated.resources.Res

private val LogIt = GetLog("BU.wally.audioPlayer.android")

@OptIn(ExperimentalResourceApi::class)
actual class AudioPlayer {

    private val context: Context = wallyAndroidApp as Context

    private val soundPool: SoundPool by lazy {
        val audioAttributes = AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()

        SoundPool.Builder()
          .setMaxStreams(6)
          .setAudioAttributes(audioAttributes)
          .build()
    }

    // Preload sounds from composeResources/files/
    // https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html#interaction-with-other-libraries-and-resources
    private val soundIds: List<Int> by lazy {
        soundResList.map { path ->
            try {
                val uri = Res.getUri(path)
                val assetPath = uri
                  .removePrefix("file:///android_asset/")
                  .removePrefix("/")

                val assetFd = context.assets.openFd(assetPath)
                val soundId = soundPool.load(assetFd, 1)
                assetFd.close()
                soundId
            } catch (e: Exception) {
                LogIt.error(e.message ?: "Error loading soundIds")
                LogIt.error(e.stackTraceToString())
                0
            }
        }
    }

    actual suspend fun playSound(id: Int) {
        if (!soundEnabled.value) return

        withContext(Dispatchers.Main.immediate) {
            try {
                val soundId = soundIds.getOrNull(id) ?: return@withContext
                if (soundId != 0) {
                    soundPool.play(soundId, 1.0f, 1.0f, 0, 0, 1.0f)
                }
            } catch (e: Exception) {
                LogIt.error(e.message ?: "Error playing sound with id: $id")
                LogIt.error(e.stackTraceToString())
            }
        }
    }

    actual fun release() {
        soundPool.release()
    }
}