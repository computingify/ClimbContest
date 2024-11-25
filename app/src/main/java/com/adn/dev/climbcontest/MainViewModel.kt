package com.adn.dev.climbcontest

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    var isClimberButtonEnabled = mutableStateOf(true)
    var isBlocButtonEnabled = mutableStateOf(true)
}