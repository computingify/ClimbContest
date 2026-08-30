package com.adn.dev.climbcontest

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Une validation passée, telle que le juge doit pouvoir la relire. */
data class Validation(
    val grimpeur: String,
    val bloc: String,
    val heure: String,
    /**
     * La couleur du circuit, telle que le serveur la nomme — « Jaune », « Vert »…
     *
     * Le journal l'affiche en pastille : d'un coup d'oeil, le juge voit sur
     * quels circuits il a validé ces dernières minutes. `null` pour un bloc dont
     * le catalogue ne connaît pas la couleur, et la ligne reste lisible.
     */
    val couleur: String? = null,
)


class MainViewModel : ViewModel() {

    private val _climberId = MutableStateFlow<String?>(null)
    val climberId: StateFlow<String?> = _climberId

    private val _climberName = MutableStateFlow<String?>(null)
    val climberName: StateFlow<String?> = _climberName

    private val _blocId = MutableStateFlow<String?>(null)
    val blocId: StateFlow<String?> = _blocId

    private val _blocName = MutableStateFlow<String?>(null)
    val blocName: StateFlow<String?> = _blocName

    /**
     * La couleur du circuit du bloc scanné.
     *
     * C'est elle qui donne sa couleur à l'écran : la carte du bloc et le bouton
     * « Envoyer » la prennent. Ce n'est pas décoratif — un juge vérifie ainsi
     * qu'il est sur le bon circuit, ce que le tag seul (« ZJ1 ») ne dit pas à
     * quelqu'un qui ne connaît pas la convention de nommage par cœur.
     */
    private val _blocCouleur = MutableStateFlow<String?>(null)
    val blocCouleur: StateFlow<String?> = _blocCouleur

    fun setBlocCouleur(couleur: String?) {
        _blocCouleur.value = couleur
    }

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

    /**
     * Les dernières validations, les plus récentes en tête.
     *
     * Le manque le plus criant de la version précédente : un juge n'avait
     * **aucun moyen** de vérifier ce qu'il venait d'envoyer. Le toast dure deux
     * secondes, dans une salle bruyante où on regarde le mur — et l'écran se
     * vide juste après. « Est-ce que j'ai bien envoyé le bloc de Léa ? » n'avait
     * pas de réponse, sinon aller demander à un organisateur.
     */
    private val _historique = MutableStateFlow<List<Validation>>(emptyList())
    val historique: StateFlow<List<Validation>> = _historique

    fun ajouterAuJournal(validation: Validation) {
        // Cinq lignes : de quoi répondre « oui, c'est parti », pas de quoi
        // transformer l'écran du juge en registre.
        _historique.value = (listOf(validation) + _historique.value).take(5)
    }

    /**
     * Le serveur répond-il ?
     *
     * Trois états, et `null` en est un à part entière : **on est en train de
     * vérifier**. Ce n'est pas « on ne sait pas et on s'en accommode » — c'est
     * l'état affiché à la reprise de l'application, le temps d'un aller-retour.
     *
     * Un juge n'apprenait l'existence d'un problème réseau qu'au moment où
     * quelque chose échouait — donc au pire moment, en plein geste.
     */
    private val _serveurJoignable = MutableStateFlow<Boolean?>(null)
    val serveurJoignable: StateFlow<Boolean?> = _serveurJoignable

    fun setServeurJoignable(joignable: Boolean) {
        _serveurJoignable.value = joignable
    }

    /**
     * « Je vérifie. »
     *
     * Posé au retour au premier plan, **avant** l'aller-retour avec le serveur.
     * Sans lui, la première image affichée serait l'état d'avant la mise en
     * arrière-plan — un vert vieux de vingt minutes, le temps que la réponse
     * arrive. Un voyant qui ment une demi-seconde ment quand même.
     */
    fun setServeurEnVerification() {
        _serveurJoignable.value = null
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
        _blocCouleur.value = null
    }
}