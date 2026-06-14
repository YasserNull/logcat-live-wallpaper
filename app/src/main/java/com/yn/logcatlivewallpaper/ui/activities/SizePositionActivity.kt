/*
* Shows a fullscreen preview for editing log area size and position.
*/
package com.yn.logcatlivewallpaper.ui.activities

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yn.logcatlivewallpaper.core.LogCatRenderer
import com.yn.logcatlivewallpaper.core.PermissionManager
import com.yn.logcatlivewallpaper.core.Preferences
import com.yn.logcatlivewallpaper.ui.dialogs.SizePositionDialog
import com.yn.logcatlivewallpaper.ui.theme.LogCatLiveWallpaperTheme
import java.io.BufferedReader
import java.io.InputStreamReader

class SizePositionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        setContent {
            LogCatLiveWallpaperTheme {
                SizePositionScreen()
            }
        }
        window.decorView.post { hideSystemBars() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.decorView.windowInsetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    @Composable
    private fun SizePositionScreen() {
        val context = LocalContext.current
        val initial = remember { Preferences.getSettings(context) }
        var width by remember { mutableStateOf(initial.logWidth.toInput()) }
        var height by remember { mutableStateOf(initial.logHeight.toInput()) }
        var positionX by remember { mutableStateOf(initial.logPositionX.toInput()) }
        var positionY by remember { mutableStateOf(initial.logPositionY.toInput()) }
        var rotation by remember { mutableStateOf(initial.logRotation.toRotationInput()) }
        var viewportWidth by remember { mutableStateOf("0") }
        var viewportHeight by remember { mutableStateOf("0") }
        var showDialog by remember { mutableStateOf(false) }
        var previewSettings by remember { mutableStateOf(initial) }

        fun draftSettings(
            nextWidth: String = width,
            nextHeight: String = height,
            nextPositionX: String = positionX,
            nextPositionY: String = positionY,
            nextRotation: String = rotation
        ): Preferences.Settings {
            return Preferences.getSettings(context).copy(
                logWidth = nextWidth.toFloatOrZero(),
                logHeight = nextHeight.toFloatOrZero(),
                logPositionX = nextPositionX.toFloatOrZero(),
                logPositionY = nextPositionY.toFloatOrZero(),
                logRotation = nextRotation.toFloatOrNull() ?: 0f
            )
        }

        fun applyDraft(
            nextWidth: String = width,
            nextHeight: String = height,
            nextPositionX: String = positionX,
            nextPositionY: String = positionY,
            nextRotation: String = rotation
        ) {
            previewSettings = draftSettings(nextWidth, nextHeight, nextPositionX, nextPositionY, nextRotation)
        }

        fun saveArea(settings: Preferences.Settings) {
            val latest = Preferences.getSettings(context)
            Preferences.saveSettings(
                context,
                latest.copy(
                    logWidth = settings.logWidth,
                    logHeight = settings.logHeight,
                    logPositionX = settings.logPositionX,
                    logPositionY = settings.logPositionY,
                    logRotation = settings.logRotation
                )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = {
                    SizePositionPreviewView(
                        context = it,
                        settings = previewSettings,
                        onViewportChanged = { w, h ->
                            viewportWidth = w.toInput()
                            viewportHeight = h.toInput()
                            if (width == "0") width = viewportWidth
                            if (height == "0") height = viewportHeight
                        },
                        onAreaChanged = { x, y, w, h ->
                            val nextX = x.toInput()
                            val nextY = y.toInput()
                            val nextWidth = w.toInput()
                            val nextHeight = h.toInput()
                            positionX = nextX
                            positionY = nextY
                            width = nextWidth
                            height = nextHeight
                            val nextSettings = draftSettings(nextWidth, nextHeight, nextX, nextY)
                            previewSettings = nextSettings
                            saveArea(nextSettings)
                        }
                    )
                },
                update = { it.updateSettings(previewSettings) },
                modifier = Modifier.fillMaxSize()
            )
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Settings, null)
            }
        }

        if (showDialog) {
            SizePositionDialog(
                width = width,
                height = height,
                positionX = positionX,
                positionY = positionY,
                rotation = rotation,
                onWidthChanged = {
                    width = it
                    applyDraft(nextWidth = it)
                },
                onHeightChanged = {
                    height = it
                    applyDraft(nextHeight = it)
                },
                onPositionXChanged = {
                    positionX = it
                    applyDraft(nextPositionX = it)
                },
                onPositionYChanged = {
                    positionY = it
                    applyDraft(nextPositionY = it)
                },
                onRotationChanged = {
                    rotation = it
                    applyDraft(nextRotation = it)
                },
                onReset = {
                    width = viewportWidth
                    height = viewportHeight
                    positionX = "0"
                    positionY = "0"
                    rotation = "0"
                    applyDraft(width, height, "0", "0", "0")
                },
                onSave = {
                    val latest = Preferences.getSettings(context)
                    val toSave = draftSettings()
                    Preferences.saveSettings(
                        context,
                        latest.copy(
                            logWidth = toSave.logWidth,
                            logHeight = toSave.logHeight,
                            logPositionX = toSave.logPositionX,
                            logPositionY = toSave.logPositionY,
                            logRotation = toSave.logRotation
                        )
                    )
                    showDialog = false
                },
                onDismiss = { showDialog = false }
            )
        }
    }

    private fun Float.toInput(): String = if (this <= 0f) "0" else toInt().toString()
    private fun Float.toRotationInput(): String = toInt().toString()
    private fun String.toFloatOrZero(): Float = toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
}

