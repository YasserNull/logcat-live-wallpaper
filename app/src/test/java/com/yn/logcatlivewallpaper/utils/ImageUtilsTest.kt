package com.yn.logcatlivewallpaper.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageUtilsTest {
  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun testIsGifWithGif89aHeader() {
    val file = tempFolder.newFile("sample.bin")
    file.writeBytes(byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00))
    assertTrue(ImageUtils.isGif(file))
    assertTrue(ImageUtils.isGif(file.absolutePath))
  }

  @Test
  fun testIsGifWithGif87aHeader() {
    val file = tempFolder.newFile("image_without_ext")
    file.writeBytes(byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x37, 0x61, 0x00, 0x00))
    assertTrue(ImageUtils.isGif(file))
  }

  @Test
  fun testIsGifWithGifExtension() {
    val file = tempFolder.newFile("animation.gif")
    file.writeBytes(byteArrayOf(0x00, 0x01, 0x02))
    assertTrue(ImageUtils.isGif(file))
  }

  @Test
  fun testIsGifReturnsFalseForPng() {
    val file = tempFolder.newFile("photo.png")
    file.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
    assertFalse(ImageUtils.isGif(file))
  }

  @Test
  fun testIsGifReturnsFalseForShortOrEmptyFile() {
    val emptyFile = tempFolder.newFile("empty.bin")
    assertFalse(ImageUtils.isGif(emptyFile))

    val shortFile = tempFolder.newFile("short.bin")
    shortFile.writeBytes(byteArrayOf(0x47, 0x49))
    assertFalse(ImageUtils.isGif(shortFile))
  }

  @Test
  fun testIsGifReturnsFalseForNonExistentFile() {
    assertFalse(ImageUtils.isGif("/path/that/does/not/exist.png"))
    assertFalse(ImageUtils.isGif(""))
  }
}
