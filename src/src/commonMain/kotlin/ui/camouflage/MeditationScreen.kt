package ui.camouflage

import AudioPlayer
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import info.bitcoinunlimited.www.wally.CAMOUFLAGE_MEDITATION_INTERVAL
import info.bitcoinunlimited.www.wally.KotlinTarget
import info.bitcoinunlimited.www.wally.PREFERENCE_FILE_NAME
import info.bitcoinunlimited.www.wally.PREF_MODE_PRIVATE
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.SOUND_ENABLED_PREF
import info.bitcoinunlimited.www.wally.TEST_PREF
import info.bitcoinunlimited.www.wally.getSharedPreferences
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.platform
import info.bitcoinunlimited.www.wally.ui.Camouflage
import info.bitcoinunlimited.www.wally.ui.camouflageTemp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import wpw.src.generated.resources.Res
import wpw.src.generated.resources.meditating_person
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds


// The i18n(..) implementation breaks in @Preview so that is why strings are defined as such
data class MeditationDrawerStrings(
  val meditate: String,
  val interval: String,
  val settings: String,
  val version: String,
  val start: String,
  val meditation: String,
  val meditating: String,
  val mediationComplete: String,
  val okay: String,
  val cancel: String,
  val minutes: String
)

enum class MeditationProgress
{
    PREPARING,
    ONGOING,
    ENDED
}

data class MeditationUiState(
  val sound: Boolean = true,
  val intervalSound: Boolean = true,
  val strings: MeditationDrawerStrings = MeditationDrawerStrings(
    meditate = "Meditate",
    interval = "1 min interval sound",
    settings = "Settings",
    version = "Version: 1.0.3 (build 156)",
    start = "Start",
    meditation = "meditation",
    meditating = "Meditating...",
    mediationComplete = "Meditation complete!",
    okay = "Okay",
    cancel = "Cancel",
    minutes = "Minutes"
  ),
  val durationMinutes: String = "20",
  val timeLeft: String = "00:00",
  val progress: MeditationProgress = MeditationProgress.PREPARING
)

abstract class MeditationViewModel: ViewModel()
{
    private val _uiState = MutableStateFlow(MeditationUiState())
    open val uiState: StateFlow<MeditationUiState> = _uiState
    abstract fun toggleIntervalSound(enabled: Boolean)
    abstract fun setDuration(minutes: String)
    abstract fun startMeditation()
    abstract fun cancelMeditation()
    abstract fun disableCamouflageTemp()
}

class MeditationViewModelReal: MeditationViewModel()
{
    private val sharedPref = getSharedPreferences(TEST_PREF + PREFERENCE_FILE_NAME, PREF_MODE_PRIVATE)
    private val _uiState = MutableStateFlow(
      MeditationUiState(
        sound = sharedPref.getBoolean(SOUND_ENABLED_PREF, true),
        intervalSound = sharedPref.getBoolean(CAMOUFLAGE_MEDITATION_INTERVAL, true),
        strings = MeditationDrawerStrings(
          meditate = i18n(S.Meditate),
          interval = i18n(S.OneMinuteIntervalSound),
          settings = i18n(S.settings),
          version = i18n(S.MeditationVersion),
          start = i18n(S.Start),
          meditation = i18n(S.Meditation),
          meditating = i18n(S.Meditating),
          mediationComplete = i18n(S.MediationComplete),
          okay = i18n(S.Okay),
          cancel = i18n(S.cancel),
          minutes = i18n(S.Minutes)
        ),
      )
    )
    override val uiState: StateFlow<MeditationUiState> = _uiState
    private var countdownJob: Job? = null
    private val audioPlayer: AudioPlayer = AudioPlayer()

    override fun toggleIntervalSound(enabled: Boolean)
    {
        _uiState.update { it.copy(intervalSound = enabled) }
        sharedPref.edit().putBoolean(CAMOUFLAGE_MEDITATION_INTERVAL, enabled).commit()
    }

