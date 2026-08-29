package com.adn.dev.climbcontest

import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Qui est ce téléphone.
 *
 * @param id  Créé au premier lancement, jamais réécrit ensuite.
 * @param nom Ce que le juge a tapé — « Mur jaune », « Zone bleue ». Facultatif.
 */
data class IdentiteAppareil(val id: String, val nom: String?) {

    fun versJson(): String = JSONObject()
        .put("id", id)
        .apply { nom?.let { put("nom", it) } }
        .toString()

    companion object {
        /**
         * Le nom est **coupé** à cette longueur, jamais refusé.
         *
         * Elle vaut celle de la colonne `appareil_nom` côté serveur. Un nom trop
         * long tronqué reste utilisable ; un envoi rejeté pour un nom trop long
         * serait absurde.
         */
        const val LONGUEUR_NOM = 60

        fun depuisJson(texte: String): IdentiteAppareil? = try {
            val o = JSONObject(texte)
            val id = o.optString("id")
            if (id.isBlank()) null
            else IdentiteAppareil(id, o.optString("nom").ifBlank { null })
        } catch (e: Exception) {
            null
        }

        fun nettoyerLeNom(brut: String?): String? =
            brut?.trim()?.take(LONGUEUR_NOM)?.ifBlank { null }
    }
}

/**
 * L'identité du téléphone, sur son disque.
 *
 * **Un UUID d'application, pas un identifiant matériel.** `ANDROID_ID`, l'IMEI
 * ou l'adresse MAC identifient l'appareil au sens du Play Store, relèvent de sa
 * politique sur les identifiants persistants, et survivent à la
 * désinstallation — trois propriétés dont on n'a aucun besoin. Un UUID généré
 * ici est cantonné à l'application et disparaît avec elle.
 *
 * Qu'il disparaisse est d'ailleurs le comportement voulu : un téléphone dont on
 * a effacé les données repart avec un identifiant neuf, et la console montre
 * l'ancien qui s'est tu. C'est une information, pas une anomalie.
 *
 * ⚠️ Ce qu'on identifie est un **poste**, pas une personne. Les téléphones
 * changent de main dans la journée ; « Mur jaune » désigne un endroit de la
 * salle. `saisie_par`, côté serveur, continue seul d'identifier quelqu'un — et
 * seulement pour une saisie manuelle.
 */
class DepotIdentite(fichier: File) {

    private val stockage = StockageFichier(fichier)

    @Volatile private var cache: IdentiteAppareil? = null

    /** L'identité de ce téléphone. La crée au premier appel. */
    @Synchronized
    fun courante(): IdentiteAppareil {
        cache?.let { return it }

        // Un fichier absent, vide ou illisible donne la même chose : une
        // identité neuve. Refuser de démarrer parce qu'un JSON est tronqué
        // serait un bien mauvais échange.
        val lue = stockage.lire().firstOrNull()?.let { IdentiteAppareil.depuisJson(it) }
        val identite = lue ?: IdentiteAppareil(UUID.randomUUID().toString(), null)
        if (lue == null) ecrire(identite)
        cache = identite
        return identite
    }

    /** Renomme le téléphone. L'identifiant, lui, ne bouge pas. */
    @Synchronized
    fun renommer(nom: String?) {
        val identite = courante().copy(nom = IdentiteAppareil.nettoyerLeNom(nom))
        ecrire(identite)
        cache = identite
    }

    private fun ecrire(identite: IdentiteAppareil) {
        stockage.remplacer(listOf(identite.versJson()))
    }

    companion object {
        const val FICHIER = "appareil.json"
    }
}
