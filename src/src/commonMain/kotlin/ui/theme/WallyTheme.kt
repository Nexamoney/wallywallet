package info.bitcoinunlimited.www.wally.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

@Composable
expect fun WallyTheme(
  darkTheme: Boolean,
  dynamicColor: Boolean,
  content: @Composable () -> Unit
)

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
@Composable expect fun MpMediaView(mediaData: ByteArray?, mediaUri: String?, wrapper: @Composable (MediaInfo, @Composable (Modifier?) -> Unit) -> Unit):Boolean


expect fun MpIcon(mediaUri: String, widthPx: Int, heightPx: Int): ImageBitmap