private class SizePositionPreviewView(
    context: Context,
    settings: Preferences.Settings,
    private val onViewportChanged: (Float, Float) -> Unit,
    private val onAreaChanged: (Float, Float, Float, Float) -> Unit
) : View(context), SharedPreferences.OnSharedPreferenceChangeListener {
    private val renderer = LogCatRenderer(context)
    private val framePaint = Paint().apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = false
    }
    private val cornerPaint = Paint().apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
        strokeWidth = 8f
        isAntiAlias = false
    }
    private var currentSettings = settings
    private var running = true
    private var touchMode = TOUCH_NONE
    private var downX = 0f
    private var downY = 0f
    private var startX = 0f
    private var startY = 0f
    private var startWidth = 0f
    private var startHeight = 0f
    private var logcatHandle: PermissionManager.LogcatHandle? = null
    private var readerThread: Thread? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        Preferences.observer(context).registerOnSharedPreferenceChangeListener(this)
        renderer.updateSettings(currentSettings)
        Choreographer.getInstance().postFrameCallback(frameCallback)
        startLogcatReader()
    }

    fun updateSettings(settings: Preferences.Settings) {
        currentSettings = settings.clamped(width.toFloat(), height.toFloat())
        renderer.updateSettings(currentSettings)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateSettings(currentSettings)
        onViewportChanged(width.toFloat(), height.toFloat())
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        updateSettings(Preferences.getSettings(context))
        if (key == "permission_method" || key == "logcat_command") {
            stopLogcatReader()
            renderer.clear()
            startLogcatReader()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchMode = cornerMode(event.x, event.y)
                if (touchMode == TOUCH_NONE && logAreaContains(event.x, event.y)) {
                    touchMode = TOUCH_MOVE
                }
                if (touchMode == TOUCH_NONE) return false
                downX = event.x
                downY = event.y
                startX = currentSettings.logPositionX
                startY = currentSettings.logPositionY
                startWidth = areaWidth()
                startHeight = areaHeight()
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchMode == TOUCH_NONE) return true
                if (touchMode == TOUCH_MOVE) {
                    moveArea(event.x, event.y)
                } else {
                    resizeArea(event.x, event.y)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchMode = TOUCH_NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderer.draw(canvas)
        drawEditFrame(canvas)
    }

    override fun onDetachedFromWindow() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        stopLogcatReader()
        Preferences.observer(context).unregisterOnSharedPreferenceChangeListener(this)
        super.onDetachedFromWindow()
    }

    private fun startLogcatReader() {
        if (readerThread?.isAlive == true) return
        readerThread = Thread {
            try {
                val handle = PermissionManager.startLogcat(context) ?: return@Thread
                logcatHandle = handle
                val reader = BufferedReader(InputStreamReader(handle.inputStream))
                var line: String? = null
                while (running && reader.readLine().also { line = it } != null) {
                    line?.let { ln ->
                        if (ln.isNotEmpty()) {
                            renderer.enqueueLine(ln)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true; start() }
    }

    private fun stopLogcatReader() {
        logcatHandle?.destroy()
        logcatHandle = null
        readerThread?.interrupt()
        readerThread = null
    }

    private fun logAreaContains(x: Float, y: Float): Boolean {
        val point = unrotatedPoint(x, y)
        return areaRect().contains(point.first, point.second)
    }

    private fun areaRect(): RectF {
        val w = areaWidth()
        val h = areaHeight()
        return RectF(
            currentSettings.logPositionX,
            currentSettings.logPositionY,
            currentSettings.logPositionX + w,
            currentSettings.logPositionY + h
        )
    }

    private fun drawEditFrame(canvas: Canvas) {
        val rect = areaRect()
        canvas.save()
        canvas.rotate(currentSettings.logRotation, rect.centerX(), rect.centerY())
        canvas.drawRect(rect, framePaint)
        val size = cornerTouchSize() * 0.55f
        canvas.drawLine(rect.left, rect.top, rect.left + size, rect.top, cornerPaint)
        canvas.drawLine(rect.left, rect.top, rect.left, rect.top + size, cornerPaint)
        canvas.drawLine(rect.right, rect.top, rect.right - size, rect.top, cornerPaint)
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + size, cornerPaint)
        canvas.drawLine(rect.left, rect.bottom, rect.left + size, rect.bottom, cornerPaint)
        canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - size, cornerPaint)
        canvas.drawLine(rect.right, rect.bottom, rect.right - size, rect.bottom, cornerPaint)
        canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - size, cornerPaint)
        drawHandle(canvas, rect.centerX(), rect.top)
        drawHandle(canvas, rect.centerX(), rect.bottom)
        drawHandle(canvas, rect.left, rect.centerY())
        drawHandle(canvas, rect.right, rect.centerY())
        canvas.restore()
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        val size = cornerTouchSize() * 0.28f
        canvas.drawRect(x - size, y - size, x + size, y + size, cornerPaint)
    }

    private fun cornerMode(x: Float, y: Float): Int {
        val point = unrotatedPoint(x, y)
        val rect = areaRect()
        val touch = cornerTouchSize()
        return when {
            near(point.first, point.second, rect.left, rect.top, touch) -> TOUCH_RESIZE_TOP_LEFT
            near(point.first, point.second, rect.right, rect.top, touch) -> TOUCH_RESIZE_TOP_RIGHT
            near(point.first, point.second, rect.left, rect.bottom, touch) -> TOUCH_RESIZE_BOTTOM_LEFT
            near(point.first, point.second, rect.right, rect.bottom, touch) -> TOUCH_RESIZE_BOTTOM_RIGHT
            near(point.first, point.second, rect.centerX(), rect.top, touch) -> TOUCH_RESIZE_TOP
            near(point.first, point.second, rect.centerX(), rect.bottom, touch) -> TOUCH_RESIZE_BOTTOM
            near(point.first, point.second, rect.left, rect.centerY(), touch) -> TOUCH_RESIZE_LEFT
            near(point.first, point.second, rect.right, rect.centerY(), touch) -> TOUCH_RESIZE_RIGHT
            else -> TOUCH_NONE
        }
    }

    private fun unrotatedPoint(x: Float, y: Float): Pair<Float, Float> {
        val rect = areaRect()
        val radians = Math.toRadians((-currentSettings.logRotation).toDouble())
        val cos = kotlin.math.cos(radians).toFloat()
        val sin = kotlin.math.sin(radians).toFloat()
        val dx = x - rect.centerX()
        val dy = y - rect.centerY()
        return Pair(
            rect.centerX() + dx * cos - dy * sin,
            rect.centerY() + dx * sin + dy * cos
        )
    }

    private fun near(x: Float, y: Float, targetX: Float, targetY: Float, radius: Float): Boolean {
        return kotlin.math.abs(x - targetX) <= radius && kotlin.math.abs(y - targetY) <= radius
    }

    private fun moveArea(x: Float, y: Float) {
        val areaWidth = areaWidth()
        val areaHeight = areaHeight()
        val nextX = (startX + x - downX).coerceIn(0f, (width - areaWidth).coerceAtLeast(0f))
        val nextY = (startY + y - downY).coerceIn(0f, (height - areaHeight).coerceAtLeast(0f))
        onAreaChanged(nextX, nextY, areaWidth, areaHeight)
    }

    private fun resizeArea(x: Float, y: Float) {
        val dx = x - downX
        val dy = y - downY
        var nextX = startX
        var nextY = startY
        var nextWidth = startWidth
        var nextHeight = startHeight

        when (touchMode) {
            TOUCH_RESIZE_TOP_LEFT -> {
                nextX = startX + dx
                nextY = startY + dy
                nextWidth = startWidth - dx
                nextHeight = startHeight - dy
            }
            TOUCH_RESIZE_TOP_RIGHT -> {
                nextY = startY + dy
                nextWidth = startWidth + dx
                nextHeight = startHeight - dy
            }
            TOUCH_RESIZE_BOTTOM_LEFT -> {
                nextX = startX + dx
                nextWidth = startWidth - dx
                nextHeight = startHeight + dy
            }
            TOUCH_RESIZE_BOTTOM_RIGHT -> {
                nextWidth = startWidth + dx
                nextHeight = startHeight + dy
            }
            TOUCH_RESIZE_TOP -> {
                nextY = startY + dy
                nextHeight = startHeight - dy
            }
            TOUCH_RESIZE_BOTTOM -> {
                nextHeight = startHeight + dy
            }
            TOUCH_RESIZE_LEFT -> {
                nextX = startX + dx
                nextWidth = startWidth - dx
            }
            TOUCH_RESIZE_RIGHT -> {
                nextWidth = startWidth + dx
            }
        }

        val minSize = minAreaSize()
        if (nextWidth < minSize) {
            if (touchMode == TOUCH_RESIZE_TOP_LEFT || touchMode == TOUCH_RESIZE_BOTTOM_LEFT || touchMode == TOUCH_RESIZE_LEFT) {
                nextX -= minSize - nextWidth
            }
            nextWidth = minSize
        }
        if (nextHeight < minSize) {
            if (touchMode == TOUCH_RESIZE_TOP_LEFT || touchMode == TOUCH_RESIZE_TOP_RIGHT || touchMode == TOUCH_RESIZE_TOP) {
                nextY -= minSize - nextHeight
            }
            nextHeight = minSize
        }
        nextX = nextX.coerceIn(0f, (width - nextWidth).coerceAtLeast(0f))
        nextY = nextY.coerceIn(0f, (height - nextHeight).coerceAtLeast(0f))
        nextWidth = nextWidth.coerceAtMost((width - nextX).coerceAtLeast(minSize))
        nextHeight = nextHeight.coerceAtMost((height - nextY).coerceAtLeast(minSize))
        onAreaChanged(nextX, nextY, nextWidth, nextHeight)
    }

    private fun areaWidth(): Float {
        return if (currentSettings.logWidth > 0f) currentSettings.logWidth else width.toFloat()
    }

    private fun areaHeight(): Float {
        return if (currentSettings.logHeight > 0f) currentSettings.logHeight else height.toFloat()
    }

    private fun cornerTouchSize(): Float = 28f * resources.displayMetrics.density

    private fun minAreaSize(): Float = 80f * resources.displayMetrics.density

    private fun Preferences.Settings.clamped(canvasWidth: Float, canvasHeight: Float): Preferences.Settings {
        if (canvasWidth <= 0f || canvasHeight <= 0f) return this
        val w = if (logWidth > 0f) logWidth.coerceAtMost(canvasWidth) else 0f
        val h = if (logHeight > 0f) logHeight.coerceAtMost(canvasHeight) else 0f
        val actualWidth = if (w > 0f) w else canvasWidth
        val actualHeight = if (h > 0f) h else canvasHeight
        return copy(
            logWidth = w,
            logHeight = h,
            logPositionX = logPositionX.coerceIn(0f, (canvasWidth - actualWidth).coerceAtLeast(0f)),
            logPositionY = logPositionY.coerceIn(0f, (canvasHeight - actualHeight).coerceAtLeast(0f))
        )
    }

    companion object {
        private const val TOUCH_NONE = 0
        private const val TOUCH_MOVE = 1
        private const val TOUCH_RESIZE_TOP_LEFT = 2
        private const val TOUCH_RESIZE_TOP_RIGHT = 3
        private const val TOUCH_RESIZE_BOTTOM_LEFT = 4
        private const val TOUCH_RESIZE_BOTTOM_RIGHT = 5
        private const val TOUCH_RESIZE_TOP = 6
        private const val TOUCH_RESIZE_BOTTOM = 7
        private const val TOUCH_RESIZE_LEFT = 8
        private const val TOUCH_RESIZE_RIGHT = 9
    }
}
