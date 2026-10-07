package com.yassernull.logcatlivewallpaper.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesTest {
  @Test
  fun testSettingsDefaultWallpaperVideoIsEmpty() {
    val settings = Preferences.Settings()
    assertEquals("", settings.wallpaperVideo)
    assertEquals("", settings.wallpaperVideoName)
    assertEquals("", settings.backgroundImage)
    assertEquals("", settings.backgroundImageName)
  }

  @Test
  fun testSettingsCopyPreservesWallpaperVideo() {
    val settings = Preferences.Settings(
      wallpaperVideo = "/data/video.mp4",
      wallpaperVideoName = "video.mp4",
    )
    val copied = settings.copy(scrollSpeed = 2.5f)
    assertEquals("/data/video.mp4", copied.wallpaperVideo)
    assertEquals("video.mp4", copied.wallpaperVideoName)
    assertEquals(2.5f, copied.scrollSpeed, 0.001f)
  }

  @Test
  fun testMutualExclusivityState() {
    // When video is set, image should be empty
    val videoModeSettings = Preferences.Settings(
      wallpaperVideo = "/path/to/video.mp4",
      wallpaperVideoName = "video.mp4",
      backgroundImage = "",
      backgroundImageName = "",
    )
    assertTrue(videoModeSettings.wallpaperVideo.isNotEmpty())
    assertTrue(videoModeSettings.backgroundImage.isEmpty())

    // When image is set, video should be empty
    val imageModeSettings = Preferences.Settings(
      wallpaperVideo = "",
      wallpaperVideoName = "",
      backgroundImage = "/path/to/image.png",
      backgroundImageName = "image.png",
    )
    assertTrue(imageModeSettings.backgroundImage.isNotEmpty())
    assertTrue(imageModeSettings.wallpaperVideo.isEmpty())
  }
}
