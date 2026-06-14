/*
* Shows the full settings screen for wallpaper configuration.
*/
package com.yn.logcatlivewallpaper.ui.activities

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yn.logcatlivewallpaper.R
import com.yn.logcatlivewallpaper.core.PermissionManager
import com.yn.logcatlivewallpaper.core.Preferences
import com.yn.logcatlivewallpaper.ui.dialogs.ColorPickerDialog
import com.yn.logcatlivewallpaper.ui.dialogs.CommandEditorDialog
import com.yn.logcatlivewallpaper.ui.dialogs.FontPickerDialog
import com.yn.logcatlivewallpaper.ui.dialogs.PermissionPickerDialog
import com.yn.logcatlivewallpaper.ui.dialogs.ScrollModeDialog
import com.yn.logcatlivewallpaper.ui.theme.LogCatLiveWallpaperTheme
import com.yn.logcatlivewallpaper.utils.ApplyStatusBarColor
import com.yn.logcatlivewallpaper.utils.defaultColorForTarget
import com.yn.logcatlivewallpaper.utils.fontLabel
import com.yn.logcatlivewallpaper.utils.imageLabel
import com.yn.logcatlivewallpaper.utils.normalizeHex
import com.yn.logcatlivewallpaper.utils.permissionLabel
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LogCatLiveWallpaperTheme {
                ApplyStatusBarColor(MaterialTheme.colorScheme.surface)
                val statusBarColor = MaterialTheme.colorScheme.surface
                Box(Modifier.fillMaxSize()) {
                    SettingsScreen(onBack = { finish() })
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(statusBarColor)
                            .align(Alignment.TopCenter)
                    )
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
        val context = LocalContext.current
        BackHandler(onBack = onBack)
        val current = remember { Preferences.getSettings(context) }
        var scrollSpeed by remember { mutableFloatStateOf(current.scrollSpeed) }
        var scrollMode by remember { mutableStateOf(current.scrollMode) }
        var logWidth by remember { mutableFloatStateOf(current.logWidth) }
        var logHeight by remember { mutableFloatStateOf(current.logHeight) }
        var logPositionX by remember { mutableFloatStateOf(current.logPositionX) }
        var logPositionY by remember { mutableFloatStateOf(current.logPositionY) }
        var logRotation by remember { mutableFloatStateOf(current.logRotation) }
        var fontSize by remember { mutableIntStateOf(current.fontSizeSp) }
        var logcatCommand by remember { mutableStateOf(current.logcatCommand) }
        var permissionMethod by remember { mutableStateOf(current.permissionMethod) }
        var backgroundColor by remember { mutableStateOf(current.backgroundColor) }
        var backgroundImage by remember { mutableStateOf(current.backgroundImage) }
        var backgroundImageName by remember { mutableStateOf(current.backgroundImageName) }
        var fontPath by remember { mutableStateOf(current.fontPath) }
        var customFontName by remember { mutableStateOf(current.customFontName) }
        var wrapWord by remember { mutableStateOf(current.wrapWord) }
        var colorVerbose by remember { mutableStateOf(current.colorVerbose) }
        var colorDebug by remember { mutableStateOf(current.colorDebug) }
        var colorInfo by remember { mutableStateOf(current.colorInfo) }
        var colorWarning by remember { mutableStateOf(current.colorWarning) }
        var colorError by remember { mutableStateOf(current.colorError) }
        var colorFatal by remember { mutableStateOf(current.colorFatal) }
        var colorSilent by remember { mutableStateOf(current.colorSilent) }
        
        var showCommandEditor by remember { mutableStateOf(false) }
        var draftCommand by remember { mutableStateOf(current.logcatCommand) }
        var showScrollModePicker by remember { mutableStateOf(false) }
        var showPermissionPicker by remember { mutableStateOf(false) }
        var showFontPicker by remember { mutableStateOf(false) }
        var showColorPicker by remember { mutableStateOf(false) }
        var colorPickerTarget by remember { mutableStateOf("background") }
        var draftBackgroundHex by remember { mutableStateOf(current.backgroundColor) }
        var colorPickerBrightness by remember {
            val hsv = FloatArray(3)
            Color.colorToHSV(Color.parseColor(current.backgroundColor), hsv)
            mutableFloatStateOf(hsv[2])
        }
        val sizePositionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            val latest = Preferences.getSettings(context)
            logWidth = latest.logWidth
            logHeight = latest.logHeight
            logPositionX = latest.logPositionX
            logPositionY = latest.logPositionY
            logRotation = latest.logRotation
        }

        fun saveSettings() {
            val latestSettings = Preferences.getSettings(context)
            val s = Preferences.Settings(
                scrollSpeed,
                scrollMode,
                latestSettings.logWidth,
                latestSettings.logHeight,
                latestSettings.logPositionX,
                latestSettings.logPositionY,
                latestSettings.logRotation,
                fontSize,
                logcatCommand,
                permissionMethod,
                backgroundColor,
                backgroundImage,
                backgroundImageName,
                fontPath,
                customFontName,
                wrapWord,
                colorVerbose,
                colorDebug,
                colorInfo,
                colorWarning,
                colorError,
                colorFatal,
                colorSilent
            )
            Preferences.saveSettings(context, s)
        }

        fun getTargetColor(): String {
            return when (colorPickerTarget) {
                "verbose" -> colorVerbose
                "debug" -> colorDebug
                "info" -> colorInfo
                "warning" -> colorWarning
                "error" -> colorError
                "fatal" -> colorFatal
                "silent" -> colorSilent
                else -> backgroundColor
            }
        }

        fun setTargetColor(hex: String, updateDraft: Boolean = true) {
            when (colorPickerTarget) {
                "verbose" -> colorVerbose = hex
                "debug" -> colorDebug = hex
                "info" -> colorInfo = hex
                "warning" -> colorWarning = hex
                "error" -> colorError = hex
                "fatal" -> colorFatal = hex
                "silent" -> colorSilent = hex
                else -> backgroundColor = hex
            }
            if (updateDraft) {
                draftBackgroundHex = hex
            }
            saveSettings()
        }

        fun updateTargetBrightness(value: Float) {
            val hsv = FloatArray(3)
            Color.colorToHSV(Color.parseColor(getTargetColor()), hsv)
            hsv[2] = value
            val hex = "#%08X".format(Color.HSVToColor(hsv))
            setTargetColor(hex)
            colorPickerBrightness = value
        }

        val imagePickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                try {
                    val name = context.contentResolver
                        .query(it, null, null, null, null)
                        ?.use { cursor ->
                            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                        }
                        ?: context.getString(R.string.settings_no_background_image)
                    val inputStream = context.contentResolver.openInputStream(it)
                    val extension = name.substringAfterLast('.', "png").ifBlank { "png" }
                    val file = File(context.filesDir, "bg_image_${System.currentTimeMillis()}.$extension")
                    inputStream?.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    backgroundImage = file.absolutePath
                    backgroundImageName = name
                    saveSettings()
                } catch (_: Exception) {}
            }
        }

        val fontPickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let {
                try {
                    val name = context.contentResolver
                        .query(it, null, null, null, null)
                        ?.use { cursor ->
                            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                        }
                        ?: context.getString(R.string.font_custom_fallback)
                    val inputStream = context.contentResolver.openInputStream(it)
                    val extension = name.substringAfterLast('.', "ttf").ifBlank { "ttf" }
                    val file = File(context.filesDir, "custom_font_${System.currentTimeMillis()}.$extension")
                    inputStream?.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    fontPath = file.absolutePath
                    customFontName = name
                    saveSettings()
                } catch (_: Exception) {}
            }
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.menu_settings)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.settings_scroll_speed, scrollSpeed),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                    Slider(
                        value = scrollSpeed,
                        onValueChange = {
                            scrollSpeed = it
                            saveSettings()
                        },
                        valueRange = 0.5f..50.0f,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
                Column {
                    Button(
                        onClick = { showScrollModePicker = true },
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = stringResource(R.string.settings_scroll_mode),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = when (scrollMode) {
                                    Preferences.SCROLL_MODE_TERMINAL -> stringResource(R.string.scroll_mode_terminal)
                                    else -> stringResource(R.string.scroll_mode_smooth)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                Column {
                    Button(
                        onClick = {
                            sizePositionLauncher.launch(Intent(context, SizePositionActivity::class.java))
                        },
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = stringResource(R.string.settings_change_size_position),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${logWidth.toInt()} x ${logHeight.toInt()}  ${logPositionX.toInt()}, ${logPositionY.toInt()}  ${logRotation.toInt()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                Column {
                    Text(
                        text = stringResource(R.string.settings_font_size, fontSize),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                    Slider(
                        value = fontSize.toFloat(),
                        onValueChange = {
                            fontSize = it.toInt()
                            saveSettings()
                        },
                        valueRange = 8f..32f,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
                Column {
                    Button(
                        onClick = { showFontPicker = true },
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = stringResource(R.string.settings_change_font),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = fontLabel(context, fontPath, customFontName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clickable {
                                wrapWord = !wrapWord
                                saveSettings()
                            }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.settings_wrap_word),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Switch(
                            checked = wrapWord,
                            onCheckedChange = {
                                wrapWord = it
                                saveSettings()
                            }
                        )
                    }
                }
                Column {
                    Button(
                        onClick = {
                            draftCommand = logcatCommand
                            showCommandEditor = true
                        },
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = stringResource(R.string.settings_customize_command),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = logcatCommand.ifBlank { Preferences.DEFAULT_LOGCAT_COMMAND },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                Column {
                    Button(
                        onClick = { showPermissionPicker = true },
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = stringResource(R.string.settings_permission_method),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = permissionLabel(permissionMethod),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            imagePickerLauncher.launch("image/*")
                        },
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_change_background_image),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = imageLabel(context, backgroundImage, backgroundImageName),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            if (backgroundImage.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.action_clear_image),
                                    modifier = Modifier.clickable {
                                        backgroundImage = ""
                                        backgroundImageName = ""
                                        saveSettings()
                                    }
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            colorPickerTarget = "background"
                            val hsv = FloatArray(3)
                            Color.colorToHSV(Color.parseColor(backgroundColor), hsv)
                            colorPickerBrightness = hsv[2]
                            draftBackgroundHex = backgroundColor
                            showColorPicker = true
                        },
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_change_background_color),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = backgroundColor,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            Surface(
                                color = androidx.compose.ui.graphics.Color(Color.parseColor(backgroundColor)),
                                shape = CircleShape,
                                modifier = Modifier.size(20.dp)
                            ) {}
                        }
                    }
                    
                    Text(
                        text = stringResource(R.string.settings_log_level_colors),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    )

                    val logLevels = listOf(
                        Triple("verbose", stringResource(R.string.settings_color_verbose), colorVerbose),
                        Triple("debug", stringResource(R.string.settings_color_debug), colorDebug),
                        Triple("info", stringResource(R.string.settings_color_info), colorInfo),
                        Triple("warning", stringResource(R.string.settings_color_warning), colorWarning),
                        Triple("error", stringResource(R.string.settings_color_error), colorError),
                        Triple("fatal", stringResource(R.string.settings_color_fatal), colorFatal),
                        Triple("silent", stringResource(R.string.settings_color_silent), colorSilent)
                    )

                    logLevels.forEach { (target, label, color) ->
                        Button(
                            onClick = {
                                colorPickerTarget = target
                                val hsv = FloatArray(3)
                                Color.colorToHSV(Color.parseColor(color), hsv)
                                colorPickerBrightness = hsv[2]
                                draftBackgroundHex = color
                                showColorPicker = true
                            },
                            shape = RectangleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = color,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                Surface(
                                    color = androidx.compose.ui.graphics.Color(Color.parseColor(color)),
                                    shape = CircleShape,
                                    modifier = Modifier.size(20.dp)
                                ) {}
                            }
                        }
                    }
                }
            }
        }

        if (showColorPicker) {
            ColorPickerDialog(
                target = colorPickerTarget,
                targetColor = getTargetColor(),
                draftHex = draftBackgroundHex,
                brightness = colorPickerBrightness,
                onDismiss = { showColorPicker = false },
                onDraftHexChanged = { value ->
                    draftBackgroundHex = value
                    normalizeHex(value)?.let { hex ->
                        setTargetColor(hex, updateDraft = false)
                        val hsv = FloatArray(3)
                        Color.colorToHSV(Color.parseColor(hex), hsv)
                        colorPickerBrightness = hsv[2]
                    }
                },
                onColorChanged = { hex, updateDraft -> setTargetColor(hex, updateDraft) },
                onBrightnessChanged = { updateTargetBrightness(it) },
                onReset = {
                    val defaultHex = defaultColorForTarget(colorPickerTarget)
                    setTargetColor(defaultHex)
                    val hsv = FloatArray(3)
                    Color.colorToHSV(Color.parseColor(defaultHex), hsv)
                    colorPickerBrightness = hsv[2]
                }
            )
        }
        if (showCommandEditor) {
            CommandEditorDialog(
                command = draftCommand,
                onCommandChanged = { draftCommand = it },
                onSave = {
                    logcatCommand = draftCommand.ifBlank { Preferences.DEFAULT_LOGCAT_COMMAND }
                    saveSettings()
                    showCommandEditor = false
                },
                onDismiss = { showCommandEditor = false }
            )
        }
        if (showScrollModePicker) {
            ScrollModeDialog(
                selectedMode = scrollMode,
                onModeSelected = { mode ->
                    scrollMode = mode
                    saveSettings()
                    showScrollModePicker = false
                },
                onDismiss = { showScrollModePicker = false }
            )
        }
        if (showPermissionPicker) {
            PermissionPickerDialog(
                selectedMethod = permissionMethod,
                onMethodSelected = { method ->
                    permissionMethod = method
                    saveSettings()
                    showPermissionPicker = false
                    if (method != "none") {
                        PermissionManager.activate(method)
                    }
                },
                onDismiss = { showPermissionPicker = false }
            )
        }
        if (showFontPicker) {
            FontPickerDialog(
                selectedFontPath = fontPath,
                onBuiltInFontSelected = { path ->
                    fontPath = path
                    customFontName = ""
                    saveSettings()
                    showFontPicker = false
                },
                onCustomFontSelected = {
                    showFontPicker = false
                    fontPickerLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype", "*/*"))
                },
                onDismiss = { showFontPicker = false }
            )
        }
    }
}
