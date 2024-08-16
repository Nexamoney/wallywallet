package info.bitcoinunlimited.www.wally.ui.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

interface MusicViewModelI
{
    val state: StateFlow<MusicViewState>
    fun play(filePath: String)
    fun pause()
    fun stop()
}

data class MusicViewState(
  val isPlaying: Boolean = false,
  val currentTime: Int? = null,
  val duration: Int? = null
)

expect class MusicViewModel(musicViewState: MusicViewState, filePath: String) : ViewModel, MusicViewModelI
