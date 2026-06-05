package com.example.apkextractor.ui

interface AppStrings {
    val appTitle: String
    val searchPlaceholder: String
    val showSystemApps: String
    val versionLabel: String
    val sizeLabel: String
    val formatLabel: String
    val sourcePathLabel: String
    val splitPackageLabel: String
    val abiLabel: String
    val systemAppLabel: String
    val playStoreLabel: String
    val extractButton: String
    val shareButton: String
    val statusExtracting: String
    val statusSuccess: String
    val statusError: String
    val languageLabel: String
    val systemDefault: String
    val english: String
    val turkish: String
}

object EnStrings : AppStrings {
    override val appTitle = "APK Extractor"
    override val searchPlaceholder = "Search installed apps..."
    override val showSystemApps = "Show System Apps"
    override val versionLabel = "Version"
    override val sizeLabel = "Size"
    override val formatLabel = "Format"
    override val sourcePathLabel = "Source APKs"
    override val splitPackageLabel = "SAI package"
    override val abiLabel = "CPU ABI"
    override val systemAppLabel = "System"
    override val playStoreLabel = "Play Store"
    override val extractButton = "Extract"
    override val shareButton = "Share"
    override val statusExtracting = "Extracting package..."
    override val statusSuccess = "Package saved to Downloads!"
    override val statusError = "Extraction failed: "
    override val languageLabel = "Language"
    override val systemDefault = "System Default"
    override val english = "English"
    override val turkish = "Turkish"
}

object TrStrings : AppStrings {
    override val appTitle = "APK \u00C7\u0131kar\u0131c\u0131"
    override val searchPlaceholder = "Y\u00FCkl\u00FC uygulamalar\u0131 ara..."
    override val showSystemApps = "Sistem uygulamalar\u0131n\u0131 g\u00F6ster"
    override val versionLabel = "S\u00FCr\u00FCm"
    override val sizeLabel = "Boyut"
    override val formatLabel = "Format"
    override val sourcePathLabel = "Kaynak APK'lar"
    override val splitPackageLabel = "SAI paketi"
    override val abiLabel = "\u0130\u015Flemci"
    override val systemAppLabel = "Sistem"
    override val playStoreLabel = "Play Store"
    override val extractButton = "D\u0131\u015Fa aktar"
    override val shareButton = "Payla\u015F"
    override val statusExtracting = "Paket d\u0131\u015Fa aktar\u0131l\u0131yor..."
    override val statusSuccess = "Paket \u0130ndirilenler klas\u00F6r\u00FCne kaydedildi!"
    override val statusError = "D\u0131\u015Fa aktarma ba\u015Far\u0131s\u0131z: "
    override val languageLabel = "Dil"
    override val systemDefault = "Sistem varsay\u0131lan\u0131"
    override val english = "\u0130ngilizce"
    override val turkish = "T\u00FCrk\u00E7e"
}

fun getAppStrings(langCode: String): AppStrings {
    return when (langCode) {
        "tr" -> TrStrings
        "en" -> EnStrings
        else -> {
            val defaultLang = java.util.Locale.getDefault().language
            if (defaultLang == "tr") TrStrings else EnStrings
        }
    }
}
