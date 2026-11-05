package net.jdbridges.droidviewfinder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

private const val MAX_ZOOM = 8f
private const val CONTROLS_TIMEOUT_MS = 5_000L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
            ) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

@Composable
private fun App(vm: SpikeViewModel = viewModel()) {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }

    val current = vm.current
    if (hasCamera && vm.opened != null && current != null) {
        Viewer(vm, current)
    } else {
        DeviceList(vm, hasCamera) { askCamera.launch(Manifest.permission.CAMERA) }
    }
}

@Composable
private fun DeviceList(vm: SpikeViewModel, hasCamera: Boolean, askCamera: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Video sources", style = MaterialTheme.typography.titleLarge)
        if (!hasCamera) {
            Text("CAMERA permission is needed to open a USB video device.")
            Button(onClick = askCamera) { Text("Grant CAMERA") }
            return@Column
        }
        OutlinedButton(onClick = { vm.refreshDevices() }) { Text("Refresh") }
        if (vm.devices.isEmpty()) Text("No USB video devices found. Plug one in and tap Refresh.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.devices) { d ->
                Button(onClick = { vm.select(d) }, modifier = Modifier.fillMaxWidth()) {
                    Text("${d.productName ?: d.deviceName}  (${"%04x:%04x".format(d.vendorId, d.productId)})")
                }
            }
        }
    }
}

/** Full-screen, aspect-correct preview with a back arrow and three drop-downs overlaid. */
@Composable
private fun Viewer(vm: SpikeViewModel, current: com.serenegiant.usb.Size) {
    ImmersiveWhileShown()
    BackHandler { vm.close() }

    // Pinch to zoom (two fingers), drag to pan while zoomed, double-tap to return to the full view.
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var container by remember { mutableStateOf(IntSize.Zero) }
    var content by remember { mutableStateOf(IntSize.Zero) }
    // The overlay controls fade away after CONTROLS_TIMEOUT_MS and come back only on a double tap (which also
    // resets the zoom); single taps, drags and pinches leave them alone. They stay while a drop-down is open.
    // touchCount counts double taps.
    var controlsVisible by remember { mutableStateOf(true) }
    var touchCount by remember { mutableIntStateOf(0) }
    var openMenu by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(touchCount, openMenu, vm.resetting) {
        controlsVisible = true
        if (openMenu == null && !vm.resetting) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }
    // A different resolution is a different picture: start it at the full view.
    LaunchedEffect(current.width, current.height) {
        scale = 1f
        offset = Offset.Zero
    }
    // Keeps the picture covering the window along any axis where it is larger than the window.
    fun clamp(o: Offset, s: Float): Offset {
        val maxX = maxOf(0f, (content.width * s - container.width) / 2f)
        val maxY = maxOf(0f, (content.height * s - container.height) / 2f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onSizeChanged { container = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = 1f
                    offset = Offset.Zero
                    touchCount++ // also brings the controls back
                })
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                    // Zoom about the fingers: keep the point under the centroid where it is.
                    val c = centroid - Offset(container.width / 2f, container.height / 2f)
                    offset = clamp(c - (c - offset) * (newScale / scale) + pan, newScale)
                    scale = newScale
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // aspectRatio picks the largest size with this ratio that fits the window, so the picture is
        // letterboxed (never stretched) in both orientations and on both fold postures.
        Box(
            Modifier.aspectRatio(current.width.toFloat() / current.height)
                .onSizeChanged { content = it }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(h: SurfaceHolder) = vm.surfaceReady(h.surface)
                            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) = Unit
                            override fun surfaceDestroyed(h: SurfaceHolder) = vm.surfaceGone(h.surface)
                        })
                    }
                },
                // Render into a buffer of the camera's own size and let the compositor scale it, so a
                // zoomed-in picture shows the camera's real detail instead of an upscaled screen-size copy.
                update = { it.holder.setFixedSize(current.width, current.height) },
            )
        }

        if (vm.resetting) {
            Text(
                "Resetting USB device\u2026",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopStart),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            IconButton(
                onClick = { vm.close() },
                modifier = Modifier.safeDrawingPadding().padding(4.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to sources") }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f))
                    .safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Selector(
                    "Type", typeLabel(current.type), vm.types, ::typeLabel, vm::selectType, Modifier.weight(1f),
                    open = openMenu == "type", onOpenChange = { openMenu = if (it) "type" else null },
                )
                Selector(
                    "Resolution", "${current.width}x${current.height}", vm.resolutions(current.type),
                    { (w, h) -> "${w}x$h" }, { (w, h) -> vm.selectResolution(w, h) }, Modifier.weight(1f),
                    open = openMenu == "resolution", onOpenChange = { openMenu = if (it) "resolution" else null },
                )
                Selector(
                    "FPS", "${current.fps}", vm.frameRates(current.type, current.width, current.height), { "$it" },
                    vm::selectFps, Modifier.weight(1f),
                    open = openMenu == "fps", onOpenChange = { openMenu = if (it) "fps" else null },
                )
                IconButton(
                    onClick = vm::resetDevice,
                    enabled = !vm.resetting,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.15f), contentColor = Color.White),
                ) { Icon(Icons.Filled.Refresh, contentDescription = "Reset USB device") }
            }
        }
    }
}

/** A compact drop-down: the label sits above the current value, and tapping opens the options. */
@Composable
private fun <T> Selector(
    label: String, value: String, options: List<T>, text: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier,
    open: Boolean, onOpenChange: (Boolean) -> Unit,
) {
    Box(modifier) {
        OutlinedButton(onClick = { onOpenChange(true) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                Text(value, color = Color.White, maxLines = 1)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Color.White)
        }
        DropdownMenu(expanded = open, onDismissRequest = { onOpenChange(false) }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(text(o)) }, onClick = { onOpenChange(false); onSelect(o) })
            }
        }
    }
}

// Size.type is the UVC descriptor subtype: 4/5 = uncompressed, 6/7 = MJPEG.
private fun typeLabel(type: Int) = when (type) {
    4, 5 -> "Uncompressed"
    6, 7 -> "MJPEG"
    else -> "Format $type"
}

/** Hides the system bars and keeps the screen on while the viewer is shown; restores both afterwards. */
@Composable
private fun ImmersiveWhileShown() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as android.app.Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
}
