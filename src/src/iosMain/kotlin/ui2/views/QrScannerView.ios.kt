package info.bitcoinunlimited.www.wally.ui2.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.nexa.libnexakotlin.GetLog
import platform.AVFoundation.*
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectZero
import platform.Foundation.*
import platform.QuartzCore.CALayer
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.*
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

private val LogIt = GetLog("QrCodeScannerScreen.ios")

enum class CodeType {
    Codabar, Code39, Code93, Code128, EAN8, EAN13, ITF, UPCE, Aztec, DataMatrix, PDF417, QR
}

enum class CameraPermissionStatus {
    Denied, Granted
}

interface CameraPermissionState {
    val status: CameraPermissionStatus
    fun requestCameraPermission()
    fun goToSettings()
}

fun List<CodeType>.toFormat(): List<AVMetadataObjectType> = map {
    when(it) {
        CodeType.Codabar -> AVMetadataObjectTypeCodabarCode
        CodeType.Code39 -> AVMetadataObjectTypeCode39Code
        CodeType.Code93 -> AVMetadataObjectTypeCode93Code
        CodeType.Code128 -> AVMetadataObjectTypeCode128Code
        CodeType.EAN8 -> AVMetadataObjectTypeEAN8Code
        CodeType.EAN13 -> AVMetadataObjectTypeEAN13Code
        CodeType.ITF -> AVMetadataObjectTypeITF14Code
        CodeType.UPCE -> AVMetadataObjectTypeUPCECode
        CodeType.Aztec -> AVMetadataObjectTypeAztecCode
        CodeType.DataMatrix -> AVMetadataObjectTypeDataMatrixCode
        CodeType.PDF417 -> AVMetadataObjectTypePDF417Code
        CodeType.QR -> AVMetadataObjectTypeQRCode
    }
}

@Composable
@Deprecated("Moved to https://github.com/kalinjul/EasyQRScan library for iOS")
actual fun QrScannerView(
  modifier: Modifier,
  onQrCodeScanned: (String) -> Unit
) {
    ScannerWithPermissions(modifier, onScanned = {onQrCodeScanned(it); true }, types = listOf(
        CodeType.QR
    ))
}

/**
 * Code Scanner with permission handling.
 *
 * @param types Code types to scan.
 * @param onScanned Called when a code was scanned. The given lambda should return true
 *                  if scanning was successful and scanning should be aborted.
 *                  Return false if scanning should continue.
 * @param permissionText Text to show if permission was denied.
 * @param openSettingsLabel Label to show on the "Go to settings" Button
 */
@Composable
fun ScannerWithPermissions(
    modifier: Modifier = Modifier,
    onScanned: (String) -> Boolean,
    types: List<CodeType>,
    permissionText: String = "Camera is required for QR Code scanning",
    openSettingsLabel: String = "Open Settings",
) {
    ScannerWithPermissions(
      modifier = modifier.clipToBounds(),
      onScanned = onScanned,
      types = types,
      permissionDeniedContent = { permissionState ->
          Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                modifier = Modifier.padding(6.dp),
                text = permissionText
              )
              Button(onClick = { permissionState.goToSettings() }) {
                  Text(openSettingsLabel)
              }
          }
      }
    )
}

/**
 * Code Scanner with permission handling.
 *
 * @param types Code types to scan.
 * @param onScanned Called when a code was scanned. The given lambda should return true
 *                  if scanning was successful and scanning should be aborted.
 *                  Return false if scanning should continue.
 * @param permissionDeniedContent Content to show if permission was denied.
 */
@Composable
fun ScannerWithPermissions(
    modifier: Modifier = Modifier,
    onScanned: (String) -> Boolean,
    types: List<CodeType>,
    permissionDeniedContent: @Composable (CameraPermissionState) -> Unit,
) {
    val permissionState = rememberCameraPermissionState()

    LaunchedEffect(Unit) {
        if (permissionState.status == CameraPermissionStatus.Denied)
        {
            permissionState.requestCameraPermission()
        }
    }

    if (permissionState.status == CameraPermissionStatus.Granted)
    {
        Scanner(modifier, types = types, onScanned = onScanned)
    }
    else
    {
        permissionDeniedContent(permissionState)
    }
}


