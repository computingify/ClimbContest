package com.adn.dev.climbcontest

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel : ViewModel() {
    private val _serverAddress = MutableStateFlow("192.168.1.100")
    val serverAddress: StateFlow<String> = _serverAddress

    private val _climberId = MutableStateFlow<String?>(null)
    val climberId: StateFlow<String?> = _climberId

    private val _climberName = MutableStateFlow<String?>(null)
    val climberName: StateFlow<String?> = _climberName

    private val _blocId = MutableStateFlow<String?>(null)
    val blocId: StateFlow<String?> = _blocId

    private val _blocName = MutableStateFlow<String?>(null)
    val blocName: StateFlow<String?> = _blocName

    fun updateServerAddress(newAddress: String) {
        _serverAddress.value = newAddress
    }

    fun loadServerAddress(context: Context) {
        val sharedPrefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        _serverAddress.value = sharedPrefs.getString("BASE_URL", _serverAddress.value) ?: _serverAddress.value
    }

    fun saveServerAddress(context: Context) {
        val sharedPrefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putString("BASE_URL", _serverAddress.value)
            apply()
        }
    }

    fun setClimberId(id: String?) {
        _climberId.value = id
    }

    fun setClimberName(id: String?) {
        _climberName.value = id
    }

    fun setBlocId(id: String?) {
        _blocId.value = id
    }

    fun setBlocName(id: String?) {
        _blocName.value = id
    }

    fun reset() {
        _climberId.value = null
        _climberName.value = null
        _blocId.value = null
        _blocName.value = null
    }
}