package com.adn.dev.climbcontest

import org.json.JSONObject
import java.io.File

/** Une réussite en attente d'envoi. */
data class ReussiteEnAttente(
    /** Identifiant **client**, qui ne sert qu'à savoir quoi retirer de la file. */
    val ref: String,
    val dossard: String,
    val bloc: String,
    /** Heure du scan, ISO 8601. Indicative : le serveur pose la sienne. */
    val scanneLe: String,
) {
    fun versJson(): String = JSONObject()
        .put("ref", ref)
        .put("bib", dossard)
        .put("bloc", bloc)
        .put("at", scanneLe)
        .toString()

    companion object {
        /** Relit une ligne. Renvoie `null` si elle est illisible — jamais d'exception. */
        fun depuisJson(ligne: String): ReussiteEnAttente? = try {
            val o = JSONObject(ligne)
            val ref = o.optString("ref")
            val bib = o.optString("bib")
            val bloc = o.optString("bloc")
            if (ref.isBlank() || bib.isBlank() || bloc.isBlank()) null
            else ReussiteEnAttente(ref, bib, bloc, o.optString("at"))
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * La file des réussites qui n'ont pas encore atteint le serveur.
 *
 * C'est la pièce qui change tout pour le juge : « Validé » s'affiche quand la
 * réussite est sur le **disque du téléphone**, plus quand elle est sur celui de
 * la VM. Le disque du téléphone est bien plus fiable qu'un wifi de salle avec
 * 125 personnes dessus.
 *
 * **Deux fichiers, tous deux en ajout seul.**
 *
 * | Fichier | Contenu |
 * | --- | --- |
 * | `file.jsonl` | une réussite par ligne, dans l'ordre où le juge a validé |
 * | `acquittees.txt` | les `ref` dont le serveur a confirmé le sort |
 *
 * Pourquoi deux fichiers plutôt qu'un seul qu'on réécrirait à chaque envoi :
 * parce qu'une réécriture peut être interrompue, et qu'une interruption au
 * mauvais moment perdrait des réussites déjà validées. Un ajout ne peut pas
 * laisser un état intermédiaire. La seule réécriture du système — le
 * compactage — n'a lieu que lorsque **tout** est acquitté, donc quand il n'y a
 * plus rien à perdre, et elle passe par un renommage atomique.
 *
 * **Volume.** Une compétition, c'est ~3 600 réussites × ~90 octets ≈ 320 ko.
 * Une base de données serait un marteau-pilon ; et surtout, elle se testerait
 * mal — un journal se vérifie avec un dossier temporaire sur la JVM, là où Room
 * ramènerait l'émulateur, précisément ce qu'on a écarté pour instabilité.
 */
class FileDeReussites(dossier: File) {

    private val journal = StockageFichier(File(dossier, FICHIER_FILE))
    private val acquits = StockageFichier(File(dossier, FICHIER_ACQUITS))

    companion object {
        const val FICHIER_FILE = "file.jsonl"
        const val FICHIER_ACQUITS = "acquittees.txt"
    }

    /**
     * Ajoute une réussite. **Elle est sur le disque quand cette méthode rend la main.**
     *
     * L'ordre compte : l'appelant ne doit afficher « Validé » qu'après le retour,
     * jamais avant. Afficher d'abord rendrait le message mensonger.
     */
    @Synchronized
    fun ajouter(reussite: ReussiteEnAttente) {
        journal.ajouter(reussite.versJson())
    }

    /** Ce qui reste à envoyer, dans l'ordre de validation. */
    @Synchronized
    fun enAttente(): List<ReussiteEnAttente> {
        val acquittees = acquits.lire().toSet()
        return journal.lire()
            .mapNotNull { ReussiteEnAttente.depuisJson(it) }
            .filter { it.ref !in acquittees }
    }

    /** Combien de réussites ne sont pas encore parties. C'est ce que le juge voit. */
    @Synchronized
    fun nombreEnAttente(): Int = enAttente().size

    /** Le prochain lot à envoyer, au plus [taille] éléments. */
    @Synchronized
    fun prochainLot(taille: Int): List<ReussiteEnAttente> = enAttente().take(taille)

    /**
     * Note que le serveur a statué sur ces `ref`.
     *
     * « Statué » veut dire *enregistrée*, *déjà connue* ou *refusée* — les trois
     * sont définitifs. Une `ref` sur laquelle le serveur n'a **rien** dit n'est
     * pas passée ici : elle reste en file et repartira. Le défaut est de garder.
     */
    @Synchronized
    fun acquitter(refs: Collection<String>) {
        if (refs.isEmpty()) return
        refs.forEach { acquits.ajouter(it) }
        compacterSiTermine()
    }

    /**
     * Vide les deux fichiers **quand il ne reste rien**.
     *
     * Sans compactage, les fichiers grossiraient toute la journée et la
     * relecture ralentirait à chaque envoi. Avec, ils reviennent à zéro dès que
     * la file est à jour — c'est-à-dire la plupart du temps.
     *
     * La condition est stricte : **tout** doit être acquitté. Compacter avec des
     * réussites en attente demanderait de réécrire le journal en le filtrant, et
     * une coupure au milieu de cette réécriture perdrait des réussites validées.
     * On préfère un fichier qui grossit à une fenêtre de perte.
     */
    private fun compacterSiTermine() {
        if (enAttente().isNotEmpty()) return
        journal.remplacer(emptyList())
        acquits.remplacer(emptyList())
    }

    /** Taille occupée sur le disque, pour le diagnostic. */
    @Synchronized
    fun octets(): Long = journal.taille() + acquits.taille()
}
