package com.example.apkextractor.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.Locale

data class AppInfo(
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val size: String,
    val isSystem: Boolean,
    val isPlayStore: Boolean,
    val sourceDir: String,
    val splitSourceDirs: List<String>,
    val splitNames: List<String>,
    val supportedAbis: List<String>,
    val minSdk: Int,
    val targetSdk: Int,
    val icon: Drawable
) {
    val isSplitPackage: Boolean
        get() = splitSourceDirs.isNotEmpty()

    val apkFileCount: Int
        get() = 1 + splitSourceDirs.size

    val outputExtension: String
        get() = if (isSplitPackage) "apks" else "apk"

    val abiSummary: String
        get() = supportedAbis.joinToString(", ")
}

object ApkExtractor {
    private const val PLAY_STORE_PACKAGE = "com.android.vending"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val APKS_MIME_TYPE = "application/octet-stream"
    private const val COPY_BUFFER_SIZE = 128 * 1024
    private val knownAbis = listOf(
        "arm64-v8a",
        "armeabi-v7a",
        "armeabi",
        "x86_64",
        "x86",
        "riscv64",
        "mips64",
        "mips"
    )

    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val apps = mutableListOf<AppInfo>()
        val packages = pm.getInstalledPackages(0)

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            val sourceDir = appInfo.sourceDir ?: continue
            if (sourceDir.isEmpty()) continue

            val name = appInfo.loadLabel(pm).toString()
            val packageName = pkg.packageName
            val versionName = pkg.versionName ?: "1.0"
            val versionCode = getVersionCode(pkg)
            
            val file = File(sourceDir)
            if (!file.exists()) continue
            val splitSourceDirs = appInfo.splitSourceDirs
                ?.filter { it.isNotBlank() && File(it).exists() }
                .orEmpty()
            val splitNames = appInfo.splitNames?.toList().orEmpty()
            val sizeBytes = (listOf(sourceDir) + splitSourceDirs).sumOf { File(it).length() }
            val sizeFormatted = formatSize(sizeBytes)

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            
            val installer = try {
                getInstallerPackageName(pm, packageName)
            } catch (e: Exception) {
                null
            }
            val isPlayStore = installer == PLAY_STORE_PACKAGE
            val icon = appInfo.loadIcon(pm)
            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 0
            val supportedAbis = detectSupportedAbis(splitNames, splitSourceDirs)

