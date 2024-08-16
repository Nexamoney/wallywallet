package info.bitcoinunlimited.www.wally.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import info.bitcoinunlimited.www.wally.ui.viewmodels.MusicViewModel
import info.bitcoinunlimited.www.wally.ui.viewmodels.MusicViewState
import info.bitcoinunlimited.www.wally.ui.views.ResImageView

@Composable
expect fun WallyTheme(
  darkTheme: Boolean,
  dynamicColor: Boolean,
  content: @Composable () -> Unit
)

/** Sets the title (at the native/platform level) if needed */
expect fun NativeTitle(title: String)

/** Sets/removes the native splashscreen, returning True if the platform HAS a native splashscreen */
expect fun NativeSplash(start: Boolean): Boolean

/** Call this when text entry has the focus -- this works around issues on some platforms in gaining knowledge of what the
 * system UX is doing.
 */
expect fun UxInTextEntry(boolean: Boolean)

data class MediaInfo(val width: Int, val height: Int,
  /** Is this a video? */
  val video:Boolean,
  /** If false, this platform cannot display this media.  A "can't display" icon is automatically substituted, or the wrapper can choose its
   * own error display by not calling the child composable. */
  val displayable: Boolean = true)

/** Provide a view for this piece of media.  If mediaData is non-null, use it as the media file contents.
 * However, still provide mediaUri (or at least dummy.ext) so that we can determine the media type from the file name within the Uri.
 * This composable is "unique" in that rather than providing a callback for contents, it provides a callback that allows you to wrap the final
 * media view.  This callback includes information about the piece of media being shown, so that you can create a custom wrapper based on the media.
 *
 * Your custom wrapper MUST call the passed composable to actually render the media.  You may pass a custom modifier.  If you pass null,
 * Modifier.fillMaxSize().background(Color.Transparent) is used.
 */
@Composable expect fun MpMediaView(mediaImage: ImageBitmap?, mediaData: ByteArray?, mediaUri: String?, autoplay: Boolean = false, hideMusicView: Boolean = false, wrapper: @Composable (MediaInfo, @Composable (Modifier?) -> Unit) -> Unit):Boolean


expect fun MpIcon(mediaUri: String, widthPx: Int, heightPx: Int): ImageBitmap

@Composable fun MusicView(filePath: String, wrapper: @Composable (MediaInfo, @Composable (Modifier?) -> Unit) -> Unit)
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
