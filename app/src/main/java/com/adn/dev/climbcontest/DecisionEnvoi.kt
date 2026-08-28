package com.adn.dev.climbcontest

/**
 * Ce que le juge doit lire après avoir appuyé sur « Envoyer ».
 *
 * Volontairement séparé des ressources Android : cet énuméré ne connaît ni
 * `R.string`, ni `Toast`, ni `Context`, donc [DecisionEnvoi] est testable sur la
 * JVM. La traduction en texte affichable reste dans [Server], où elle n'est plus
 * qu'un `when` exhaustif sans logique.
 */
enum class MessageJuge {
    /** Rien n'a été scanné : il n'y a pas de requête à faire. */
    RIEN_A_ENVOYER,

    /** La réussite est enregistrée. C'est le seul cas où l'écran se vide. */
    VALIDE,

    /**
     * On n'a pas pu parler au serveur, ou il est tombé. Le juge doit
     * **réessayer** : l'envoi est idempotent, un doublon ne coûte rien.
     */
    ERREUR_RESEAU,

    /**
     * Le serveur a compris et a refusé (dossard inconnu, bloc inconnu, aucune
     * compétition active). Réessayer ne servirait à rien : il faut rescanner.
     */
    ENVOI_REFUSE,
}

/**
 * La logique de décision de l'écran d'envoi, isolée de l'interface.
 *
 * Elle vivait dans une coroutine, entre un `ViewModel` et un `Toast` — donc
 * intestable sans émulateur, alors que c'est précisément elle qui détermine ce
 * qu'un juge fait ensuite : réessayer, ou passer au grimpeur suivant. Une erreur
 * ici se traduit par des réussites perdues un dimanche matin, sans trace.
 */
object DecisionEnvoi {

    /**
     * Décide avant même d'appeler le serveur.
     *
     * Renvoie `null` quand l'envoi peut partir, sinon le message à afficher.
     * Le bouton « Envoyer » reste cliquable en permanence : partir en silence
     * rendrait l'écran inerte, et un juge qui doute d'avoir appuyé ne pourrait
     * pas distinguer « ignoré » de « planté ».
     */
    fun avantEnvoi(dossard: String?, bloc: String?): MessageJuge? =
        if (dossard.isNullOrBlank() || bloc.isNullOrBlank()) MessageJuge.RIEN_A_ENVOYER else null

    /** Traduit la réponse du serveur en consigne pour le juge. */
    fun apresEnvoi(resultat: ApiResult): MessageJuge = when (resultat) {
        is ApiResult.Succes -> MessageJuge.VALIDE
        is ApiResult.Echec ->
            if (resultat.reseau) MessageJuge.ERREUR_RESEAU else MessageJuge.ENVOI_REFUSE
    }

    /**
     * L'écran ne se vide **que** sur un succès confirmé.
     *
     * C'est la règle la plus importante du fichier : effacer le scan après un
     * échec ferait perdre la réussite sans que personne ne s'en aperçoive.
     */
    fun doitReinitialiser(message: MessageJuge): Boolean = message == MessageJuge.VALIDE
}
