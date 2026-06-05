package com.example.apkextractor.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class ApkArchiveEntry(
    val file: File,
    val entryName: String
)

internal object SaiApksArchive {
    private const val BUFFER_SIZE = 128 * 1024

    fun write(
        outputStream: OutputStream,
        apkEntries: List<ApkArchiveEntry>,
        metaV1Json: String,
        metaV2Json: String,
        iconPng: ByteArray? = null
    ) {
        require(apkEntries.isNotEmpty()) { "At least one APK entry is required" }

        ZipOutputStream(BufferedOutputStream(outputStream, BUFFER_SIZE)).use { zip ->
            iconPng?.let { bytes ->
                zip.putNextEntry(ZipEntry("icon.png"))
                zip.write(bytes)
                zip.closeEntry()
            }

            zip.putTextEntry("meta.sai_v1.json", metaV1Json)
            zip.putTextEntry("meta.sai_v2.json", metaV2Json)

            val usedNames = mutableSetOf<String>()
            apkEntries.forEach { entry ->
                val safeEntryName = uniqueEntryName(entry.entryName, usedNames)
                zip.putNextEntry(ZipEntry(safeEntryName))
                BufferedInputStream(entry.file.inputStream(), BUFFER_SIZE).use { input ->
                    input.copyTo(zip, BUFFER_SIZE)
                }
                zip.closeEntry()
            }

            zip.finish()
        }
    }

    private fun ZipOutputStream.putTextEntry(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun uniqueEntryName(name: String, usedNames: MutableSet<String>): String {
        val cleaned = name
            .replace('\\', '/')
            .substringAfterLast('/')
            .ifBlank { "split.apk" }

        if (usedNames.add(cleaned)) return cleaned

        val baseName = cleaned.substringBeforeLast('.', cleaned)
        val extension = cleaned.substringAfterLast('.', "")
        var index = 2
        while (true) {
            val candidate = if (extension.isBlank()) {
                "${baseName}_$index"
            } else {
                "${baseName}_$index.$extension"
            }
            if (usedNames.add(candidate)) return candidate
            index++
        }
    }
}
