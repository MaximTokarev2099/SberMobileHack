package com.sbermobilehack.magicball

import android.graphics.Bitmap
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF7C3AED))) {
                MagicBallScreen()
            }
        }
    }
}

@Composable
private fun MagicBallScreen() {
    val context = LocalContext.current
    val config = remember { BallConfig.load(context) }
    var seasonName by rememberSaveable { mutableStateOf(config.season.name) }
    val season = Season.valueOf(seasonName)
    var motionEnabled by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("Нажатия по карточкам работают даже во время эффекта.") }
    var visible by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    var runId by remember { mutableIntStateOf(0) }
    var prediction by remember { mutableStateOf("") }
    var previous by remember { mutableStateOf("") }
    var launchCenter by remember { mutableStateOf(Offset.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var flying by remember { mutableStateOf(false) }
    var freeCenter by remember { mutableStateOf<Offset?>(null) }
    var throwVelocity by remember { mutableStateOf(Offset.Zero) }
    var answerId by remember { mutableIntStateOf(0) }
    var controlsHeight by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    val opacity = remember { Animatable(1f) }
    val reducedMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val hasSensor = remember {
        (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
            .getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }
    fun vibrate(duration: Long, amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE) {
        val vibrator = context.getSystemService(Vibrator::class.java)
        if (vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
    }
    fun launch() {
        if (visible && (!settled || dragging || flying)) return
        freeCenter = null
        settled = false
        prediction = ""
        visible = true
        runId++
        vibrate(35)
    }
    MotionInput(motionEnabled) { if (!visible) launch() }
    fun hideBall() {
        visible = false
        settled = false
        dragging = false
        flying = false
        freeCenter = null
    }
    BackHandler(visible) { hideBall() }
    LaunchedEffect(runId, visible) {
        if (!visible) return@LaunchedEffect
        opacity.snapTo(1f)
        progress.snapTo(0f)
        progress.animateTo(1f, tween(if (reducedMotion) 0 else 1150, easing = LinearEasing))
        settled = true
        vibrate(70)
        answerId++
    }
    LaunchedEffect(answerId, visible, dragging, flying, settled) {
        if (!visible || !settled || dragging || flying) return@LaunchedEffect
        opacity.snapTo(1f)
        val text = nextAnswer(config.answers, previous)
        previous = text
        if (reducedMotion) prediction = text else {
            val points = text.codePoints().toArray()
            for (count in 1..points.size) {
                prediction = String(points, 0, count)
                delay(65)
            }
        }
        delay(8000)
        opacity.animateTo(0f, tween(if (reducedMotion) 0 else 450))
        hideBall()
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFF5F7F8)).safeDrawingPadding()
        .onGloballyPositioned { rootOrigin = it.boundsInRoot().topLeft }) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val orbSize = min(maxWidth.value * .60f, 250f).dp
        val orbPx = with(density) { orbSize.toPx() }
        val radius = orbPx / 2
        val playHeight = (heightPx - controlsHeight).coerceAtLeast(orbPx)
        fun clampCenter(position: Offset) = Offset(
            position.x.coerceIn(radius, (widthPx - radius).coerceAtLeast(radius)),
            position.y.coerceIn(radius, (playHeight - radius).coerceAtLeast(radius)))
        val target = Offset(widthPx / 2, heightPx * .46f)
        val start = launchCenter - rootOrigin
        LaunchedEffect(flying, visible, widthPx, playHeight) {
            if (!visible || !flying) return@LaunchedEffect
            val initial = clampCenter(freeCenter ?: target)
            var state = ThrowState(initial.x, initial.y, throwVelocity.x, throwVelocity.y)
            var lastFrame = withFrameNanos { it }
            var lastImpact = 0L
            val started = lastFrame
            while (true) {
                val frame = withFrameNanos { it }
                val step = advanceThrow(state, (frame - lastFrame) / 1_000_000_000f,
                    widthPx, playHeight, radius, density.density)
                state = step.state
                lastFrame = frame
                freeCenter = Offset(state.x, state.y)
                if (step.impact > 200 * density.density && frame - lastImpact > 90_000_000) {
                    val strength = (step.impact / (20 * density.density)).toInt().coerceIn(40, 220)
                    vibrate(20, strength)
                    lastImpact = frame
                }
                if (step.stopped || frame - started > 8_000_000_000L) break
            }
            flying = false
            answerId++
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("СберKids", fontSize = 25.sp, fontWeight = FontWeight.Black, color = Color(0xFF168D50), modifier = Modifier.weight(1f))
                TextButton(onClick = { launch() }) { Text("✦", fontSize = 25.sp) }
                Button(onClick = { launch() }, modifier = Modifier.onGloballyPositioned { launchCenter = it.boundsInRoot().center }) {
                    Text("Запустить")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Привет! 👋", fontSize = 18.sp)
                Text("Твой день — твои открытия", fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text("Это демонстрационный интерфейс, не настоящее приложение СберKids.", fontSize = 13.sp, color = Color.Gray)
            }
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF149E62), Color(0xFF67CB9D))), RoundedCornerShape(25.dp)).padding(26.dp)) {
                Text("Моя копилка", color = Color.White)
                Text("1 250 ₽", fontSize = 37.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { .58f }, modifier = Modifier.fillMaxWidth(), color = Color.White, trackColor = Color.White.copy(alpha = .3f))
            }
            listOf(listOf("💚\nКопилка", "⭐\nНаграды"), listOf("🎮\nИгры", "🐱\nСберКот")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    row.forEach { label ->
                        Card(onClick = { status = "Нажатие прошло: ${label.replace('\n', ' ')}" },
                            modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Text(label, Modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = TextAlign.Center, fontSize = 20.sp)
                        }
                    }
                }
            }
            Text("Потряси телефон или нажми «Запустить»", fontSize = 13.sp)
            Text("Поймай шар пальцем и брось! После остановки появится новый ответ.", fontSize = 13.sp, color = Color(0xFF7C3AED))
            Text(status, fontSize = 12.sp, color = Color(0xFF287952), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (visible) {
            val glow = config.glowColors[season]?.let { Color(it) } ?: Color(season.glow)
            if (!reducedMotion) SeasonalParticles(season, config.particleCount, widthPx, heightPx, runId)
            val p = progress.value
            val travel = (p / .65f).coerceIn(0f, 1f)
            val bounce = when {
                p < .65f -> 0f
                p < .83f -> 25f - (p - .65f) / .18f * 41f
                else -> -16f + (p - .83f) / .17f * 16f
            }
            val center = freeCenter?.let { clampCenter(it) } ?: if (reducedMotion) target else Offset(
                start.x + (target.x - start.x) * travel,
                start.y + (target.y - start.y) * travel * travel + with(density) { bounce.dp.toPx() })
            Box(Modifier.size(orbSize).graphicsLayer {
                translationX = center.x - orbPx / 2
                translationY = center.y - orbPx / 2
                rotationZ = if (reducedMotion) 0f else -360f * (1 - travel)
                scaleX = if (reducedMotion) 1f else .12f + .88f * travel
                scaleY = scaleX
                alpha = opacity.value
            }.then(if (settled) Modifier
                .pointerInput(runId, widthPx, playHeight) {
                    val tracker = VelocityTracker()
                    var distance = Offset.Zero
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            flying = false
                            prediction = ""
                            freeCenter = clampCenter(freeCenter ?: target)
                            distance = Offset.Zero
                            tracker.resetTracking()
                            vibrate(15)
                        },
                        onDragEnd = {
                            val velocity = tracker.calculateVelocity()
                            val limit = 3500f * density.density
                            throwVelocity = Offset(velocity.x.coerceIn(-limit, limit), velocity.y.coerceIn(-limit, limit))
                            dragging = false
                            if (reducedMotion) answerId++ else flying = true
                        },
                        onDragCancel = {
                            dragging = false
                            if (reducedMotion) answerId++ else {
                                throwVelocity = Offset.Zero
                                flying = true
                            }
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            distance += amount
                            tracker.addPosition(change.uptimeMillis, distance)
                            freeCenter = clampCenter((freeCenter ?: target) + amount)
                        }
                    )
                }
                .clickable(enabled = !dragging && !flying, onClickLabel = "Новое предсказание") { launch() }
                else Modifier)
                .semantics { contentDescription = if (settled) "Магический шар. $prediction. Можно перетащить и бросить." else "Магический шар появляется" }) {
                if (settled) Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(glow.copy(alpha = .6f), Color.Transparent)), CircleShape))
                SvgAsset(season.asset, Modifier.fillMaxSize())
                if (settled && !dragging && !flying) Text(prediction, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, lineHeight = 16.sp,
                    modifier = Modifier.offset(x = orbSize * .32f, y = orbSize * .39f).width(orbSize * .36f))
            }
        }
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { controlsHeight = it.height }, color = Color.White.copy(alpha = .97f), shadowElevation = 8.dp) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Season.entries.forEach { item ->
                        TextButton(onClick = { seasonName = item.name }) {
                            Text(item.label, fontWeight = if (item == season) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = hasSensor, onClick = { motionEnabled = !motionEnabled }) {
                        Text(if (!hasSensor) "Датчик недоступен" else if (motionEnabled) "Выключить тряску" else "Включить тряску")
                    }
                    TextButton(onClick = { hideBall() }, enabled = visible) { Text("Скрыть") }
                }
            }
        }
    }
}

