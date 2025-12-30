import info.bitcoinunlimited.www.wally.ui.soundEnabled
import kotlinx.cinterop.ExperimentalForeignApi
import org.jetbrains.compose.resources.ExperimentalResourceApi
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSURL
import wpw.src.generated.resources.Res

@OptIn(ExperimentalResourceApi::class)
actual class AudioPlayer {
    private val mediaItems = soundResList.map { path ->
        val uri = Res.getUri(path)
        NSURL.URLWithString(URLString = uri)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun playSound(id: Int) {
        if (soundEnabled.value)
        {
            val avAudioPlayer = AVAudioPlayer(mediaItems[id]!!, error = null)
            avAudioPlayer.prepareToPlay()
            avAudioPlayer.play()
        }
    }

    actual fun release() {}
}