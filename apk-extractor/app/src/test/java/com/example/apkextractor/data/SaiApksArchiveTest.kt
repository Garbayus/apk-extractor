package com.example.apkextractor.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

class SaiApksArchiveTest {
    @Test
    fun write_createsSaiCompatibleArchiveWithApksAndMetadata() {
        val tempDir = Files.createTempDirectory("sai-apks-test").toFile()
        try {
            val baseApk = tempDir.fileWithBytes("base-source.apk", byteArrayOf(1, 2, 3))
            val abiSplit = tempDir.fileWithBytes("split_config.arm64_v8a.apk", byteArrayOf(4, 5))
            val densitySplit = tempDir.fileWithBytes("split_config.xxhdpi.apk", byteArrayOf(6))
            val output = File(tempDir, "chrome.apks")

            output.outputStream().use { out ->
                SaiApksArchive.write(
                    outputStream = out,
                    apkEntries = listOf(
                        ApkArchiveEntry(baseApk, "base.apk"),
                        ApkArchiveEntry(abiSplit, "split_config.arm64_v8a.apk"),
                        ApkArchiveEntry(densitySplit, "split_config.xxhdpi.apk")
                    ),
                    metaV1Json = """{"package":"com.android.chrome"}""",
                    metaV2Json = """{"split_apk":true}""",
                    iconPng = byteArrayOf(7, 8, 9)
                )
            }

            ZipFile(output).use { zip ->
                assertNotNull(zip.getEntry("base.apk"))
                assertNotNull(zip.getEntry("split_config.arm64_v8a.apk"))
                assertNotNull(zip.getEntry("split_config.xxhdpi.apk"))
                assertEquals("""{"package":"com.android.chrome"}""", zip.readText("meta.sai_v1.json"))
                assertEquals("""{"split_apk":true}""", zip.readText("meta.sai_v2.json"))
                assertArrayEquals(byteArrayOf(7, 8, 9), zip.readBytes("icon.png"))
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun File.fileWithBytes(name: String, bytes: ByteArray): File {
        return File(this, name).also { it.writeBytes(bytes) }
    }

    private fun ZipFile.readText(entryName: String): String {
        return getInputStream(getEntry(entryName)).use { it.reader(Charsets.UTF_8).readText() }
    }

    private fun ZipFile.readBytes(entryName: String): ByteArray {
        return getInputStream(getEntry(entryName)).use { it.readBytes() }
    }
}