@Composable
fun Scanner(
  modifier: Modifier,
  onScanned: (String) -> Boolean, // return true to abort scanning
  types: List<CodeType>
) {
    UiScannerView(
      modifier = modifier,
      onScanned = {
          onScanned(it)
      },
      allowedMetadataTypes = types.toFormat()
    )
}

@Composable
fun rememberCameraPermissionState(): CameraPermissionState
{
    return remember {
        IosMutableCameraPermissionState()
    }
}

abstract class MutableCameraPermissionState: CameraPermissionState
{
    override var status: CameraPermissionStatus by mutableStateOf(getCameraPermissionStatus())

}

class IosMutableCameraPermissionState: MutableCameraPermissionState() {
    override fun requestCameraPermission() {
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo)
        {
            this.status = getCameraPermissionStatus()
        }
    }

    override fun goToSettings() {
        val appSettingsUrl = NSURL(string = UIApplicationOpenSettingsURLString)
        if (UIApplication.sharedApplication.canOpenURL(appSettingsUrl))
        {
            UIApplication.sharedApplication.openURL(appSettingsUrl)
        }
    }
}

fun getCameraPermissionStatus(): CameraPermissionStatus
{
    val authorizationStatus = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
    return if (authorizationStatus == AVAuthorizationStatusAuthorized) CameraPermissionStatus.Granted else CameraPermissionStatus.Denied
}

@Composable
fun UiScannerView(
  modifier: Modifier = Modifier,
  // https://developer.apple.com/documentation/avfoundation/avmetadataobjecttype?language=objc
  allowedMetadataTypes: List<AVMetadataObjectType>,
  onScanned: (String) -> Boolean
) {
    val coordinator = remember {
        ScannerCameraCoordinator(
          onScanned = onScanned
        )
    }

    DisposableEffect(Unit) {
        val listener = OrientationListener { orientation ->
            coordinator.setCurrentOrientation(orientation)
        }

        listener.register()

        onDispose {
            listener.unregister()
        }
    }

    UIKitView<UIView>(
      modifier = modifier.fillMaxSize(),
      factory = {
          val previewContainer = ScannerPreviewView(coordinator)
          LogIt.info("Calling prepare")
          coordinator.prepare(previewContainer.layer, allowedMetadataTypes)
          previewContainer
      },
      properties = UIKitInteropProperties(
        isInteractive = true,
        isNativeAccessibilityEnabled = true,
      )
    )

//    DisposableEffect(Unit) {
//        onDispose {
//            // stop capture
//            coordinator.
//        }
//    }

}

@OptIn(ExperimentalForeignApi::class)
class ScannerPreviewView(private val coordinator: ScannerCameraCoordinator): UIView(frame = cValue { CGRectZero }) {
    @OptIn(ExperimentalForeignApi::class)
    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setValue(true, kCATransactionDisableActions)

        layer.setFrame(frame)
        coordinator.setFrame(frame)
        CATransaction.commit()
    }
}

