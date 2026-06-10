package com.yn.logcatlivewallpaper

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import java.io.InputStream

enum class PermissionMethod { SHIZUKU, ROOT }

object PermissionManager {
    private const val PACKAGE = "com.yn.logcatlivewallpaper"
    private const val PERMISSION = "android.permission.READ_LOGS"
    private const val SHIZUKU_REQUEST_CODE = 1001

    private var shizukuListenerRegistered = false
    @Volatile
    var activationTimestamp = 0L

    private val shizukuPermissionListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            if (requestCode == SHIZUKU_REQUEST_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
                if (grantShizuku()) {
                    activationTimestamp = System.currentTimeMillis()
                }
            }
        }
    }

    fun ensureShizukuListener() {
        if (!shizukuListenerRegistered) {
            try {
                Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
                shizukuListenerRegistered = true
            } catch (_: Exception) {}
        }
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.getVersion() > 0
        } catch (_: Exception) { false }
    }

    fun isRootAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("which", "su")
                .redirectErrorStream(true).start()
            process.waitFor() == 0
        } catch (_: Exception) { false }
    }

    fun hasShizukuPermission(): Boolean {
        return try {
            Shizuku.getVersion() > 0 &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }
    }

    fun hasReadLogsPermission(context: Context): Boolean {
        return context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    fun isActivated(context: Context): Boolean {
        return hasReadLogsPermission(context)
    }

    fun activate(method: String, context: Context): Boolean {
        return when (method) {
            "shizuku" -> activateShizuku(context)
            "root" -> activateRoot(context)
            else -> false
        }
    }

    private fun activateShizuku(context: Context): Boolean {
        if (!isShizukuAvailable()) return false
        ensureShizukuListener()
        if (hasShizukuPermission()) {
            return grantShizuku()
        }
        try {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        } catch (_: Exception) {}
        return false
    }

    private fun activateRoot(context: Context): Boolean {
        if (!isRootAvailable()) return false
        return try {
            val process = ProcessBuilder(
                "su", "-c", "pm grant $PACKAGE $PERMISSION"
            ).redirectErrorStream(true).start()
            val ok = process.waitFor() == 0
            if (ok) activationTimestamp = System.currentTimeMillis()
            ok
        } catch (_: Exception) { false }
    }

    private fun grantShizuku(): Boolean {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", "pm grant $PACKAGE $PERMISSION"),
                null,
                null
            )
            val exitCode = process::class.java.getMethod("waitFor").invoke(process) as Int
            val ok = exitCode == 0
            if (ok) activationTimestamp = System.currentTimeMillis()
            ok
        } catch (_: Exception) { false }
    }

    fun startLogcat(context: Context): LogcatHandle? {
        val s = SettingsManager.getSettings(context)
        return when (s.permissionMethod) {
            "shizuku" -> startShizukuLogcat(s.logcatFormat)
            "root" -> startRootLogcat(s.logcatFormat)
            else -> startDirectLogcat(s.logcatFormat)
        }
    }

    fun clearLogcat(context: Context) {
        val s = SettingsManager.getSettings(context)
        try {
            when (s.permissionMethod) {
                "shizuku" -> {
                    if (!isShizukuAvailable()) return
                    val method = Shizuku::class.java.getDeclaredMethod(
                        "newProcess",
                        Array<String>::class.java,
                        Array<String>::class.java,
                        String::class.java
                    )
                    method.isAccessible = true
                    val process = method.invoke(
                        null,
                        arrayOf("logcat", "-c"),
                        null,
                        null
                    )
                    process::class.java.getMethod("waitFor").invoke(process)
                }
                "root" -> {
                    ProcessBuilder("su", "-c", "logcat -c")
                        .redirectErrorStream(true).start().waitFor()
                }
                else -> {
                    ProcessBuilder("logcat", "-c")
                        .redirectErrorStream(true).start().waitFor()
                }
            }
        } catch (_: Exception) {}
    }

    private fun startDirectLogcat(format: String): LogcatHandle? {
        return try {
            val process = ProcessBuilder(
                "logcat", "-v", format
            ).redirectErrorStream(true).start()
            LogcatHandle(process.inputStream, process)
        } catch (_: Exception) { null }
    }

    private fun startShizukuLogcat(format: String): LogcatHandle? {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(
                null,
                arrayOf("logcat", "-v", format),
                null,
                null
            )
            val inputStream = process::class.java.getMethod("getInputStream").invoke(process) as InputStream
            LogcatHandle(inputStream, process)
        } catch (_: Exception) { null }
    }

    private fun startRootLogcat(format: String): LogcatHandle? {
        return try {
            val process = ProcessBuilder(
                "su", "-c", "logcat -v $format"
            ).redirectErrorStream(true).start()
            LogcatHandle(process.inputStream, process)
        } catch (_: Exception) { null }
    }

    class LogcatHandle(
        val inputStream: InputStream,
        private val process: Any?
    ) {
        fun destroy() {
            when (process) {
                is Process -> process.destroy()
                else -> {
                    try {
                        process?.let {
                            it::class.java.getMethod("destroy").invoke(it)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
