package info.bitcoinunlimited.www.wally.ui.viewModels

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import info.bitcoinunlimited.www.wally.ui.views.MediaInfo
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import info.bitcoinunlimited.www.wally.ui.views.WallyBoringIconButton
import kotlinx.cinterop.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.nexa.libnexakotlin.GetLog
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.*

private val LogIt = GetLog("BU.wally.AudiManagerViewModel.ios")

data class MusicViewState(
  val isPlaying: Boolean = false,
  val currentTime: Int? = null,
  val duration: Int? = null
)

@OptIn(ExperimentalForeignApi::class)
class MusicViewModel (musicViewState: MusicViewState, val filePath: String): ViewModel() {
    private var audioPlayer: AVAudioPlayer? = null
    private var timer: NSTimer? = null

    /*
        the `state` variable can be read and observed in Compose.
        Keep the MutableStateFlow private to support unidirectional data flow.
        Use MusicViewModel to encapsulate setters to the _state variable
     */
    private val _state: MutableStateFlow<MusicViewState> = MutableStateFlow(musicViewState)
    val state = _state.asStateFlow()

    init {
        initPlayer()
    }

    fun initPlayer()
    {
        val filePathUrl = NSURL(string = filePath)
        try
        {
            audioPlayer = throwError { errorPointer: CPointer<ObjCObjectVar<NSError?>> ->
                AVAudioPlayer(contentsOfURL = filePathUrl, error = errorPointer)
            }
            _state.value = _state.value.copy(isPlaying = false, currentTime = 0, duration = audioPlayer?.duration?.toInt() ?: 0)
        }
        catch (e: NSErrorException) {
            LogIt.error("Error creating AVAudioPlayer(): $e")
        }
        catch (e: Exception) {
            LogIt.error("Error creating AVAudioPlayer(): $e")
        }
    }

    class NSErrorException(nsError: NSError): Exception(nsError.toString())

    private fun observeSongProgressWithInterval(interval: Double) {
        // Start a timer to observe the current time
        timer = NSTimer.scheduledTimerWithTimeInterval(interval, repeats = true) { hey ->
            audioPlayer?.let { player ->
                    _state.value = _state.value.copy(
                      currentTime = player.currentTime.toInt(),
                      isPlaying = audioPlayer?.isPlaying() ?: false
                    )
                }
            }
    }

    fun play(filePath: String) {
        val filePathUrl = NSURL(string = filePath)
        try {
            audioPlayer = throwError { errorPointer: CPointer<ObjCObjectVar<NSError?>> ->
                AVAudioPlayer(contentsOfURL = filePathUrl, error = errorPointer)
            }
            audioPlayer?.currentTime = 0.0
            audioPlayer?.prepareToPlay()
            audioPlayer?.play()
            observeSongProgressWithInterval(1.0)
            _state.value = _state.value.copy(isPlaying = true, currentTime = 0, duration = audioPlayer?.duration?.toInt() ?: 0)
        }
        catch (e: NSErrorException) {
            LogIt.error("Error creating AVAudioPlayer(): $e")
        }
        catch (e: Exception) {
            LogIt.error("Error creating AVAudioPlayer(): $e")
        }
    }

    fun pause()
    {
        audioPlayer?.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    fun stop()
    {
        audioPlayer?.stop()
        audioPlayer?.currentTime = 0.0
        _state.value = _state.value.copy(isPlaying = false, currentTime = 0)
    }

    /**
     * Source: https://github.com/guardian/multiplatform-ophan/blob/da4761a1a68ecfc7d182ae19a7de4528bcd19917/src/iosMain/kotlin/com/gu/ophan/FileRecordStore.kt#L116C1-L116C87
     * Helper method allowing any [NSError] error that occurs within [block] to be thrown as an exception.
     *
     * Apple's Objective-C libraries have their own error-handling idiom. Various functions accept a parameter called
     * `error` which is a pointer and these functions will make that pointer point to an [NSError] if something goes wrong.
     * In Kotlin, using a pointer in this way is cumbersome so this helper handles the heavy lifting and let's you write
     * code like this:
     *
     *     throwError { errorPointer ->
     *         NSData.dataWithContentsOfURL(url, 0.toULong(), errorPointer)
     *     }
     *
     * which either evaluates to the expected [NSData] returned by `dataWithContentsOfURL` or throws an [NSErrorException]
     * if something went wrong. This exception can be caught and handled in the normal way.
     */
    @OptIn(ExperimentalForeignApi::class)
    fun <T> throwError(block: (errorPointer: CPointer<ObjCObjectVar<NSError?>>) -> T): T {
        memScoped {
            val errorPointer: CPointer<ObjCObjectVar<NSError?>> = alloc<ObjCObjectVar<NSError?>>().ptr
            val result: T = block(errorPointer)
            val error: NSError? = errorPointer.pointed.value
            if (error != null) {
                throw NSErrorException(error)
            } else {
                return result
            }
        }
    }
}


@Composable
fun MusicView(filePath: String, wrapper: @Composable (MediaInfo, @Composable (Modifier?) -> Unit) -> Unit)
{
    val musicViewModel: MusicViewModel = viewModel { MusicViewModel(MusicViewState(), filePath) }
    val state = musicViewModel.state.collectAsState()
    val isPlaying = state.value.isPlaying
    val trackPosition = state.value.currentTime ?: "?"
    val duration = state.value.duration ?: "?"

    wrapper(MediaInfo(200, 200, false, true))
    {
        Column(
          modifier = Modifier.padding(8.dp).fillMaxSize(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
            ResImageView("icons/note.png", modifier = Modifier.weight(4f).fillMaxSize())

            // Track Position
            Text(
              text = "$trackPosition / $duration",
              fontSize = 16.sp,
              modifier = Modifier.weight(1f).fillMaxSize(),
              textAlign = TextAlign.Center
            )

            // Controls
            Row(
              modifier = Modifier.weight(2f).fillMaxSize(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
                WallyBoringIconButton("icons/music.stop.png", Modifier.weight(1f).padding(8.dp)) {
                    musicViewModel.stop()
                }
                if(isPlaying)
                    WallyBoringIconButton("icons/music.pause.png", Modifier.weight(1f).padding(8.dp)) {
                        musicViewModel.pause()
                    }
                else
                    WallyBoringIconButton("icons/music.play.png", Modifier.weight(1f).padding(8.dp)) {
                        musicViewModel.play(filePath)
                    }
            }
        }
    }
}
