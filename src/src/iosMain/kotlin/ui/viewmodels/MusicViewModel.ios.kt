package info.bitcoinunlimited.www.wally.ui.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.cinterop.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.nexa.libnexakotlin.GetLog
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.*

private val LogIt = GetLog("BU.wally.AudiManagerViewModel.ios")

@OptIn(ExperimentalForeignApi::class)
actual class MusicViewModel actual constructor(musicViewState: MusicViewState, val filePath: String): ViewModel(), MusicViewModelI {
    private var audioPlayer: AVAudioPlayer? = null
    private var timer: NSTimer? = null

    /*
        the `state` variable can be read and observed in Compose.
        Keep the MutableStateFlow private to support unidirectional data flow.
        Use MusicViewModel to encapsulate setters to the _state variable
     */
    private val _state: MutableStateFlow<MusicViewState> = MutableStateFlow(musicViewState)
    override val state = _state.asStateFlow()

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

    override fun play(filePath: String) {
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

    override fun pause()
    {
        audioPlayer?.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    override fun stop()
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
