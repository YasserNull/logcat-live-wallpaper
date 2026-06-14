/*
* Manages logcat access through Shizuku root or normal shell.
*/
package com.yn.logcatlivewallpaper.core

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.InputStream

enum class PermissionMethod { SHIZUKU, ROOT }

object PermissionManager {
    private const val SHIZUKU_REQUEST_CODE = 1001

    private var shizukuListenerRegistered = false

    private val shizukuPermissionListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            // Shizuku permission is enough to run logcat through Shizuku.newProcess.
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

    fun preferredAvailableMethod(): String {
        return when {
            isShizukuAvailable() -> "shizuku"
            isRootAvailable() -> "root"
            else -> "none"
        }
    }

    fun activate(method: String): Boolean {
        return when (method) {
            "shizuku" -> activateShizuku()
            "root" -> activateRoot()
            else -> false
        }
    }

    fun sanitizeSavedMethod(context: android.content.Context): String {
        val settings = Preferences.getSettings(context)
        if (isMethodReady(settings.permissionMethod)) {
            return settings.permissionMethod
        }
        if (settings.permissionMethod == "shizuku" || settings.permissionMethod == "root") {
            Preferences.saveSettings(context, settings.copy(permissionMethod = "none"))
        }
        return "none"
    }

    private fun isMethodReady(method: String): Boolean {
        return when (method) {
            "shizuku" -> isShizukuAvailable() && hasShizukuPermission()
            "root" -> isRootAvailable()
            else -> true
        }
    }

    private fun activateShizuku(): Boolean {
        if (!isShizukuAvailable()) return false
        ensureShizukuListener()
        if (hasShizukuPermission()) {
            return true
        }
        try {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        } catch (_: Exception) {}
        return false
    }

    private fun activateRoot(): Boolean {
        return isRootAvailable()
    }

    fun startLogcat(context: android.content.Context): LogcatHandle? {
        val s = Preferences.getSettings(context)
        val method = sanitizeSavedMethod(context)
        return when (method) {
            "shizuku" -> startShizukuCommand(s.logcatCommand)
            "root" -> startRootCommand(s.logcatCommand)
            else -> startDirectCommand(s.logcatCommand)
        }
    }

    fun clearLogcat(context: android.content.Context) {
        val s = Preferences.getSettings(context)
        val method = sanitizeSavedMethod(context)
        try {
            when (method) {
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

    private fun startDirectCommand(command: String): LogcatHandle? {
        return try {
            val process = ProcessBuilder(
                "sh", "-c", command.ifBlank { Preferences.DEFAULT_LOGCAT_COMMAND }
            ).redirectErrorStream(true).start()
            LogcatHandle(process.inputStream, process)
        } catch (_: Exception) { null }
    }

    private fun startShizukuCommand(command: String): LogcatHandle? {
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
                arrayOf("sh", "-c", command.ifBlank { Preferences.DEFAULT_LOGCAT_COMMAND }),
                null,
                null
            )
            val inputStream = process::class.java.getMethod("getInputStream").invoke(process) as InputStream
            LogcatHandle(inputStream, process)
        } catch (_: Exception) { null }
    }

    private fun startRootCommand(command: String): LogcatHandle? {
        return try {
            val process = ProcessBuilder(
                "su", "-c", command.ifBlank { Preferences.DEFAULT_LOGCAT_COMMAND }
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
