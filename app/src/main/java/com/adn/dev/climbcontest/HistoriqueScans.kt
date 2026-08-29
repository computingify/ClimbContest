package com.adn.dev.climbcontest

import org.json.JSONObject
import java.io.File
import java.time.Duration
import java.time.Instant

/** Où en est un scan, du point de vue du téléphone. */
enum class EtatScan { EN_ATTENTE, PARTIE, REFUSEE;

    fun code(): String = name.lowercase()

    companion object {
        fun depuisCode(code: String): EtatScan? =
            entries.firstOrNull { it.code() == code }
    }
}

/** Un scan tel que le journal s'en souvient. */
data class ScanJournalise(
    val ref: String,
    val dossard: String,
    val bloc: String,
    /** Heure du scan, ISO 8601 UTC. */
    val scanneLe: String,
    val etat: EtatScan,
    val motif: String? = null,
) {
    /** Les six premiers caractères de la référence : de quoi la lire à voix haute. */
    fun refCourte(): String = ref.take(6)
}

/**
 * Le journal de **tous** les scans, celui qu'on relit après coup.
 *
 * [FileDeReussites] répond à « qu'est-ce qui n'est pas encore parti ? » et se
 * vide dès que tout est acquitté — le compactage, qui garde la file courte donc
 * les envois rapides. Ce journal-ci répond à une autre question, « qu'est-ce que
 * j'ai scanné, et est-ce arrivé ? », et ne se vide donc jamais tout seul.
 *
 * **Ce journal n'est PAS la source de vérité de l'envoi.** `file.jsonl` le
 * reste. C'est ce qui rend la purge à trente jours sans danger : elle ne touche
 * pas au fichier qui porte les réussites non envoyées, et ne peut donc pas en
 * perdre une. Un test le verrouille.
 *
 * **Le format : un fichier en ajout seul, une ligne par évènement.**
 *
 * ```
 * {"ref":"a1b2","bib":"42","bloc":"ZV3","at":"2026-11-08T10:42:03Z","etat":"en_attente"}
 * {"ref":"a1b2","etat":"partie"}
 * ```
 *
 * Règle de relecture : **pour une `ref`, la dernière ligne fait foi.** Elle est
 * triviale à tester et tolère une coupure n'importe où — au pire un scan reste
 * « en attente » alors qu'il est parti, ce qui est le sens de l'erreur qu'on
 * préfère : on ne prétend jamais qu'une réussite est arrivée sans le savoir.
 *
 * **Aucun nom de grimpeur n'est écrit ici.** Le journal ne garde que le dossard
 * et le tag du bloc — ce que le juge a scanné. Le nom s'affiche en le
 * retrouvant dans le catalogue courant, et disparaît quand le catalogue change.
 * Trente jours de noms de mineurs sur vingt-cinq téléphones de bénévoles ne
 * serviraient à rien qu'on ne sache déjà faire autrement.
 */
class HistoriqueScans(dossier: File) {

    private val stockage = StockageFichier(File(dossier, FICHIER))

    /** Note un scan qui vient d'être validé par le juge. */
    @Synchronized
    fun noter(reussite: ReussiteEnAttente) {
        stockage.ajouter(
            JSONObject()
                .put("ref", reussite.ref)
                .put("bib", reussite.dossard)
                .put("bloc", reussite.bloc)
                .put("at", reussite.scanneLe)
                .put("etat", EtatScan.EN_ATTENTE.code())
                .toString()
        )
    }

    /**
     * Une réussite refusée repart sous une **nouvelle** référence.
     *
     * C'est [FileDeReussites.renvoyerLesRefusees] qui la renumérote, et pour une
     * bonne raison : l'ancienne référence figure dans le fichier des
     * acquittements, donc la remettre en file telle quelle la rendrait
     * invisible — acquittée d'avance.
     *
     * Le journal, lui, n'a pas à montrer deux lignes : c'est **un seul scan**,
     * qu'on a tenté deux fois. L'entrée existante change simplement de
     * référence, garde sa place dans la liste et son motif de refus.
     */
    @Synchronized
    fun reprendre(ancienneRef: String, nouvelleRef: String) {
        stockage.ajouter(
            JSONObject()
                .put("ref", nouvelleRef)
                .put("remplace", ancienneRef)
                .put("etat", EtatScan.EN_ATTENTE.code())
                .toString()
        )
    }

