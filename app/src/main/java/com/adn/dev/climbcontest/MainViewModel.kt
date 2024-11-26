package com.adn.dev.climbcontest

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class MainViewModel : ViewModel() {
    var isClimberButtonEnabled = mutableStateOf(true)
    var isBlocButtonEnabled = mutableStateOf(true)

    private val _climberId = MutableStateFlow<String?>(null)
    val climberId: StateFlow<String?> = _climberId

    private val _blocId = MutableStateFlow<String?>(null)
    val blocId: StateFlow<String?> = _blocId

    private val _uuid = MutableStateFlow<String>(UUID.randomUUID().toString())
    val uuid: StateFlow<String> = _uuid

    fun setClimberId(id: String?) {
        _climberId.value = id
    }

    fun setBlocId(id: String?) {
        _blocId.value = id
    }

    fun reset() {
        _climberId.value = null
        _blocId.value = null
        _uuid.value = UUID.randomUUID().toString()
    }
}