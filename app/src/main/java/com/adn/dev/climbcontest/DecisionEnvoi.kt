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

/** Ce que le juge doit lire après avoir scanné un QR code. */
enum class MessageScan {
    /** Le QR est reconnu par le serveur. */
    ACCEPTE,

    /**
     * Le serveur a compris et répond que ce QR ne correspond à rien.
     * Rescanner, ou aller voir un organisateur.
     */
    REFUSE,

    /**
     * On n'a pas pu joindre le serveur. Le QR est peut-être parfaitement bon —
     * on n'en sait rien. Le juge doit **réessayer**, pas conclure.
     */
    ERREUR_RESEAU,
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
     * Traduit la réponse d'un scan.
     *
     * La distinction est la même qu'à l'envoi, et pour la même raison — mais
     * elle manquait ici, à l'étape qui vient **avant**. Un wifi qui hoquette
     * affichait « Identifiant incorrect. Recommencez. » Le juge en concluait
     * que le QR était mauvais ou que le grimpeur n'était pas inscrit, et allait
     * chercher un organisateur. Rien ne lui disait de réessayer.
     */
    fun apresScan(resultat: ApiResult): MessageScan = when (resultat) {
        is ApiResult.Succes -> MessageScan.ACCEPTE
        is ApiResult.Echec ->
            if (resultat.reseau) MessageScan.ERREUR_RESEAU else MessageScan.REFUSE
    }

    /** Un scan n'est retenu que s'il a été confirmé par le serveur. */
    fun doitRetenirLeScan(message: MessageScan): Boolean = message == MessageScan.ACCEPTE

    /**
     * L'écran ne se vide **que** sur un succès confirmé.
     *
     * C'est la règle la plus importante du fichier : effacer le scan après un
     * échec ferait perdre la réussite sans que personne ne s'en aperçoive.
     */
    fun doitReinitialiser(message: MessageJuge): Boolean = message == MessageJuge.VALIDE

    /**
     * Ce que le **journal des scans** doit retenir d'un envoi par lots.
     *
     * Le piège est là : une réussite refusée figure dans les DEUX listes du
     * bilan. Le serveur a statué sur elle, donc elle est acquittée — elle quitte
     * la file — et elle est refusée. Laisser l'acquittement l'emporter
     * afficherait « arrivé » à côté d'un scan que le serveur vient de rejeter,
     * c'est-à-dire exactement le contraire de la vérité.
     *
     * Un refus l'emporte donc toujours sur l'acquittement qui l'accompagne.
     */
    fun pourLeJournal(bilan: BilanEnvoi): List<SuiteDeScan> {
        val refus = bilan.refusees.associateBy { it.ref }
        return bilan.acquittees.map { ref ->
            refus[ref]
                ?.let { SuiteDeScan(ref, EtatScan.REFUSEE, it.message) }
                ?: SuiteDeScan(ref, EtatScan.PARTIE, null)
        }
    }
}

/** Le sort d'un scan, tel que le journal doit l'inscrire. */
data class SuiteDeScan(val ref: String, val etat: EtatScan, val motif: String?)
