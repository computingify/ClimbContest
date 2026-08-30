package com.adn.dev.climbcontest

/**
 * Quand faut-il envoyer ? Décision pure, sans réseau ni horloge implicite.
 *
 * Trois règles, et un compromis assumé entre deux choses qui s'opposent :
 * envoyer souvent fait des requêtes, envoyer rarement fait un écran de
 * résultats en retard.
 */
object PolitiqueEnvoi {

    /** Au-delà, on part sans attendre. Cinq validations, c'est ~30 secondes de juge. */
    const val LOT_PLEIN = 5

    /** Et si le lot ne se remplit pas, on part quand même au bout de ce délai. */
    const val DELAI_MS = 10_000L

    /** Le serveur refuse au-delà ; on reste en dessous. */
    const val LOT_MAX = 50

    /** Premier délai d'attente après un échec, puis doublé à chaque fois. */
    const val RETRAIT_INITIAL_MS = 2_000L

    /** Plafond du retrait. Au-delà, un juge attendrait trop après un rétablissement. */
    const val RETRAIT_MAX_MS = 60_000L

    /**
     * Combien de temps attendre après [echecsConsecutifs] échecs.
     *
     * Retrait exponentiel plafonné : 2 s, 4 s, 8 s… puis 60 s. Le plafond
     * compte autant que la croissance — sans lui, un backend éteint pendant une
     * heure ferait attendre le premier renvoi une demi-heure après son retour.
     */
    fun attenteApresEchec(echecsConsecutifs: Int): Long {
        if (echecsConsecutifs <= 0) return 0
        var attente = RETRAIT_INITIAL_MS
        repeat(echecsConsecutifs - 1) {
            attente = (attente * 2).coerceAtMost(RETRAIT_MAX_MS)
        }
        return attente.coerceAtMost(RETRAIT_MAX_MS)
    }

    /**
     * Faut-il tenter un envoi maintenant ?
     *
     * [forcer] correspond au geste du juge : la pastille « 3 en attente » ou le
     * « Tout envoyer » du menu. Il ignore le lot, le délai, et **rabat le
     * retrait à son premier palier**.
     *
     * ⚠️ Le retrait était auparavant opposé au juge comme au reste, avec pour
     * raison qu'appuyer en boucle sur un serveur éteint noierait le téléphone
     * de requêtes. Mais après cinq échecs le retrait vaut trente secondes :
     * pendant tout ce temps, appuyer sur « 3 en attente » ne tentait **rien du
     * tout** et affichait « il en reste 3 » — un bouton qui répond en donnant
     * l'impression d'avoir échoué alors qu'il n'a pas essayé.
     *
     * Le plancher de [RETRAIT_INITIAL_MS] suffit à la protection d'origine : il
     * borne un juge nerveux à une requête toutes les deux secondes, ce qu'un
     * aller-retour réseau coûte de toute façon. Et il ne s'applique qu'après un
     * échec — sur un serveur qui répond, le geste part toujours.
     */
    fun doitEnvoyer(
        enAttente: Int,
        msDepuisDernierEnvoi: Long,
        echecsConsecutifs: Int,
        forcer: Boolean = false,
    ): Boolean {
        if (enAttente <= 0) return false
        val retrait = attenteApresEchec(echecsConsecutifs).let {
            if (forcer) minOf(it, RETRAIT_INITIAL_MS) else it
        }
        if (msDepuisDernierEnvoi < retrait) return false
        if (forcer) return true
        return enAttente >= LOT_PLEIN || msDepuisDernierEnvoi >= DELAI_MS
    }

    /** Taille du prochain lot. */
    fun tailleLot(enAttente: Int): Int = enAttente.coerceAtMost(LOT_MAX)
}

/** Ce qui s'est passé lors d'une tentative d'envoi. */
data class BilanEnvoi(
    val envoyees: Int,
    val refusees: List<RefusServeur>,
    val restantes: Int,
    /**
     * Les references sur lesquelles le serveur a statue, refus compris.
     *
     * Sert au journal des scans : c'est ce qui lui permet de passer une ligne
     * de « en attente » a « partie ». La file, elle, s'en occupe deja seule.
     */
    val acquittees: Set<String> = emptySet(),
    /** Total des refusees mises de cote, en attente d'une decision humaine. */
    val misesDeCote: Int = 0,
    val echec: String? = null,
    val catalogueVersion: Int? = null,
) {
    val aReussi: Boolean get() = echec == null
}

/**
 * Vide la file vers le serveur, sans jamais rien perdre.
 *
 * Ne contient aucune coroutine ni aucun minuteur : [tenter] est appelée par
 * quelqu'un d'autre, ce qui la rend testable de bout en bout sur la JVM avec un
 * serveur factice.
 *
 * **L'invariant**, et il n'y en a qu'un qui compte : une réussite ne quitte la
 * file que si le serveur a explicitement statué sur elle. Réseau coupé, réponse
 * partielle, `401`, corps illisible — dans tous ces cas la file reste intacte et
 * l'envoi repartira. Réessayer est gratuit : le serveur est idempotent sur le
 * couple (grimpeur, bloc).
 */
class Expediteur(
    private val file: FileDeReussites,
    private val api: ClimbContestApi,
    /**
     * Qui envoie. Relu a chaque lot : le juge peut renommer son telephone en
     * pleine competition, et le nom enregistre doit etre celui du moment.
     */
    private val identite: () -> IdentiteAppareil? = { null },
) {
    var echecsConsecutifs: Int = 0
        private set

    /**
     * Remet les refusees dans la file, pour un nouvel essai.
     *
     * Le geste du juge apres qu'un organisateur a ajoute le participant
     * manquant -- le cas de loin le plus frequent : « ce dossard n'existe pas
     * ENCORE ».
     */
    fun renvoyerLesRefusees(surReprise: (String, String) -> Unit = { _, _ -> }): Int =
        file.renvoyerLesRefusees(
            nouvelleRef = { java.util.UUID.randomUUID().toString() },
            surReprise = surReprise,
        )

    /** Tente un envoi. Renvoie `null` s'il n'y avait rien à envoyer. */
    fun tenter(): BilanEnvoi? {
        val enAttente = file.nombreEnAttente()
        if (enAttente == 0) return null

        val lot = file.prochainLot(PolitiqueEnvoi.tailleLot(enAttente))
        val resultat = api.envoyerLot(lot, identite())

        if (!resultat.aReussi) {
            echecsConsecutifs++
            return BilanEnvoi(
                envoyees = 0, refusees = emptyList(), restantes = file.nombreEnAttente(),
                misesDeCote = file.nombreRefusees(),
                echec = resultat.echec, catalogueVersion = resultat.catalogueVersion,
            )
        }

        echecsConsecutifs = 0

        // On met de cote AVANT d'acquitter. Une coupure entre les deux laisse
        // la reussite dans la file principale : elle repartira et sera refusee
        // a nouveau, ce qui est sans gravite. L'ordre inverse la perdrait.
        if (resultat.refusees.isNotEmpty()) {
            val parRef = lot.associateBy { it.ref }
            resultat.refusees.forEach { refus ->
                parRef[refus.ref]?.let { file.mettreDeCote(it, refus.message) }
            }
        }
        file.acquitter(resultat.acquittees)
        return BilanEnvoi(
            envoyees = resultat.acquittees.size - resultat.refusees.size,
            refusees = resultat.refusees,
            restantes = file.nombreEnAttente(),
            acquittees = resultat.acquittees,
            misesDeCote = file.nombreRefusees(),
            catalogueVersion = resultat.catalogueVersion,
        )
    }
}
