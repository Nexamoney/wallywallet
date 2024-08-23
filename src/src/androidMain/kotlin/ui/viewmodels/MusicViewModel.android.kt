package info.bitcoinunlimited.www.wally.ui.viewmodels

import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
    This expect/actual class is initially only used in iosMain, and is eventually intended for all platforms.
    Hard TODOs that throw exceptions are fine here because MusicPlayerView+MusicViewModel is unused in `androidMain`
 */
actual class MusicViewModel actual constructor(musicViewState: MusicViewState, val filePath: String) : ViewModel(), MusicViewModelI
{

    private val mediaPlayer: MediaPlayer

    /*
        the `state` variable can be read and observed in Compose.
        Keep the MutableStateFlow private to support unidirectional data flow.
        Use MusicViewModel to encapsulate setters to the _state variable
     */
    private val _state: MutableStateFlow<MusicViewState> = MutableStateFlow(musicViewState)
    override val state = _state.asStateFlow()

    init {
        mediaPlayer = MediaPlayer().apply {
            setDataSource(filePath)
            prepare()
        }
    }

    @Suppress("unused")
    override fun play(filePath: String)
    {
        mediaPlayer.start()
    }

    override fun pause()
    {
        mediaPlayer.pause()
    }

    override fun stop()
    {
        mediaPlayer.stop()
    }
}
