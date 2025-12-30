import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import info.bitcoinunlimited.www.wally.applicationState
import info.bitcoinunlimited.www.wally.ui.soundEnabled
import info.bitcoinunlimited.www.wally.wallyAndroidApp
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.nexa.libnexakotlin.appContext
import wpw.src.generated.resources.Res

@ExperimentalResourceApi
actual class AudioPlayer {
    private val context = wallyAndroidApp as Context
    private val mediaPlayer = ExoPlayer.Builder(context).build()
    private val mediaItems = soundResList.map {
        MediaItem.fromUri(Res.getUri(it))
    }

    init {
        mediaPlayer.prepare()
    }

    @OptIn(ExperimentalResourceApi::class)
    actual fun playSound(id: Int) {
        if (soundEnabled.value)
        {
            mediaPlayer.setMediaItem(mediaItems[id])
            mediaPlayer.play()
        }
    }

    actual fun release() {
        mediaPlayer.release()
    }
}