    override fun setDuration(minutes: String)
    {
        _uiState.update { it.copy(durationMinutes = minutes) }
    }

    sealed class CountdownTick {
        data class Time(val display: String) : CountdownTick()
        object MinutePassed : CountdownTick()
    }

    private fun countdownFlow(minutes: Int): Flow<CountdownTick> = flow {
        if (minutes <= 0) {
            emit(CountdownTick.Time("00:00"))
            return@flow
        }

        var secondsLeft = minutes * 60

        val min = secondsLeft / 60
        val sec = secondsLeft % 60
        emit(CountdownTick.Time("${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"))

        var previousMinute = min

        while (secondsLeft > 0) {
            delay(1.seconds)
            secondsLeft--

            val currentMin = secondsLeft / 60
            val currentSec = secondsLeft % 60
            emit(CountdownTick.Time("${currentMin.toString().padStart(2, '0')}:${currentSec.toString().padStart(2, '0')}"))

            if ((currentMin + 1) < previousMinute) {
                if (secondsLeft > 0) {
                    emit(CountdownTick.MinutePassed)
                }
                previousMinute = currentMin
            }
        }
    }.flowOn(Dispatchers.Default)

    override fun startMeditation() {
        val duration = _uiState.value.durationMinutes.toIntOrNull() ?: 0

        countdownJob?.cancel()

        if (_uiState.value.sound) {
            viewModelScope.launch(Dispatchers.Main) {
                audioPlayer.playSound(1)           // starting sound
            }
        }

        countdownJob = viewModelScope.launch {
            countdownFlow(duration)
              .collect { tick ->
                  when (tick) {
                      is CountdownTick.Time -> {
                          if (tick.display == "00:00")
                          {
                              if (_uiState.value.sound)
                                  viewModelScope.launch(Dispatchers.Main) {
                                      audioPlayer.playSound(3)
                                  }
                              _uiState.update { it.copy(timeLeft = "00:00", progress = MeditationProgress.ENDED) }
                          }
                          else
                          {
                              _uiState.update { it.copy(timeLeft = tick.display, progress = MeditationProgress.ONGOING) }
                          }
                      }

                      CountdownTick.MinutePassed -> {
                          if (_uiState.value.sound && _uiState.value.intervalSound) {
                              // Important: play on Main thread
                              launch(Dispatchers.Main) {
                                  audioPlayer.playSound(2)
                              }
                          }
                      }
                  }
              }
        }
    }

    override fun cancelMeditation()
    {
        countdownJob?.cancel()
        _uiState.update { it.copy(timeLeft = "00:00", progress = MeditationProgress.PREPARING) }
        countdownJob = null
    }

    override fun disableCamouflageTemp()
    {
        camouflageTemp.value = Camouflage.Disabled
    }
}

class MeditationViewModelFake: MeditationViewModel()
{
    private val _uiState = MutableStateFlow(MeditationUiState())
    override val uiState: StateFlow<MeditationUiState> = _uiState

    override fun toggleIntervalSound(enabled: Boolean)
    {
        _uiState.update { it.copy(intervalSound = enabled) }
    }

    override fun setDuration(minutes: String)
    {
        _uiState.update { it.copy(durationMinutes = minutes) }
    }

    override fun startMeditation()
    {
        val duration = _uiState.value.durationMinutes.toIntOrNull() ?: 0
        val min = duration.toString().padStart(2, '0')
        _uiState.update { it.copy(timeLeft = "$min:00", progress = MeditationProgress.ONGOING) }
    }

    override fun cancelMeditation()
    {
        _uiState.update { it.copy(timeLeft = "00:00", progress = MeditationProgress.PREPARING) }
    }

    override fun disableCamouflageTemp()
    {
    }

