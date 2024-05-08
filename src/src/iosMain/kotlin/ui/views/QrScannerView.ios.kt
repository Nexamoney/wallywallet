package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.platform.testTag
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.coroutines.*
import org.nexa.libnexakotlin.GetLog
import platform.AVFoundation.*
import platform.AVFoundation.AVCaptureDeviceDiscoverySession.Companion.discoverySessionWithDeviceTypes
import platform.AVFoundation.AVCaptureDeviceInput.Companion.deviceInputWithDevice
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.AudioToolbox.kSystemSoundID_Vibrate
import platform.CoreGraphics.CGRect
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSelectorFromString
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue
import org.nexa.threads.millisleep
import info.bitcoinunlimited.www.wally.*

private sealed interface CameraAccess {
    object Undefined : CameraAccess
    object Denied : CameraAccess
    object Authorized : CameraAccess
}

private val deviceTypes = listOf(
  AVCaptureDeviceTypeBuiltInWideAngleCamera,
  AVCaptureDeviceTypeBuiltInDualWideCamera,
  AVCaptureDeviceTypeBuiltInDualCamera,
  AVCaptureDeviceTypeBuiltInUltraWideCamera,
  AVCaptureDeviceTypeBuiltInDuoCamera
)

private val LogIt = GetLog("QrCodeScannerScreen.ios")

@Composable
actual fun QrScannerView(
  modifier: Modifier,
  onQrCodeScanned: (String) -> Unit
) {
    var cameraAccess: CameraAccess by remember { mutableStateOf(CameraAccess.Undefined) }
    LaunchedEffect(Unit) {
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> {
                cameraAccess = CameraAccess.Authorized
            }

            AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> {
                cameraAccess = CameraAccess.Denied
            }

            AVAuthorizationStatusNotDetermined -> {
                AVCaptureDevice.requestAccessForMediaType(
                  mediaType = AVMediaTypeVideo
                ) { success ->
                    cameraAccess = if (success) CameraAccess.Authorized else CameraAccess.Denied
                }
            }
        }
    }
    Box(
      modifier.fillMaxSize().background(Color.Black),
      contentAlignment = Alignment.Center
    ) {
        when (cameraAccess) {
            CameraAccess.Undefined -> {
                // Waiting for the user to accept permission
            }
            CameraAccess.Denied -> {
                Text("Camera access denied", color = Color.White)
            }
            CameraAccess.Authorized -> {
                AuthorizedCamera(onQrCodeScanned)
            }
            else ->
            {
                LogIt.info("Unexpected camera access issue $cameraAccess")
            }
        }
    }
}

@Composable
private fun BoxScope.AuthorizedCamera(onQrCodeScanned: (String) -> Unit) {
    val camera: AVCaptureDevice? = remember {
        discoverySessionWithDeviceTypes(
          deviceTypes = deviceTypes,
          mediaType = AVMediaTypeVideo,
          position = AVCaptureDevicePositionBack,
        ).devices.firstOrNull() as? AVCaptureDevice
    }
    if (camera != null)
    {
        RealDeviceCamera(camera, onQrCodeScanned)
    }
    else
    {
        Text(
          """
            Camera is not available on simulator.
            Please try to run on a real iOS device.
        """.trimIndent(), color = Color.White
        )
    }
}

