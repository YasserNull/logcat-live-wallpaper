/*
* Shows the color picker dialog and color wheel control.
*/
package com.yn.logcatlivewallpaper.ui.dialogs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yn.logcatlivewallpaper.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun ColorPickerDialog(
    target: String,
    targetColor: String,
    draftHex: String,
    brightness: Float,
    onDismiss: () -> Unit,
    onDraftHexChanged: (String) -> Unit,
    onColorChanged: (String, Boolean) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onReset: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (target) {
                    "verbose" -> stringResource(R.string.settings_color_verbose)
                    "debug" -> stringResource(R.string.settings_color_debug)
                    "info" -> stringResource(R.string.settings_color_info)
                    "warning" -> stringResource(R.string.settings_color_warning)
                    "error" -> stringResource(R.string.settings_color_error)
                    "fatal" -> stringResource(R.string.settings_color_fatal)
                    "silent" -> stringResource(R.string.settings_color_silent)
                    else -> stringResource(R.string.settings_pick_background_color)
                }
            )
        },
        text = {
            Column {
                AndroidView(
                    factory = { ctx ->
                        ColorWheelView(ctx, Color.parseColor(targetColor), brightness) { color ->
                            onColorChanged("#%08X".format(color), true)
                        }
                    },
                    update = { view ->
                        view.setColor(Color.parseColor(targetColor))
                        view.setBrightness(brightness)
                    },
                    modifier = Modifier.size(280.dp)
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = androidx.compose.ui.graphics.Color(Color.parseColor(targetColor)),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().height(18.dp)
                ) {}
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = draftHex,
                    onValueChange = onDraftHexChanged,
                    label = { Text(stringResource(R.string.settings_color_hex)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_color_brightness),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = brightness,
                    onValueChange = onBrightnessChanged,
                    valueRange = 0f..1f
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text(stringResource(R.string.action_reset))
            }
        }
    )
}

class ColorWheelView(
    context: Context,
    initialColor: Int,
    initialBrightness: Float,
    private val onColorChanged: (Int) -> Unit
) : View(context) {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    private val selectorShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
        color = Color.BLACK
    }
    private var wheelBitmap: Bitmap? = null
    private var wheelSize = 0
    private var hue = 0f
    private var saturation = 1f
    private var brightness = initialBrightness

    init {
        setColor(initialColor)
    }

    fun setColor(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        invalidate()
    }

    fun setBrightness(value: Float) {
        brightness = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        wheelSize = min(w, h)
        wheelBitmap = if (wheelSize > 0) createColorWheelBitmap(wheelSize) else null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = wheelBitmap ?: return
        val left = (width - wheelSize) / 2f
        val top = (height - wheelSize) / 2f
        canvas.drawBitmap(bitmap, left, top, bitmapPaint)

        val radius = wheelSize / 2f
        val angle = Math.toRadians(hue.toDouble())
        val selectorRadius = saturation * radius
        val x = left + radius + cos(angle).toFloat() * selectorRadius
        val y = top + radius + sin(angle).toFloat() * selectorRadius
        canvas.drawCircle(x, y, 11f, selectorShadowPaint)
        canvas.drawCircle(x, y, 11f, selectorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN &&
            event.action != MotionEvent.ACTION_MOVE
        ) {
            return true
        }

        val radius = wheelSize / 2f
        if (radius <= 0f) return true
        val left = (width - wheelSize) / 2f
        val top = (height - wheelSize) / 2f
        val dx = event.x - (left + radius)
        val dy = event.y - (top + radius)
        val distance = sqrt(dx * dx + dy * dy)
        if (distance > radius) return true

        val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        hue = if (angle < 0f) angle + 360f else angle
        saturation = (distance / radius).coerceIn(0f, 1f)
        onColorChanged(Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))
        invalidate()
        return true
    }

    private fun createColorWheelBitmap(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val radius = size / 2f
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - radius
                val dy = y - radius
                val distance = sqrt(dx * dx + dy * dy)
                pixels[y * size + x] = if (distance <= radius) {
                    val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                    val pixelHue = if (angle < 0f) angle + 360f else angle
                    val pixelSaturation = (distance / radius).coerceIn(0f, 1f)
                    Color.HSVToColor(floatArrayOf(pixelHue, pixelSaturation, 1f))
                } else {
                    Color.TRANSPARENT
                }
            }
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }
}