    fun completeMeditation()
    {
        _uiState.update { it.copy(timeLeft = "00:00", progress = MeditationProgress.ENDED) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeditationScreen(viewModel: MeditationViewModel = if (LocalInspectionMode.current) MeditationViewModelFake() else viewModel { MeditationViewModelReal() })
{
    val ui = viewModel.uiState.collectAsState().value
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    ModalNavigationDrawer(
      drawerState = drawerState,
      drawerContent = {
          ModalDrawerSheet(
            modifier = Modifier.width(280.dp)
          ) {
              DrawerHeadline(title = ui.strings.settings, modifier = Modifier.padding(16.dp))
              HorizontalDivider()

              Spacer(Modifier.height(8.dp))

              SettingsSwitchItem(ui.strings.interval, ui.intervalSound) { viewModel.toggleIntervalSound(it) }

              Spacer(Modifier.weight(1f))

              AppVersionFooter(ui.strings.version, viewModel, Modifier.fillMaxWidth().padding(16.dp))
          }
      }
    ) {
        when (ui.progress)
        {
            MeditationProgress.PREPARING -> MeditationPreparationView(
              viewModel,
              openDrawer = { scope.launch { drawerState.open() } }
            )
            MeditationProgress.ONGOING -> MeditationOngoingView(
              viewModel,
              openDrawer = { scope.launch { drawerState.open() } }
            )
            MeditationProgress.ENDED -> MeditationCompleteView(
              viewModel,
              openDrawer = { scope.launch { drawerState.open() } }
            )
        }
    }
}

@Composable
fun DrawerHeadline(
  title: String,
  modifier: Modifier = Modifier
)
{
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      modifier = modifier
    )
}

@Composable
fun SettingsSwitchItem(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
)
{
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { onCheckedChange(!checked) }
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
          text = label,
          style = MaterialTheme.typography.bodyLarge
        )
        Switch(
          checked = checked,
          onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
fun AppVersionFooter(
  version: String,
  viewModel: MeditationViewModel,
  modifier: Modifier = Modifier
)
{
    var clicks by remember { mutableStateOf(0) }

    Column(
      modifier = modifier,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
          text = version,
          modifier = Modifier.clickable {
              clicks++
              if (clicks == 5)
              {
                  viewModel.disableCamouflageTemp()
                  clicks = 0
              }
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview
@Composable
// https://youtrack.jetbrains.com/projects/KMT/issues/KMT-2076/Preview-in-Compose-1.10.0-fails
fun MeditationScreenPreview()
{
    MeditationScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeditationPreparationView(
  viewModel: MeditationViewModel,
  openDrawer : () -> Unit
)
{
    val ui = viewModel.uiState.collectAsState().value
    val focusManager = LocalFocusManager.current
    val starYellow = Color(0xFFFFE082)

    Scaffold(
      topBar = {
          TopAppBar(
            title = { Text(i18n(S.Meditate)) },
            navigationIcon = {
                IconButton(onClick = { openDrawer() }) {
                    Icon(Icons.Default.Menu, "Open menu")
                }
            }
          )
      }
    ) { padding ->
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentAlignment = Alignment.Center
        ) {
            Image(
              painter = painterResource(Res.drawable.meditating_person),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize().clickable {  focusManager.clearFocus()  }
            )

            Column(
              modifier = Modifier.align(Alignment.Center)
                .clickable { viewModel.startMeditation() }
            ) {
                ShiningStarGlow(
                  Modifier
                    .size(100.dp)
                    .align(Alignment.CenterHorizontally)
                    .clickable { viewModel.startMeditation() },
                  color = starYellow
                )
                TextButton(
                  onClick = { viewModel.startMeditation() },
                  modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Column(
                      horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                          text = ui.strings.start,
                          color = starYellow,
                          style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                          text = ui.strings.meditation,
                          color = starYellow,
                          style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
            }

            Column(
              modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                      value = ui.durationMinutes,
                      onValueChange = {
                          viewModel.setDuration(it)
                      },
                      singleLine = true,
                      label = {
                          Text(
                            text = "minutes",
                            style = MaterialTheme.typography.labelMedium
                          )
                      },
                      keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                      ),
                      colors = TextFieldDefaults.colors().copy(
                        focusedTextColor = starYellow,
                        unfocusedTextColor = starYellow,
                        focusedLabelColor = starYellow,
                        unfocusedLabelColor = starYellow,
                        cursorColor = starYellow,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        focusedIndicatorColor = starYellow,
                        unfocusedIndicatorColor = starYellow,
                      )
                    )
                    if (platform().target == KotlinTarget.iOS)
                        TextButton(
                          onClick = { focusManager.clearFocus() }
                        ) {
                            Text(
                              text = "Done",
                              style = MaterialTheme.typography.bodyMedium,
                              color = starYellow
                            )
                        }
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}


@Composable
private fun rememberRayLengthAnimations(
  count: Int,
  baseDuration: Int,
  staggerMs: Int = 400,
): List<Float> {
    val infinite = rememberInfiniteTransition(label = "rays")

    return List(count) { index ->
        infinite.animateFloat(
          initialValue = 0.4f,
          targetValue = 1.1f,
          animationSpec = infiniteRepeatable(
            animation = tween(
              durationMillis = baseDuration + index * staggerMs,
              easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
          ),
          label = "ray_length_$index"
        ).value
    }
}

@Composable
fun ShiningStarGlow(
  modifier: Modifier,
  color: Color,
  coreSize: Float = 12f,
  rayCount: Int = 32,
  maxRayLengthFraction: Float = 0.4f,
  pulseDurationMs: Int = 3800,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "star-shine")
    // Static per-ray variations – computed once
    val rayJitter = remember(rayCount) {
        List(rayCount) { Random.nextFloat() * 18f - 9f }   // ±9 degrees jitter
    }

    val rayBaseMultipliers = remember(rayCount) {
        List(rayCount) { 0.75f + Random.nextFloat() * 0.5f }  // 0.75 → 1.25 range
    }

    val rayThicknessVariation = remember(rayCount) {
        List(rayCount) { Random.nextFloat() }  // 0.0 → 1.0 → affects stroke width
    }

    val rayFadeStarts = remember(rayCount) {
        List(rayCount) { 0.38f + Random.nextFloat() * 0.22f }  // 0.38 → 0.60 fade start
    }

    val breathScale by infiniteTransition.animateFloat(
      initialValue = 0.94f,
      targetValue = 1.08f,
      animationSpec = infiniteRepeatable(
        animation = tween(pulseDurationMs, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "breath"
    )

    val glowAlpha by infiniteTransition.animateFloat(
      initialValue = 0.5f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween((pulseDurationMs * 1.3f).toInt(), easing = LinearOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "glow"
    )
    val rayLengths = rememberRayLengthAnimations(
      count = rayCount,
      baseDuration = pulseDurationMs
    )

    fun degreesToRadians(deg: Double): Double = deg * PI / 180.0

    Box(
      modifier = modifier,
      contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 1f * maxRayLengthFraction

            // Soft radial halo (glow around the star)
            drawCircle(
              brush = Brush.radialGradient(
                0.0f to color.copy(alpha = 0.85f * glowAlpha * breathScale),
                0.4f to color.copy(alpha = 0.35f * glowAlpha),
                0.75f to color.copy(alpha = 0.08f * glowAlpha),
                1.0f to Color.Transparent
              ),
              radius = maxRadius * 1.4f * breathScale,
              center = center,
              alpha = 0.92f
            )

            // Draw the star rays
            rotate(22.5f) {  // initial offset – you can make this animated too if you want slow spin

                for (i in 0 until rayCount) {
                    // Base angle – evenly spaced
                    val baseAngleDeg = i * (360f / rayCount)

                    // Small per-ray angle jitter → more organic / less perfect symmetry
                    val jitterDeg = rayJitter[i]  // see definition below
                    val angleDeg = baseAngleDeg + jitterDeg
                    val angleRad = degreesToRadians(angleDeg.toDouble())

                    // Asymmetric length – base + per-ray variation
                    val lengthMultiplier = rayBaseMultipliers[i] * rayLengths[i]  // combine static + animated
                    val rayLength = maxRadius * lengthMultiplier

                    // Vary stroke width slightly per ray → thinner & thicker rays
                    val stroke = (2.8f + rayThicknessVariation[i] * 2.2f) * breathScale

                    // Different fade distances per ray for more irregularity
                    val fadeStart = rayFadeStarts[i]   // e.g. 0.35f .. 0.55f

                    val end = center + Offset(
                      x = rayLength * cos(angleRad).toFloat(),
                      y = rayLength * sin(angleRad).toFloat()
                    )

                    drawLine(
                      brush = Brush.linearGradient(
                        0.00f to color.copy(alpha = 0.92f + 0.06f * rayLengths[i]),  // slightly brighter at base sometimes
                        fadeStart     to color.copy(alpha = 0.55f * rayLengths[i]),
                        0.82f         to color.copy(alpha = 0.12f * rayLengths[i]),
                        1.00f         to Color.Transparent
                      ),
                      start = center,
                      end = end,
                      strokeWidth = stroke,
                      cap = StrokeCap.Round
                    )
                }
            }

            // Tiny bright core (point-like)
            drawCircle(
              color = color,
              radius = coreSize * breathScale,
              center = center,
              alpha = 0.98f
            )

            // Small inner sparkle
            drawCircle(
              color = Color.White.copy(alpha = 0.7f * glowAlpha),
              radius = coreSize * 0.5f * breathScale,
              center = center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeditationOngoingView(
  meditationViewModel: MeditationViewModel,
  openDrawer : () -> Unit
)
{
    val ui = meditationViewModel.uiState.collectAsState().value
    val starYellow = Color(0xFFFFE082)

    Scaffold(
      topBar = {
          TopAppBar(
            title = { Text(ui.strings.meditating) },
            navigationIcon = {
                IconButton(onClick = { openDrawer() }) {
                    Icon(Icons.Default.Menu, "Open menu")
                }
            }
          )
      }
    ) { padding ->
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentAlignment = Alignment.Center
        ) {
            Image(
              painter = painterResource(Res.drawable.meditating_person),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
            Column(
              horizontalAlignment = Alignment.CenterHorizontally
            ){
                Text(
                  text = ui.timeLeft,
                  color = starYellow,
                  style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(24.dp))
                TextButton(
                  onClick = {
                      meditationViewModel.cancelMeditation()
                  },
                ) {
                    Text(
                      text ="Cancel",
                      color = starYellow,
                      style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeditationCompleteView(
  meditationViewModel: MeditationViewModel,
  openDrawer : () -> Unit
)
{
    val ui = meditationViewModel.uiState.collectAsState().value
    val starYellow = Color(0xFFFFE082)

    Scaffold(
      topBar = {
          TopAppBar(
            title = { Text(ui.strings.mediationComplete) },
            navigationIcon = {
                IconButton(onClick = { openDrawer() }) {
                    Icon(Icons.Default.Menu, "Open menu")
                }
            }
          )
      }
    ) { padding ->
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentAlignment = Alignment.Center
        ) {
            Image(
              painter = painterResource(Res.drawable.meditating_person),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
            Column(
              horizontalAlignment = Alignment.CenterHorizontally
            ){
                Text(
                  text = "00:00",
                  color = starYellow,
                  style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(24.dp))
                TextButton(
                  onClick = {
                      meditationViewModel.cancelMeditation()
                  },
                ) {
                    Text(
                      text = ui.strings.okay,
                      color = starYellow,
                      style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
        }
    }
}