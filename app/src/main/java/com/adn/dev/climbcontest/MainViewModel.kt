package com.adn.dev.climbcontest

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel : ViewModel() {

    private val _climberId = MutableStateFlow<String?>(null)
    val climberId: StateFlow<String?> = _climberId

    private val _climberName = MutableStateFlow<String?>(null)
    val climberName: StateFlow<String?> = _climberName

    private val _blocId = MutableStateFlow<String?>(null)
    val blocId: StateFlow<String?> = _blocId

    private val _blocName = MutableStateFlow<String?>(null)
    val blocName: StateFlow<String?> = _blocName

    private val _autoEval = false
    var autoEval = _autoEval

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

    fun enableAutoEval() {
        autoEval = true
    }

    fun disableAutoEval() {
        autoEval = false
    }

    fun reset(all: Boolean = true) {
        if (all) {
            _climberId.value = null
            _climberName.value = null
        }
        _blocId.value = null
        _blocName.value = null
    }
}