private val coExceptionHandler = CoroutineExceptionHandler() { ctx, err ->
    LogIt.error(ctx.toString())
    LogIt.error(err.message ?: err.toString())
}
@OptIn(ExperimentalForeignApi::class)
@Composable
private fun RealDeviceCamera(camera: AVCaptureDevice, onQrCodeScanned: (String) -> Unit)
{
    val capturePhotoOutput = remember { AVCapturePhotoOutput() }
    var actualOrientation by remember {
        mutableStateOf(
          AVCaptureVideoOrientationPortrait
        )
    }

    LogIt.info("RealDeviceCamera")

    val captureSession: AVCaptureSession = remember {
        AVCaptureSession().also { captureSession ->
            if (true)
            {
                //LogIt.info("AvCapture also")
                captureSession.sessionPreset = AVCaptureSessionPresetPhoto
                val captureDeviceInput: AVCaptureDeviceInput = deviceInputWithDevice(device = camera, error = null)!!
                captureSession.addInput(captureDeviceInput)
                captureSession.addOutput(capturePhotoOutput)

                // Initialize an AVCaptureMetadataOutput object and set it as the output device to the capture session.
                val metadataOutput = AVCaptureMetadataOutput()
                if (captureSession.canAddOutput(metadataOutput))
                {
                    //LogIt.info("adding output")
                    // Set delegate and use default dispatch queue to execute the call back
                    // fixed with https://youtrack.jetbrains.com/issue/KT-45755/iOS-delegate-protocol-is-empty
                    //metadataOutput.metadataObjectTypes = metadataOutput.availableMetadataObjectTypes()
                    captureSession.addOutput(metadataOutput)
                    metadataOutput.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
                    metadataOutput.setMetadataObjectsDelegate(objectsDelegate = object : NSObject(),
                                                                                         AVCaptureMetadataOutputObjectsDelegateProtocol
                    {
                        override fun captureOutput(output: AVCaptureOutput, didOutputMetadataObjects: List<*>, fromConnection: AVCaptureConnection)
                        {
                            for (mo in didOutputMetadataObjects)
                            {
                                if (mo != null)
                                {
                                    val readableObject = mo as? AVMetadataMachineReadableCodeObject
                                    if (readableObject != null)
                                    {
                                        val code = readableObject.stringValue
                                        if (code != null)
                                        {
                                            AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
                                            LogIt.info("QR code scanned: $code")
                                            onQrCodeScanned(code)
                                            captureSession.stopRunning()
                                        }
                                        else
                                        {
                                            LogIt.info("scanned $readableObject is not a string")
                                        }
                                    }
                                    else
                                    {
                                        LogIt.info("scanned $readableObject")
                                    }
                                }
                            }
                        }
                    }, queue = dispatch_get_main_queue())
                
                }
                else
                {
                    LogIt.info("QR cap unavailable")
                    throw UiUnavailableException()
                }
            }
        }
    }
    val cameraPreviewLayer = remember {
        AVCaptureVideoPreviewLayer(session = captureSession)
    }

    DisposableEffect(Unit) {
        class OrientationListener : NSObject()
        {
            @Suppress("UNUSED_PARAMETER")
            @ObjCAction
            fun orientationDidChange(arg: NSNotification)
            {
                val cameraConnection = cameraPreviewLayer.connection
                if (cameraConnection != null)
                {
                    LogIt.info("QR scan orientation change")
                    actualOrientation = when (UIDevice.currentDevice.orientation)
                    {
                        UIDeviceOrientation.UIDeviceOrientationPortrait ->
                            AVCaptureVideoOrientationPortrait

                        UIDeviceOrientation.UIDeviceOrientationLandscapeLeft ->
                            AVCaptureVideoOrientationLandscapeRight

                        UIDeviceOrientation.UIDeviceOrientationLandscapeRight ->
                            AVCaptureVideoOrientationLandscapeLeft

                        UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown ->
                            AVCaptureVideoOrientationPortrait

                        else -> cameraConnection.videoOrientation
                    }
                    cameraConnection.videoOrientation = actualOrientation
                }
                capturePhotoOutput.connectionWithMediaType(AVMediaTypeVideo)
                  ?.videoOrientation = actualOrientation
            }
        }

        val listener = OrientationListener()
        val notificationName = platform.UIKit.UIDeviceOrientationDidChangeNotification
        NSNotificationCenter.defaultCenter.addObserver(
          observer = listener,
          selector = NSSelectorFromString(
            OrientationListener::orientationDidChange.name + ":"
          ),
          name = notificationName,
          `object` = null
        )
        onDispose {
            LogIt.info("dispose")
            NSNotificationCenter.defaultCenter.removeObserver(
              observer = listener,
              name = notificationName,
              `object` = null
            )
        }
    }
    UIKitView(
      modifier = Modifier.fillMaxSize(),
      background = Color.Black,
      factory = {
          val cameraContainer = UIView()
          cameraContainer.layer.addSublayer(cameraPreviewLayer)
          cameraPreviewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
          wallyApp!!.later {
              //LogIt.info("cap session started")
              //millisleep(250U)
              captureSession.startRunning()
          }
          cameraContainer
      },
      onResize = { view: UIView, rect: CValue<CGRect> ->
          //LogIt.info("QR scan resize")
          CATransaction.begin()
          CATransaction.setValue(true, kCATransactionDisableActions)
          view.layer.setFrame(rect)
          cameraPreviewLayer.setFrame(rect)
          CATransaction.commit()
      },
    )
}