@Composable
private fun SvgAsset(path: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(path) {
        val svg = SVG.getFromAsset(context.assets, path)
        Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).also {
            svg.documentWidth = 400f
            svg.documentHeight = 400f
            svg.renderToCanvas(android.graphics.Canvas(it))
        }.asImageBitmap()
    }
    Image(bitmap, contentDescription = null, modifier = modifier)
}

private data class Particle(val x: Float, val phase: Float, val duration: Float, val drift: Float, val asset: String)

@Composable
private fun SeasonalParticles(season: Season, count: Int, width: Float, height: Float, runId: Int) {
    if (season.particles.isEmpty() || count == 0) return
    val particles = remember(season, count, runId) {
        List(count) { Particle(Random.nextFloat(), Random.nextFloat(), 3.2f + Random.nextFloat() * 4,
            Random.nextFloat() * 160 - 80, "particles/${season.particles.random()}.svg") }
    }
    val transition = rememberInfiniteTransition(label = "Сезонные частицы")
    val seconds by transition.animateFloat(0f, 60f, infiniteRepeatable(tween(60000, easing = LinearEasing)), label = "Падение")
    particles.forEach { particle ->
        val phase = (seconds / particle.duration + particle.phase) % 1f
        SvgAsset(particle.asset, Modifier.size(24.dp).graphicsLayer {
            translationX = particle.x * width + particle.drift * phase
            translationY = -50 + phase * (height + 100)
            rotationZ = phase * 360
            alpha = .85f
        })
    }
}
