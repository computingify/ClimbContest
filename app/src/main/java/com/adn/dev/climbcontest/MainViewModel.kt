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

    /**
     * Combien de réussites attendent encore d'atteindre le serveur.
     *
     * Le juge doit pouvoir le voir. Sans indicateur, une file qui ne part
     * jamais — backend éteint, wifi coupé toute la matinée — serait invisible
     * jusqu'au dépouillement.
     */
    private val _enAttente = MutableStateFlow(0)
    val enAttente: StateFlow<Int> = _enAttente

    fun setEnAttente(n: Int) {
        _enAttente.value = n
    }

    /**
     * Les réussites que le serveur a refusées, et qui attendent une décision.
     *
     * Presque toujours « ce dossard n'existe pas **encore** » : le participant
     * s'est inscrit à 9 h et l'organisateur ne l'a pas encore ajouté. Elles
     * étaient jetées ; le grimpeur perdait son bloc sans que personne le voie.
     */
    private val _refusees = MutableStateFlow(0)
    val refusees: StateFlow<Int> = _refusees

    fun setRefusees(n: Int) {
        _refusees.value = n
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