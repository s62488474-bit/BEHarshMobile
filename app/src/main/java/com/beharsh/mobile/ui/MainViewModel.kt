package com.beharsh.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beharsh.mobile.data.SettingsRepository
import com.beharsh.mobile.model.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    init { load() }

    fun load() {
        viewModelScope.launch {
            _settings.value = withContext(Dispatchers.IO) { repo.load() }
        }
    }

    fun save(s: Settings) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.save(s) }
            _settings.value = s
        }
    }
}
