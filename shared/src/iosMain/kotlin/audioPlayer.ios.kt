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

    // kept after the first play: prepareToPlay() holds the decoded buffer
    private val players: MutableList<AVAudioPlayer?> = MutableList(mediaItems.size) { null }

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun playSound(id: Int) {
        if (!soundEnabled.value) return
        if (id !in players.indices) return

        val player = players[id] ?: prepare(id)?.also { players[id] = it } ?: return
        player.currentTime = 0.0
        player.play()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun prepare(id: Int): AVAudioPlayer? {
        val url = mediaItems[id] ?: return null
        val p = AVAudioPlayer(url, error = null)
        p.prepareToPlay()
        return p
    }

    actual fun release() {
        for (i in players.indices)
        {
            players[i]?.stop()
            players[i] = null
        }
    }
}
