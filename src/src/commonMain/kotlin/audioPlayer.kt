import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// composeApp/src/commonMain/kotlin/platform

// Audio played that plays system sounds based from the resources folder
expect class AudioPlayer() {
    /** Plays a sound by id (see soundResList) */
    suspend fun playSound(id: Int)
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
        viewModelScope.launch {
            audioPlayer.playSound(1)
        }
    }

    override fun onCleared()
    {
        audioPlayer.release()
        super.onCleared()
    }
}