            apps.add(
                AppInfo(
                    name = name,
                    packageName = packageName,
                    versionName = versionName,
                    versionCode = versionCode,
                    size = sizeFormatted,
                    isSystem = isSystem,
                    isPlayStore = isPlayStore,
                    sourceDir = sourceDir,
                    splitSourceDirs = splitSourceDirs,
                    splitNames = splitNames,
                    supportedAbis = supportedAbis,
                    minSdk = minSdk,
                    targetSdk = appInfo.targetSdkVersion,
                    icon = icon
                )
            )
        }
        
        return apps.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun extractApk(context: Context, app: AppInfo): File? {
        val apkEntries = apkArchiveEntries(app) ?: return null
        val packageName = outputFileName(app)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, packageName)
                put(MediaStore.MediaColumns.MIME_TYPE, outputMimeType(app))
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                writePackageTo(out, app, apkEntries)
            } ?: return null
            return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), packageName)
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val destFile = File(downloadsDir, packageName)
            destFile.outputStream().use { out ->
                writePackageTo(out, app, apkEntries)
            }
            return destFile
        }
    }

    fun shareApk(context: Context, app: AppInfo) {
        val apkEntries = apkArchiveEntries(app) ?: return
        val packageName = outputFileName(app)

        val cacheDir = File(context.cacheDir, "shared_apks")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val destFile = File(cacheDir, packageName)
        if (destFile.exists()) {
            destFile.delete()
        }

        destFile.outputStream().use { out ->
            writePackageTo(out, app, apkEntries)
        }

        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, destFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = outputMimeType(app)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share APK"))
    }

    @Suppress("DEPRECATION")
    private fun getVersionCode(pkg: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkg.longVersionCode
        } else {
            pkg.versionCode.toLong()
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstallerPackageName(pm: PackageManager, packageName: String): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(packageName).installingPackageName
        } else {
            pm.getInstallerPackageName(packageName)
        }
    }

    private fun apkArchiveEntries(app: AppInfo): List<ApkArchiveEntry>? {
        val baseFile = File(app.sourceDir)
        if (!baseFile.isFile) return null

        val entries = mutableListOf(ApkArchiveEntry(baseFile, "base.apk"))
        app.splitSourceDirs.forEachIndexed { index, path ->
            val splitFile = File(path)
            if (!splitFile.isFile) return null
            entries += ApkArchiveEntry(splitFile, splitEntryName(app, splitFile, index))
        }
        return entries
    }

    private fun writePackageTo(outputStream: OutputStream, app: AppInfo, apkEntries: List<ApkArchiveEntry>) {
        if (app.isSplitPackage) {
            val timestamp = System.currentTimeMillis()
            val apkFilesSize = apkEntries.sumOf { it.file.length() }
            SaiApksArchive.write(
                outputStream = outputStream,
                apkEntries = apkEntries,
                metaV1Json = buildSaiMetaV1Json(app, timestamp),
                metaV2Json = buildSaiMetaV2Json(app, timestamp, apkFilesSize),
                iconPng = drawableToPng(app.icon)
            )
        } else {
            BufferedInputStream(apkEntries.first().file.inputStream(), COPY_BUFFER_SIZE).use { input ->
                input.copyTo(outputStream, COPY_BUFFER_SIZE)
            }
        }
    }

    private fun splitEntryName(app: AppInfo, splitFile: File, index: Int): String {
        val fileName = splitFile.name.takeIf { it.endsWith(".apk", ignoreCase = true) }
        val splitName = app.splitNames.getOrNull(index)?.let { "split_${safeZipFileName(it)}.apk" }
        return safeZipFileName(fileName ?: splitName ?: "split_${index + 1}.apk")
    }

    private fun outputFileName(app: AppInfo): String {
        val cleanName = safeFilePart(app.name)
        val cleanVersion = safeFilePart(app.versionName)
        return "${cleanName}_${cleanVersion}.${app.outputExtension}"
    }

    private fun outputMimeType(app: AppInfo): String {
        return if (app.isSplitPackage) APKS_MIME_TYPE else APK_MIME_TYPE
    }

    private fun safeFilePart(value: String): String {
        return value
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_', '.', '-')
            .ifBlank { "app" }
            .take(96)
    }

    private fun safeZipFileName(value: String): String {
        return value
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_', '.', '-')
            .ifBlank { "split.apk" }
    }

    private fun buildSaiMetaV1Json(app: AppInfo, timestamp: Long): String {
        return buildString {
            append('{')
            append("\"export_timestamp\":").append(timestamp).append(',')
            append("\"label\":").append(jsonString(app.name)).append(',')
            append("\"package\":").append(jsonString(app.packageName)).append(',')
            append("\"version_code\":").append(app.versionCode).append(',')
            append("\"version_name\":").append(jsonString(app.versionName))
            append('}')
        }
    }

    private fun buildSaiMetaV2Json(app: AppInfo, timestamp: Long, apkFilesSize: Long): String {
        return buildString {
            append('{')
            append("\"backup_components\":[{\"size\":").append(apkFilesSize).append(",\"type\":\"apk_files\"}],")
            append("\"export_timestamp\":").append(timestamp).append(',')
            append("\"split_apk\":").append(app.isSplitPackage).append(',')
            append("\"label\":").append(jsonString(app.name)).append(',')
            append("\"meta_version\":2,")
            append("\"min_sdk\":").append(app.minSdk).append(',')
            append("\"package\":").append(jsonString(app.packageName)).append(',')
            append("\"target_sdk\":").append(app.targetSdk).append(',')
            append("\"version_code\":").append(app.versionCode).append(',')
            append("\"version_name\":").append(jsonString(app.versionName))
            append('}')
        }
    }

    private fun jsonString(value: String): String {
        return buildString(value.length + 2) {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (char.code < 0x20) {
                            append("\\u")
                            append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            append(char)
                        }
                    }
                }
            }
            append('"')
        }
    }

    private fun drawableToPng(drawable: Drawable): ByteArray? {
        return try {
            val width = drawable.intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(512) ?: 128
            val height = drawable.intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(512) ?: 128
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val oldBounds = drawable.copyBounds()
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            drawable.setBounds(oldBounds)

            val bytes = ByteArrayOutputStream().use { out ->
                if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    out.toByteArray()
                } else {
                    null
                }
            }
            bitmap.recycle()
            bytes
        } catch (e: Exception) {
            null
        }
    }

    private fun detectSupportedAbis(splitNames: List<String>, splitSourceDirs: List<String>): List<String> {
        if (splitNames.isEmpty() && splitSourceDirs.isEmpty()) return emptyList()

        val values = splitNames + splitSourceDirs.map { File(it).nameWithoutExtension }
        return knownAbis.filter { abi ->
            values.any { value -> value.hasAbiToken(abi) }
        }
    }

    private fun String.hasAbiToken(abi: String): Boolean {
        val normalized = lowercase(Locale.US).replace('-', '_')
        val token = Regex.escape(abi.replace('-', '_'))
        return Regex("(^|[^a-z0-9_])$token($|[^a-z0-9_])").containsMatchIn(normalized)
    }

    private fun formatSize(sizeBytes: Long): String {
        return String.format(Locale.US, "%.1f MB", sizeBytes / (1024f * 1024f))
    }
}
