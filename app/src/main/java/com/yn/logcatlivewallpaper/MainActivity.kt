package com.yn.logcatlivewallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.yn.logcatlivewallpaper.ui.theme.LogCatLiveWallpaperTheme
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LogCatLiveWallpaperTheme {
                MainScreen()
            }
        }
    }

    @Composable
    private fun MainScreen() {
        var showSettings by remember { mutableStateOf(false) }
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            StatusBarColor()
            if (showSettings) {
                SettingsScreen(
                    onBack = { showSettings = false },
                    modifier = Modifier.padding(top = innerPadding.calculateTopPadding())
                )
            } else {
                Column(Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
                    Toolbar(onSettingsClick = { showSettings = true })
                    AndroidView(
                        factory = { ctx -> LogCatPreviewView(ctx) },
                        modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()
                    )
                }
            }
        }
    }

    @Composable
    private fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val current = remember { SettingsManager.getSettings(context) }
        var scrollSpeed by remember { mutableFloatStateOf(current.scrollSpeed) }
        var fontSize by remember { mutableIntStateOf(current.fontSizeSp) }
        var logcatFormat by remember { mutableStateOf(current.logcatFormat) }
        var permissionMethod by remember { mutableStateOf(current.permissionMethod) }
        var backgroundColor by remember { mutableStateOf(current.backgroundColor) }
        var backgroundImage by remember { mutableStateOf(current.backgroundImage) }
        var fontPath by remember { mutableStateOf(current.fontPath) }
        var showFormatPicker by remember { mutableStateOf(false) }

        val imagePickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    val file = File(context.filesDir, "bg_image.png")
                    inputStream?.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    backgroundImage = file.absolutePath
                    val s = SettingsManager.Settings(
                        scrollSpeed, fontSize, logcatFormat, permissionMethod,
                        backgroundColor, backgroundImage, fontPath
                    )
                    SettingsManager.saveSettings(context, s)
                } catch (_: Exception) {}
            }
        }

        val fontPickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    val file = File(context.filesDir, "custom_font.ttf")
                    inputStream?.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    fontPath = file.absolutePath
                    val s = SettingsManager.Settings(
                        scrollSpeed, fontSize, logcatFormat, permissionMethod,
                        backgroundColor, backgroundImage, fontPath
                    )
                    SettingsManager.saveSettings(context, s)
                    (context as? Activity)?.recreate()
                } catch (_: Exception) {}
            }
        }

        Column(modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().height(60.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "Settings",
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column {
                    Text(
                        text = "Scroll Speed: %.1f".format(scrollSpeed),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = scrollSpeed,
                        onValueChange = { scrollSpeed = it },
                        valueRange = 0.1f..10f,
                        steps = 98,
                        onValueChangeFinished = {
                            val s = SettingsManager.Settings(scrollSpeed, fontSize, logcatFormat, permissionMethod, backgroundColor, backgroundImage, fontPath)
                            SettingsManager.saveSettings(context, s)
                        }
                    )
                }
                Column {
                    Text(
                        text = "Font Size: ${fontSize}sp",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = fontSize.toFloat(),
                        onValueChange = { fontSize = it.toInt() },
                        valueRange = 8f..24f,
                        steps = 15,
                        onValueChangeFinished = {
                            val s = SettingsManager.Settings(scrollSpeed, fontSize, logcatFormat, permissionMethod, backgroundColor, backgroundImage, fontPath)
                            SettingsManager.saveSettings(context, s)
                        }
                    )
                }
                Column {
                    Text(
                        text = "Logcat Format",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        onClick = { showFormatPicker = true },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = logcatFormat,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Permission Method",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val available = mapOf(
                            "none" to true,
                            "shizuku" to PermissionManager.isShizukuAvailable(),
                            "root" to PermissionManager.isRootAvailable()
                        )
                        listOf("none", "shizuku", "root").forEach { m ->
                            val enabled = available[m] ?: false
                            Surface(
                                onClick = {
                                    if (enabled) {
                                        permissionMethod = m
                                        val s = SettingsManager.Settings(scrollSpeed, fontSize, logcatFormat, permissionMethod, backgroundColor, backgroundImage, fontPath)
                                        SettingsManager.saveSettings(context, s)
                                        if (m != "none") {
                                            PermissionManager.activate(m, context)
                                        }
                                    }
                                },
                                color = if (m == permissionMethod) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.weight(1f),
                                enabled = enabled
                            ) {
                                Text(
                                    text = m.replaceFirstChar { it.uppercase() },
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
                Column {
                    Text(
                        text = "Background",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (backgroundImage.isNotEmpty()) "Image set" else "Color",
                            modifier = Modifier.weight(1f)
                        )
                        if (backgroundImage.isNotEmpty()) {
                            IconButton(onClick = {
                                backgroundImage = ""
                                val s = SettingsManager.Settings(
                                    scrollSpeed, fontSize, logcatFormat, permissionMethod,
                                    backgroundColor, backgroundImage, fontPath
                                )
                                SettingsManager.saveSettings(context, s)
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear image")
                            }
                        }
                        IconButton(onClick = {
                            imagePickerLauncher.launch("image/*")
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Pick image")
                        }
                    }
                    if (backgroundImage.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsManager.presetColors.forEach { (hex, _) ->
                                val colorInt = Color.parseColor(hex)
                                Surface(
                                    onClick = {
                                        backgroundColor = hex
                                        val s = SettingsManager.Settings(
                                            scrollSpeed, fontSize, logcatFormat, permissionMethod,
                                            backgroundColor, backgroundImage, fontPath
                                        )
                                        SettingsManager.saveSettings(context, s)
                                    },
                                    color = androidx.compose.ui.graphics.Color(colorInt),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.size(32.dp),
                                    border = if (hex == backgroundColor)
                                        androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                    else null
                                ) {}
                            }
                        }
                    }
                }
            }
            Column {
                Text(
                    text = "Font",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (fontPath.isNotEmpty()) "Custom font" else "CGA Pixel Font",
                        modifier = Modifier.weight(1f)
                    )
                    if (fontPath.isNotEmpty()) {
                        IconButton(onClick = {
                            fontPath = ""
                            val s = SettingsManager.Settings(
                                scrollSpeed, fontSize, logcatFormat, permissionMethod,
                                backgroundColor, backgroundImage, fontPath
                            )
                            SettingsManager.saveSettings(context, s)
                            (context as? Activity)?.recreate()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Reset font")
                        }
                    }
                    IconButton(onClick = {
                        fontPickerLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype", "*/*"))
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Pick font")
                    }
                }
            }
        }
        if (showFormatPicker) {
            AlertDialog(
                onDismissRequest = { showFormatPicker = false },
                title = { Text("Logcat Format") },
                text = {
                    Column {
                        SettingsManager.formats.forEach { fmt ->
                            Surface(
                                onClick = {
                                    logcatFormat = fmt
                                    val s = SettingsManager.Settings(scrollSpeed, fontSize, logcatFormat, permissionMethod, backgroundColor, backgroundImage, fontPath)
                                    SettingsManager.saveSettings(context, s)
                                    showFormatPicker = false
                                },
                                color = if (fmt == logcatFormat) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = fmt,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFormatPicker = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }

    private fun setWallpaper() {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this@MainActivity, LogCatWallpaperService::class.java)
            )
        }
        runCatching { startActivity(intent) }
            .onFailure {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            }
    }

    private fun activateApp() {
        val method = SettingsManager.getSettings(this).permissionMethod
        val ok = PermissionManager.activate(method, this)
        if (!ok) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        }
    }

    private fun openSourceCode() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_source_code))))
    }

    private fun openDonate() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_donate))))
    }

    @Composable
    private fun StatusBarColor() {
        val color = MaterialTheme.colorScheme.surface
        val activity = LocalContext.current as? Activity
        LaunchedEffect(color) {
            val window = activity?.window ?: return@LaunchedEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = color.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                window.setNavigationBarContrastEnforced(false)
            }
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
            }
        }
    }

    @Composable
    private fun Toolbar(onSettingsClick: () -> Unit) {
        var showMenu by remember { mutableStateOf(false) }
        val context = LocalContext.current
        var hasPermission by remember { mutableStateOf(false) }

        LaunchedEffect(PermissionManager.activationTimestamp) {
            hasPermission = PermissionManager.hasReadLogsPermission(context)
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.weight(1f).padding(horizontal = 12.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontSize = 18.sp
                    )
                    Text(
                        text = if (hasPermission) stringResource(R.string.status_activated)
                        else stringResource(R.string.status_not_activated),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_set_wallpaper)) },
                            onClick = { showMenu = false; setWallpaper() },
                            leadingIcon = { Icon(Icons.Default.Star, null) }
                        )
                        if (!hasPermission) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_activate)) },
                                onClick = {
                                    showMenu = false
                                    val method = SettingsManager.getSettings(context).permissionMethod
                                    PermissionManager.activate(method, context)
                                    hasPermission = PermissionManager.hasReadLogsPermission(context)
                                },
                                leadingIcon = { Icon(Icons.Default.CheckCircle, null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("App settings") },
                            onClick = { showMenu = false; onSettingsClick() },
                            leadingIcon = { Icon(Icons.Default.Settings, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_source_code)) },
                            onClick = { showMenu = false; openSourceCode() },
                            leadingIcon = { Icon(Icons.Default.Build, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_donate)) },
                            onClick = { showMenu = false; openDonate() },
                            leadingIcon = { Icon(Icons.Default.Favorite, null) }
                        )
                    }
                }
            }
        }
    }
}

private class LogCatPreviewView(context: Context) : View(context) {
    private val lines = mutableListOf<String>()
    private val colors = mutableListOf<Int>()
    private val maxLines = 500
    private val textPaint = Paint().apply {
        isAntiAlias = false
    }
    private val bgPaint = Paint()
    private var bgBitmap: Bitmap? = null
    private var lastBgPath = ""
    private var lineHeight = 0f
    @Volatile
    private var scrollOffset = 0f
    private var running = false

    private var lastFontPath = ""

    private fun loadTypeface(path: String): Typeface {
        return if (path.isNotEmpty()) {
            Typeface.createFromFile(path)
        } else {
            Typeface.createFromAsset(context.assets, "mx437_acer710_cga.ttf")
        }
    }

    init {
        val s = SettingsManager.getSettings(context)
        textPaint.typeface = loadTypeface(s.fontPath)
        lastFontPath = s.fontPath
        running = true
        Thread {
            try {
                PermissionManager.clearLogcat(context)
                val handle = PermissionManager.startLogcat(context) ?: return@Thread
                val reader = BufferedReader(InputStreamReader(handle.inputStream))
                var line: String? = null
                while (running && reader.readLine().also { line = it } != null) {
                    line?.let { ln ->
                        if (ln.isEmpty()) return@let
                        synchronized(lines) {
                            lines.add(ln)
                            colors.add(lineColor(ln))
                            scrollOffset -= lineHeight
                            if (scrollOffset < -lineHeight * 3f) scrollOffset = -lineHeight * 3f
                            if (lines.size > maxLines) {
                                repeat(lines.size - maxLines) {
                                    lines.removeAt(0)
                                    colors.removeAt(0)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }.apply { isDaemon = true; start() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val s = SettingsManager.getSettings(context)
        if (s.fontPath != lastFontPath) {
            textPaint.typeface = loadTypeface(s.fontPath)
            lastFontPath = s.fontPath
        }
        textPaint.textSize = s.fontSizeSp * resources.displayMetrics.density
        lineHeight = textPaint.textSize * 1.25f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val s = SettingsManager.getSettings(context)

        if (s.backgroundImage.isNotEmpty()) {
            if (s.backgroundImage != lastBgPath) {
                bgBitmap = BitmapFactory.decodeFile(s.backgroundImage)
                lastBgPath = s.backgroundImage
            }
            bgBitmap?.let { bm ->
                val scale = maxOf(w / bm.width, h / bm.height)
                val srcW = (w / scale).toInt()
                val srcH = (h / scale).toInt()
                val srcX = (bm.width - srcW) / 2
                val srcY = (bm.height - srcH) / 2
                val src = Rect(srcX, srcY, srcX + srcW, srcY + srcH)
                val dst = Rect(0, 0, w.toInt(), h.toInt())
                canvas.drawBitmap(bm, src, dst, null)
            }
        } else {
            bgPaint.color = Color.parseColor(s.backgroundColor)
            canvas.drawRect(0f, 0f, w, h, bgPaint)
        }

        if (scrollOffset < 0f) {
            val step = (lineHeight * s.scrollSpeed * 0.02f).coerceAtLeast(0.1f)
            scrollOffset += minOf(-scrollOffset, step)
        }

        synchronized(lines) {
            while (lines.isNotEmpty()) {
                val oldestY = h - scrollOffset - (lines.size - 1) * lineHeight
                if (oldestY + lineHeight >= 0f) break
                lines.removeAt(0)
                colors.removeAt(0)
            }

            if (lines.isEmpty()) {
                scrollOffset = 0f
                postInvalidateOnAnimation()
                return
            }

            var y = h - scrollOffset
            for (idx in lines.indices.reversed()) {
                if (y + lineHeight < 0f) break
                if (y <= h + lineHeight) {
                    textPaint.color = colors[idx]
                    canvas.drawText(lines[idx], 8f, y, textPaint)
                }
                y -= lineHeight
            }
        }

        postInvalidateOnAnimation()
    }
}

private fun lineColor(line: String): Int {
    val priority = Regex("(?:^|\\s)([VDIWE])(?:/|\\s)")
        .find(line)?.groupValues?.getOrNull(1)
    return when (priority) {
        "V" -> Color.rgb(128, 128, 128)
        "D" -> Color.rgb(66, 133, 244)
        "I" -> Color.rgb(0, 200, 0)
        "W" -> Color.rgb(255, 193, 7)
        "E" -> Color.rgb(255, 68, 68)
        else -> Color.rgb(0, 200, 0)
    }
}
