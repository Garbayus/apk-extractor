package com.example.apkextractor.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.apkextractor.data.AppInfo
import com.example.apkextractor.data.ApkExtractor
import com.example.apkextractor.data.DataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
    data class Success(val apps: List<AppInfo>) : MainScreenUiState
}

class MainScreenViewModel(application: Application, private val dataRepository: DataRepository) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _rawApps = MutableStateFlow<List<AppInfo>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps: StateFlow<Boolean> = _showSystemApps

    private val _language = MutableStateFlow(sharedPrefs.getString("lang", "default") ?: "default")
    val language: StateFlow<String> = _language

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    private val _isExtracting = MutableStateFlow<String?>(null)
    val isExtracting: StateFlow<String?> = _isExtracting

    val uiState: StateFlow<MainScreenUiState> = combine(
        _rawApps,
        _searchQuery,
        _showSystemApps
    ) { apps, query, showSystem ->
        if (apps.isEmpty()) {
            MainScreenUiState.Loading
        } else {
            val filtered = apps.filter { app ->
                val matchesSearch = app.name.contains(query, ignoreCase = true) || 
                                    app.packageName.contains(query, ignoreCase = true)
                val matchesSystem = showSystem || !app.isSystem
                matchesSearch && matchesSystem
            }
            MainScreenUiState.Success(filtered)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)

    init {
        loadApps()
    }

    fun loadApps() {
        _rawApps.value = emptyList()
        viewModelScope.launch {
            try {
                dataRepository.getApps(getApplication()).collect { apps ->
                    _rawApps.value = apps
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setShowSystemApps(show: Boolean) {
        _showSystemApps.value = show
    }

    fun setLanguage(lang: String) {
        _language.value = lang
        sharedPrefs.edit().putString("lang", lang).apply()
    }

    fun extractApk(app: AppInfo, successText: String, errorText: String) {
        viewModelScope.launch {
            _isExtracting.value = app.packageName
            try {
                val file = ApkExtractor.extractApk(getApplication(), app)
                if (file != null) {
                    _statusMessage.value = successText
                } else {
                    _statusMessage.value = "$errorText (File null)"
                }
            } catch (e: Exception) {
                _statusMessage.value = "$errorText ${e.localizedMessage}"
            } finally {
                _isExtracting.value = null
            }
        }
    }

    fun shareApk(app: AppInfo) {
        viewModelScope.launch {
            try {
                ApkExtractor.shareApk(getApplication(), app)
            } catch (e: Exception) {
                _statusMessage.value = "Share error: ${e.localizedMessage}"
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
