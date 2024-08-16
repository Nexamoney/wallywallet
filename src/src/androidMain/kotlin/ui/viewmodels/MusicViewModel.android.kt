package info.bitcoinunlimited.www.wally.ui.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/*
    This expect/actual class is initially only used in iosMain, and is eventually intended for all platforms.
    Hard TODOs that throw exceptions are fine here because MusicPlayerView+MusicViewModel is unused in `androidMain`
 */
actual class MusicViewModel actual constructor(musicViewState: MusicViewState, filePath: String) : ViewModel(), MusicViewModelI
{
    override val state: StateFlow<MusicViewState>
        get() = TODO("Not yet implemented")

    @Suppress("unused")
    override fun play(filePath: String)
    {
        TODO("Not yet implemented")
    }

    @Suppress("unused")
    override fun pause()
    {
        TODO("Not yet implemented")
    }

    @Suppress("unused")
    override fun stop()
    {
        TODO("Not yet implemented")
    }
}
