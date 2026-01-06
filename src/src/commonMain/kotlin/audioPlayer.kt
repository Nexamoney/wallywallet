import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// composeApp/src/commonMain/kotlin/platform

// Audio played that plays system sounds based from the resources folder
expect class AudioPlayer() {
    /** Plays a sound by id (see soundResList) */
    fun playSound(id: Int)
    fun release()
}

val soundResList = listOf(
  "files/send_success.wav",
  "files/qr_scanned.wav",
  // "files/bell.mp3",
  // "files/beep7.mp3"
)

class AudioPlayerViewModel: ViewModel() {
    val audioPlayer = AudioPlayer()

    fun playScanQrSound() {
        viewModelScope.launch(Dispatchers.Main) {
            audioPlayer.playSound(1)
        }
    }
}