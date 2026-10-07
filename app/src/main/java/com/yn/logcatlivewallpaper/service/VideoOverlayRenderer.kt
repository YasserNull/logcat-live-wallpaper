/*
 * OpenGL ES 2.0 renderer for compositing video playback with LogCat text overlay.
 */
package com.yn.logcatlivewallpaper.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import com.yn.logcatlivewallpaper.core.LogCatRenderer
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class VideoOverlayRenderer(
  private val surface: Surface,
  private var surfaceWidth: Int,
  private var surfaceHeight: Int,
  private val logRenderer: LogCatRenderer,
) {
  private val tag = "VideoOverlayRenderer"

  private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
  private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
  private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

  private var videoProgram = 0
  private var videoPositionHandle = 0
  private var videoTexCoordHandle = 0
  private var videoMvpMatrixHandle = 0
  private var videoStMatrixHandle = 0
  private var videoTextureHandle = 0

  private var overlayProgram = 0
  private var overlayPositionHandle = 0
  private var overlayTexCoordHandle = 0
  private var overlayTextureHandle = 0

  private var videoTextureId = 0
  private var overlayTextureId = 0

  private var surfaceTexture: SurfaceTexture? = null
  private var videoSurface: Surface? = null
  private var mediaPlayer: MediaPlayer? = null
  private var currentVideoPath: String = ""
  private var videoWidth = 0
  private var videoHeight = 0

  private val stMatrix = FloatArray(16)
  private val mvpMatrix = FloatArray(16)

  private var overlayBitmap: Bitmap? = null
  private var overlayCanvas: Canvas? = null

  private val quadVertices: FloatBuffer
  private val quadTexCoords: FloatBuffer

  init {
    val vertexCoords = floatArrayOf(
      -1f,
      -1f,
      1f,
      -1f,
      -1f,
      1f,
      1f,
      1f,
    )
    val texCoords = floatArrayOf(
      0f,
      0f,
      1f,
      0f,
      0f,
      1f,
      1f,
      1f,
    )

    quadVertices = ByteBuffer.allocateDirect(vertexCoords.size * 4)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()
      .put(vertexCoords)
    quadVertices.position(0)

    quadTexCoords = ByteBuffer.allocateDirect(texCoords.size * 4)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()
      .put(texCoords)
    quadTexCoords.position(0)

    Matrix.setIdentityM(stMatrix, 0)
    Matrix.setIdentityM(mvpMatrix, 0)

    initEgl()
    initGl()
    initOverlayBuffer(surfaceWidth, surfaceHeight)
  }

  private fun initEgl() {
    eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
      Log.e(tag, "eglGetDisplay failed")
      return
    }

    val version = IntArray(2)
    if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
      Log.e(tag, "eglInitialize failed")
      return
    }

    val attribList = intArrayOf(
      EGL14.EGL_RED_SIZE, 8,
      EGL14.EGL_GREEN_SIZE, 8,
      EGL14.EGL_BLUE_SIZE, 8,
      EGL14.EGL_ALPHA_SIZE, 8,
      EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
      EGL14.EGL_NONE,
    )

    val configs = arrayOfNulls<EGLConfig>(1)
    val numConfigs = IntArray(1)
    if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
      Log.e(tag, "eglChooseConfig failed")
      return
    }

    val contextAttribs = intArrayOf(
      EGL14.EGL_CONTEXT_CLIENT_VERSION,
      2,
      EGL14.EGL_NONE,
    )

    eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
    if (eglContext == EGL14.EGL_NO_CONTEXT) {
      Log.e(tag, "eglCreateContext failed")
      return
    }

    val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
    eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfaceAttribs, 0)
    if (eglSurface == EGL14.EGL_NO_SURFACE) {
      Log.e(tag, "eglCreateWindowSurface failed")
      return
    }

    if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
      Log.e(tag, "eglMakeCurrent failed")
    }
  }

  private fun initGl() {
    val videoVertexShaderSrc = """
      uniform mat4 uMVPMatrix;
      uniform mat4 uSTMatrix;
      attribute vec4 aPosition;
      attribute vec4 aTextureCoord;
      varying vec2 vTextureCoord;
      void main() {
        gl_Position = uMVPMatrix * aPosition;
        vTextureCoord = (uSTMatrix * aTextureCoord).xy;
      }
    """.trimIndent()

    val videoFragmentShaderSrc = """
      #extension GL_OES_EGL_image_external : require
      precision mediump float;
      varying vec2 vTextureCoord;
      uniform samplerExternalOES sTexture;
      void main() {
        gl_FragColor = texture2D(sTexture, vTextureCoord);
      }
    """.trimIndent()

    videoProgram = createProgram(videoVertexShaderSrc, videoFragmentShaderSrc)
    videoPositionHandle = GLES20.glGetAttribLocation(videoProgram, "aPosition")
    videoTexCoordHandle = GLES20.glGetAttribLocation(videoProgram, "aTextureCoord")
    videoMvpMatrixHandle = GLES20.glGetUniformLocation(videoProgram, "uMVPMatrix")
    videoStMatrixHandle = GLES20.glGetUniformLocation(videoProgram, "uSTMatrix")
    videoTextureHandle = GLES20.glGetUniformLocation(videoProgram, "sTexture")

    val overlayVertexShaderSrc = """
      attribute vec4 aPosition;
      attribute vec2 aTextureCoord;
      varying vec2 vTextureCoord;
      void main() {
        gl_Position = aPosition;
        vTextureCoord = vec2(aTextureCoord.x, 1.0 - aTextureCoord.y);
      }
    """.trimIndent()

    val overlayFragmentShaderSrc = """
      precision mediump float;
      varying vec2 vTextureCoord;
      uniform sampler2D sTexture;
      void main() {
        gl_FragColor = texture2D(sTexture, vTextureCoord);
      }
    """.trimIndent()

    overlayProgram = createProgram(overlayVertexShaderSrc, overlayFragmentShaderSrc)
    overlayPositionHandle = GLES20.glGetAttribLocation(overlayProgram, "aPosition")
    overlayTexCoordHandle = GLES20.glGetAttribLocation(overlayProgram, "aTextureCoord")
    overlayTextureHandle = GLES20.glGetUniformLocation(overlayProgram, "sTexture")

    // Create OES video texture
    val textures = IntArray(2)
    GLES20.glGenTextures(2, textures, 0)
    videoTextureId = textures[0]
    overlayTextureId = textures[1]

    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

    surfaceTexture = SurfaceTexture(videoTextureId)
    videoSurface = Surface(surfaceTexture)
  }

  fun updateSurfaceDimensions(width: Int, height: Int) {
    if (width <= 0 || height <= 0) return
    surfaceWidth = width
    surfaceHeight = height
    initOverlayBuffer(width, height)
    updateMvpMatrix()
  }

  private fun initOverlayBuffer(width: Int, height: Int) {
    if (width <= 0 || height <= 0) return
    overlayBitmap?.recycle()
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    overlayBitmap = bmp
    overlayCanvas = Canvas(bmp)

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
  }

  fun startVideo(videoPath: String, isVisible: Boolean) {
    if (videoPath.isEmpty()) {
      stopVideo()
      return
    }
    if (mediaPlayer != null && currentVideoPath == videoPath) {
      if (isVisible) {
        resumeVideo()
      }
      return
    }

    stopVideo()
    val file = File(videoPath)
    if (!file.exists() || file.length() == 0L) {
      Log.w(tag, "Video file does not exist: $videoPath")
      return
    }

    try {
      val mp = MediaPlayer().apply {
        videoSurface?.let { setSurface(it) }
        FileInputStream(file).use { fis ->
          setDataSource(fis.fd, 0, file.length())
        }
        isLooping = true
        setVolume(0f, 0f)
        setOnVideoSizeChangedListener { _, w, h ->
          if (w > 0 && h > 0) {
            this@VideoOverlayRenderer.videoWidth = w
            this@VideoOverlayRenderer.videoHeight = h
            updateMvpMatrix()
          }
        }
        setOnPreparedListener { player ->
          if (isVisible) {
            player.start()
          }
        }
        setOnErrorListener { _, what, extra ->
          Log.e(tag, "MediaPlayer error: what=$what, extra=$extra")
          stopVideo()
          true
        }
        prepareAsync()
      }
      mediaPlayer = mp
      currentVideoPath = videoPath
    } catch (e: Exception) {
      Log.e(tag, "Failed to start video playback for: $videoPath", e)
      stopVideo()
    }
  }

  fun resumeVideo() {
    try {
      if (mediaPlayer?.isPlaying != true && currentVideoPath.isNotEmpty()) {
        mediaPlayer?.start()
      }
    } catch (e: Exception) {
      Log.e(tag, "Error resuming video", e)
    }
  }

  fun pauseVideo() {
    try {
      if (mediaPlayer?.isPlaying == true) {
        mediaPlayer?.pause()
      }
    } catch (e: Exception) {
      Log.e(tag, "Error pausing video", e)
    }
  }

  fun stopVideo() {
    try {
      mediaPlayer?.let { mp ->
        if (mp.isPlaying) {
          mp.stop()
        }
        mp.reset()
        mp.release()
      }
    } catch (e: Exception) {
      Log.e(tag, "Error stopping video", e)
    }
    mediaPlayer = null
    currentVideoPath = ""
    videoWidth = 0
    videoHeight = 0
  }

  private fun updateMvpMatrix() {
    Matrix.setIdentityM(mvpMatrix, 0)
    if (surfaceWidth == 0 || surfaceHeight == 0 || videoWidth == 0 || videoHeight == 0) {
      return
    }

    val viewRatio = surfaceWidth.toFloat() / surfaceHeight
    val videoRatio = videoWidth.toFloat() / videoHeight

    val scaleX: Float
    val scaleY: Float
    if (videoRatio > viewRatio) {
      scaleX = videoRatio / viewRatio
      scaleY = 1.0f
    } else {
      scaleX = 1.0f
      scaleY = viewRatio / videoRatio
    }

    Matrix.scaleM(mvpMatrix, 0, scaleX, scaleY, 1f)
  }

  fun drawFrame(frameTimeNanos: Long) {
    if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglSurface == EGL14.EGL_NO_SURFACE) {
      return
    }

    if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
      return
    }

    GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
    GLES20.glClearColor(0f, 0f, 0f, 1f)
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

    // 1. Render Video
    surfaceTexture?.let { st ->
      try {
        st.updateTexImage()
        st.getTransformMatrix(stMatrix)
      } catch (_: Exception) {}

      GLES20.glUseProgram(videoProgram)
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
      GLES20.glUniform1i(videoTextureHandle, 0)
      GLES20.glUniformMatrix4fv(videoMvpMatrixHandle, 1, false, mvpMatrix, 0)
      GLES20.glUniformMatrix4fv(videoStMatrixHandle, 1, false, stMatrix, 0)

      GLES20.glEnableVertexAttribArray(videoPositionHandle)
      GLES20.glVertexAttribPointer(videoPositionHandle, 2, GLES20.GL_FLOAT, false, 0, quadVertices)

      GLES20.glEnableVertexAttribArray(videoTexCoordHandle)
      GLES20.glVertexAttribPointer(videoTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, quadTexCoords)

      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

      GLES20.glDisableVertexAttribArray(videoPositionHandle)
      GLES20.glDisableVertexAttribArray(videoTexCoordHandle)
    }

    // 2. Render LogCat Text Overlay
    val bmp = overlayBitmap
    val canvas = overlayCanvas
    if (bmp != null && canvas != null) {
      bmp.eraseColor(Color.TRANSPARENT)
      logRenderer.draw(canvas, frameTimeNanos)

      GLES20.glEnable(GLES20.GL_BLEND)
      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
      GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bmp)

      GLES20.glUseProgram(overlayProgram)
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
      GLES20.glUniform1i(overlayTextureHandle, 0)

      GLES20.glEnableVertexAttribArray(overlayPositionHandle)
      GLES20.glVertexAttribPointer(overlayPositionHandle, 2, GLES20.GL_FLOAT, false, 0, quadVertices)

      GLES20.glEnableVertexAttribArray(overlayTexCoordHandle)
      GLES20.glVertexAttribPointer(overlayTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, quadTexCoords)

      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

      GLES20.glDisableVertexAttribArray(overlayPositionHandle)
      GLES20.glDisableVertexAttribArray(overlayTexCoordHandle)
      GLES20.glDisable(GLES20.GL_BLEND)
    }

    EGL14.eglSwapBuffers(eglDisplay, eglSurface)
  }

  fun release() {
    stopVideo()

    videoSurface?.release()
    videoSurface = null
    surfaceTexture?.release()
    surfaceTexture = null

    overlayBitmap?.recycle()
    overlayBitmap = null
    overlayCanvas = null

    if (videoTextureId != 0 || overlayTextureId != 0) {
      val textures = intArrayOf(videoTextureId, overlayTextureId)
      GLES20.glDeleteTextures(2, textures, 0)
      videoTextureId = 0
      overlayTextureId = 0
    }

    if (videoProgram != 0) {
      GLES20.glDeleteProgram(videoProgram)
      videoProgram = 0
    }
    if (overlayProgram != 0) {
      GLES20.glDeleteProgram(overlayProgram)
      overlayProgram = 0
    }

    if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
      EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
      if (eglSurface != EGL14.EGL_NO_SURFACE) {
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
        eglSurface = EGL14.EGL_NO_SURFACE
      }
      if (eglContext != EGL14.EGL_NO_CONTEXT) {
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        eglContext = EGL14.EGL_NO_CONTEXT
      }
      EGL14.eglTerminate(eglDisplay)
      eglDisplay = EGL14.EGL_NO_DISPLAY
    }
  }

  private fun createProgram(vertexSource: String, fragmentSource: String): Int {
    val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
    if (vertexShader == 0) return 0
    val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
    if (pixelShader == 0) return 0

    var program = GLES20.glCreateProgram()
    if (program != 0) {
      GLES20.glAttachShader(program, vertexShader)
      GLES20.glAttachShader(program, pixelShader)
      GLES20.glLinkProgram(program)
      val linkStatus = IntArray(1)
      GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
      if (linkStatus[0] != GLES20.GL_TRUE) {
        Log.e(tag, "Could not link program: ${GLES20.glGetProgramInfoLog(program)}")
        GLES20.glDeleteProgram(program)
        program = 0
      }
    }
    return program
  }

  private fun loadShader(shaderType: Int, source: String): Int {
    var shader = GLES20.glCreateShader(shaderType)
    if (shader != 0) {
      GLES20.glShaderSource(shader, source)
      GLES20.glCompileShader(shader)
      val compiled = IntArray(1)
      GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
      if (compiled[0] == 0) {
        Log.e(tag, "Could not compile shader $shaderType: ${GLES20.glGetShaderInfoLog(shader)}")
        GLES20.glDeleteShader(shader)
        shader = 0
      }
    }
    return shader
  }
}