@OptIn(ExperimentalForeignApi::class)
class ScannerCameraCoordinator(
  val onScanned: (String) -> Boolean
): AVCaptureMetadataOutputObjectsDelegateProtocol, NSObject() {

    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    lateinit var captureSession: AVCaptureSession

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    fun prepare(layer: CALayer, allowedMetadataTypes: List<AVMetadataObjectType>) {
        captureSession = AVCaptureSession()
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        if (device == null)
        {
            LogIt.warning("Device has no camera")
            return
        }

        LogIt.info("Initializing video input")
        val videoInput = memScoped {
            val error: ObjCObjectVar<NSError?> = alloc<ObjCObjectVar<NSError?>>()
            val videoInput = AVCaptureDeviceInput(device = device, error = error.ptr)
            if (error.value != null)
            {
                LogIt.error(error.value.toString())
                null
            }
            else
            {
                videoInput
            }
        }

        LogIt.info("Adding video input")
        if (videoInput != null && captureSession.canAddInput(videoInput))
        {
            captureSession.addInput(videoInput)
        } else {
            LogIt.error("Could not add input")
            return
        }

        val metadataOutput = AVCaptureMetadataOutput()

        LogIt.info("Adding metadata output")
        if (captureSession.canAddOutput(metadataOutput))
        {
            captureSession.addOutput(metadataOutput)

            metadataOutput.setMetadataObjectsDelegate(this, queue = dispatch_get_main_queue())
            metadataOutput.metadataObjectTypes = allowedMetadataTypes
        }
        else
        {
            LogIt.error("Could not add output")
            return
        }
        LogIt.info("Adding preview layer")
        previewLayer = AVCaptureVideoPreviewLayer(session = captureSession).also {
            it.frame = layer.bounds
            it.videoGravity = AVLayerVideoGravityResizeAspectFill
            LogIt.info("Set orientation")
            setCurrentOrientation(newOrientation = UIDevice.currentDevice.orientation)
            LogIt.info("Adding sublayer")
            layer.bounds.useContents {
                LogIt.info("Bounds: ${this.size.width}x${this.size.height}")

            }
            layer.frame.useContents {
                LogIt.info("Frame: ${this.size.width}x${this.size.height}")
            }
            layer.addSublayer(it)
        }

        LogIt.info("Launching capture session")
        GlobalScope.launch(Dispatchers.Default) {
            captureSession.startRunning()
        }
    }


    fun setCurrentOrientation(newOrientation: UIDeviceOrientation) {
        when(newOrientation) {
            UIDeviceOrientation.UIDeviceOrientationLandscapeLeft ->
                previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationLandscapeRight
            UIDeviceOrientation.UIDeviceOrientationLandscapeRight ->
                previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationLandscapeLeft
            UIDeviceOrientation.UIDeviceOrientationPortrait ->
                previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationPortrait
            UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown ->
                previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationPortraitUpsideDown
            else ->
                previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationPortrait
        }
    }

    override fun captureOutput(output: AVCaptureOutput, didOutputMetadataObjects: List<*>, fromConnection: AVCaptureConnection) {
        val metadataObject = didOutputMetadataObjects.firstOrNull() as? AVMetadataMachineReadableCodeObject
        metadataObject?.stringValue?.let { onFound(it) }
    }

    fun onFound(code: String) {
        captureSession.stopRunning()
        if (!onScanned(code))
        {
            GlobalScope.launch(Dispatchers.Default) {
                captureSession.startRunning()
            }
        }
    }

    fun setFrame(rect: CValue<CGRect>) {
        previewLayer?.setFrame(rect)
    }
}

@OptIn(ExperimentalForeignApi::class)
class OrientationListener(
  val orientationChanged: (UIDeviceOrientation) -> Unit
) : NSObject() {

    val notificationName = UIDeviceOrientationDidChangeNotification

    @Suppress("UNUSED_PARAMETER")
    @ObjCAction
    fun orientationDidChange(arg: NSNotification) {
        orientationChanged(UIDevice.currentDevice.orientation)
    }

    fun register() {
        NSNotificationCenter.defaultCenter.addObserver(
          observer = this,
          selector = NSSelectorFromString(
            OrientationListener::orientationDidChange.name + ":"
          ),
          name = notificationName,
          `object` = null
        )
    }

    fun unregister() {
        NSNotificationCenter.defaultCenter.removeObserver(
          observer = this,
          name = notificationName,
          `object` = null
        )
    }
}