    /** Note le sort d'un scan : le serveur a tranché. */
    @Synchronized
    fun changerEtat(ref: String, etat: EtatScan, motif: String? = null) {
        stockage.ajouter(
            JSONObject()
                .put("ref", ref)
                .put("etat", etat.code())
                .apply { motif?.let { put("motif", it) } }
                .toString()
        )
    }

    /**
     * Tous les scans, du plus ancien au plus récent.
     *
     * Une ligne illisible est ignorée, et seulement elle : un fichier abîmé au
     * milieu ne doit pas emporter ce qu'il y a autour.
     */
    @Synchronized
    fun tous(): List<ScanJournalise> {
        // Une liste pour l'ordre, un index pour retrouver une entree par sa
        // reference COURANTE. Une simple `LinkedHashMap` ne suffirait pas : une
        // reprise change la cle d'une entree, et la reinserer la renverrait a la
        // fin de la liste alors qu'elle doit garder sa place.
        val entrees = ArrayList<ScanJournalise>()
        val position = HashMap<String, Int>()

        for (ligne in stockage.lire()) {
            val o = try { JSONObject(ligne) } catch (e: Exception) { continue }
            val ref = o.optString("ref").ifBlank { continue }
            val etat = EtatScan.depuisCode(o.optString("etat")) ?: EtatScan.EN_ATTENTE
            val motif = o.optString("motif").ifBlank { null }
            val remplace = o.optString("remplace")
            val bib = o.optString("bib")

            when {
                // Ligne de creation : elle porte tout.
                bib.isNotBlank() -> {
                    position[ref] = entrees.size
                    entrees += ScanJournalise(
                        ref = ref, dossard = bib, bloc = o.optString("bloc"),
                        scanneLe = o.optString("at"), etat = etat, motif = motif,
                    )
                }
                // Reprise : le meme scan, sous une nouvelle reference.
                remplace.isNotBlank() -> position.remove(remplace)?.let { i ->
                    position[ref] = i
                    entrees[i] = entrees[i].copy(ref = ref, etat = etat)
                }
                // Changement d'etat. Sans creation prealable — fichier tronque
                // par le debut, purge partielle — il n'y a rien a mettre a jour,
                // et inventer un scan vide serait pire.
                else -> position[ref]?.let { i ->
                    entrees[i] = entrees[i].copy(etat = etat, motif = motif ?: entrees[i].motif)
                }
            }
        }
        return entrees
    }

    /** Ce qui n'a pas atteint le serveur : en attente ou refusé. */
    fun nonArrives(): List<ScanJournalise> =
        tous().filter { it.etat != EtatScan.PARTIE }

    /**
     * Efface ce qui a plus de [RETENTION_JOURS] jours.
     *
     * Réécrit le journal **consolidé** : une seule ligne par scan, portant son
     * état final. Le fichier repart donc de sa taille utile, et non de la somme
     * de tous les évènements depuis l'installation.
     *
     * Un scan dont l'heure est illisible est **gardé**. On n'efface pas ce
     * qu'on ne sait pas dater.
     *
     * Rappel : `file.jsonl` n'est pas touché. Une réussite non envoyée survit à
     * la purge, quel que soit son âge.
     */
    @Synchronized
    fun purger(maintenant: Instant = Instant.now()): Int {
        val tous = tous()
        val gardes = tous.filter { estRecent(it, maintenant) }
        // Rien a jeter : on ne reecrit pas pour rien.
        if (gardes.size == tous.size) return 0
        stockage.remplacer(gardes.map { ligneConsolidee(it) })
        return tous.size - gardes.size
    }

    private fun estRecent(scan: ScanJournalise, maintenant: Instant): Boolean {
        val quand = try {
            Instant.parse(scan.scanneLe)
        } catch (e: Exception) {
            return true
        }
        return Duration.between(quand, maintenant).toDays() < RETENTION_JOURS
    }

    private fun ligneConsolidee(scan: ScanJournalise): String = JSONObject()
        .put("ref", scan.ref)
        .put("bib", scan.dossard)
        .put("bloc", scan.bloc)
        .put("at", scan.scanneLe)
        .put("etat", scan.etat.code())
        .apply { scan.motif?.let { put("motif", it) } }
        .toString()

    companion object {
        const val FICHIER = "historique.jsonl"

        /** Décision d'Adrien du 29/08. */
        const val RETENTION_JOURS = 30L
    }
}
