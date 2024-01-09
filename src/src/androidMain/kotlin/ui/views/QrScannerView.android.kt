package info.bitcoinunlimited.www.wally.ui.views


import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner

import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.*
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import org.nexa.libnexakotlin.GetLog
import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


/**
 * Open camera and scan QR-code with android
 */
@Composable
actual fun QrScannerView(modifier: Modifier, onQrCodeScanned: (String) -> Unit)
{
    Box(modifier = modifier) {
        QRCodeComposable(onQrCodeScanned)
        Text(
          text = i18n(S.scanPaymentQRcode),
          modifier = Modifier.background(Color.Black.copy(alpha = 0.3f))
            .align(Alignment.BottomCenter).padding(4.dp),
          style = MaterialTheme.typography.caption.copy(color = Color.White)
        )
    }
}

/**
 * Physically vibrates the phone
 */
@SuppressLint("ObsoleteSdkInt")
fun vibratePhone(context: Context)
{
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
    {
        vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
    }
    else
    {
        // Deprecated in API 26
        @Suppress("DEPRECATION")
        vibrator.vibrate(1000)
    }
}

/**
 * Content for android QR-code scanner view
 */
@Composable
fun QRCodeComposable(onQrCodeScanned: (String) -> Unit)
{
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var preview by remember { mutableStateOf<Preview?>(null) }

    var hasCamPermission by remember {
        mutableStateOf(
          ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
          ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission(),
      onResult = { granted ->
          hasCamPermission = granted
      }
    )
    LaunchedEffect(key1 = true) {
        launcher.launch(Manifest.permission.CAMERA)
    }
    if (hasCamPermission)
    {
        AndroidView(
          factory = { androidViewContext ->
              PreviewView(androidViewContext).apply {
                  this.scaleType = PreviewView.ScaleType.FILL_CENTER
                  layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                  )
                  implementationMode = PreviewView.ImplementationMode.COMPATIBLE
              }
          },
          modifier = Modifier.fillMaxSize(),
          update = { previewView ->
              val cameraSelector: CameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()
              val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
              val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
                ProcessCameraProvider.getInstance(context)

              cameraProviderFuture.addListener({
                  preview = Preview.Builder().build().also {
                      it.setSurfaceProvider(previewView.surfaceProvider)
                  }
                  val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                  val barcodeAnalyser = BarcodeAnalyser { barcodes ->
                      barcodes.forEach { barcode ->
                          barcode.rawValue?.let { barcodeValue ->
                              vibratePhone(context)
                              cameraProviderFuture.get().unbindAll()
                              onQrCodeScanned(barcodeValue)
                              return@BarcodeAnalyser
                          }
                      }
                  }
                  val imageAnalysis: ImageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, barcodeAnalyser)
                    }

                  try
                  {
                      cameraProvider.unbindAll()
                      cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                      )
                  }
                  catch (e: Exception)
                  {
                      e.printStackTrace()
                      Log.e("qr code", e.message ?: "")
                  }
              }, ContextCompat.getMainExecutor(context))
          }
        )
    }
}

/**
 * Analyze QR-code for android
 */
class BarcodeAnalyser(
  private val onBarcodeDetected: (barcodes: List<Barcode>) -> Unit,
) : ImageAnalysis.Analyzer
{
    private var lastAnalyzedTimeStamp = 0L

    /**
     * Detect QR code
     */
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy)
    {
        val currentTimestamp = System.currentTimeMillis()
        if (currentTimestamp - lastAnalyzedTimeStamp >= TimeUnit.SECONDS.toMillis(1))
        {
            image.image?.let { imageToAnalyze ->
                val options = BarcodeScannerOptions.Builder()
                  .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                  .build()
                val barcodeScanner = BarcodeScanning.getClient(options)
                val imageToProcess =
                  InputImage.fromMediaImage(imageToAnalyze, image.imageInfo.rotationDegrees)

                barcodeScanner.process(imageToProcess)
                  .addOnSuccessListener { barcodes ->
                      if (barcodes.isNotEmpty())
                      {
                          onBarcodeDetected(barcodes)
                      }
                  }
                  .addOnFailureListener { exception ->
                      exception.printStackTrace()
                  }
                  .addOnCompleteListener {
                      image.close()
                  }
            }
            lastAnalyzedTimeStamp = currentTimestamp
        }
        else
        {
            image.close()
        }
    }
}