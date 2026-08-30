package com.adn.dev.climbcontest

import org.json.JSONObject
import java.io.File

/**
 * Le catalogue de la compétition, en mémoire, pour valider un scan sans réseau.
 *
 * C'est ce qui supprime **deux des trois allers-retours** que le juge subissait
 * à chaque validation. Une recherche est un accès de table de hachage — ~100 ns,
 * contre ~200 ms pour interroger le serveur. Le facteur est de deux millions, et
 * c'est ce que le juge ressent comme « instantané » au lieu de « ça rame ».
 *
 * Kotlin pur : aucune dépendance Android, donc testable sur la JVM.
 *
 * **Ce que le catalogue ne fait pas.** Il ne refuse rien définitivement. Un QR
 * qu'il ne connaît pas déclenche un repli réseau (voir [Server]) — parce qu'un
 * participant peut s'être inscrit dix minutes plus tôt, et qu'un juge ne doit
 * jamais s'entendre dire « inconnu » pour un grimpeur qui est bien là.
 */
class Catalogue(
    /** dossard → nom complet. C'est le nom que le juge lit pour confirmer. */
    private val parDossard: Map<String, String>,
    /** tag du bloc → libellé affiché. */
    private val parTag: Map<String, String>,
    /** Version du catalogue côté serveur. Sert à savoir s'il faut rafraîchir. */
    val version: Int,
    /**
     * tag du bloc → **couleur de son circuit**, telle que le serveur la nomme :
     * « Jaune », « Vert », « Bleu », « Mauve », « Rouge », « Noir ».
     *
     * Ajoutée pour la refonte visuelle : l'écran prend la couleur du bloc
     * scanné. Ce n'est pas de la décoration — un juge vérifie d'un coup d'œil
     * qu'il est sur le bon circuit, ce que le tag seul ne dit pas à quelqu'un
     * qui ne connaît pas la convention de nommage par cœur.
     *
     * Facultative : un bloc sans couleur reste parfaitement utilisable, l'écran
     * se contente alors de sa teinte neutre.
     */
    private val couleurParTag: Map<String, String> = emptyMap(),
) {

    val nombreParticipants: Int get() = parDossard.size
    val nombreBlocs: Int get() = parTag.size
    val estVide: Boolean get() = parDossard.isEmpty() && parTag.isEmpty()

    /** Le nom du grimpeur, ou `null` si ce dossard est inconnu **localement**. */
    fun grimpeur(dossard: String): String? = parDossard[dossard.trim()]

    /** Le libellé du bloc, ou `null` si ce tag est inconnu **localement**. */
    fun bloc(tag: String): String? = parTag[tag.trim().uppercase()]

    /** La couleur de circuit de ce bloc, telle que le serveur la nomme. */
    fun couleurDuBloc(tag: String): String? = couleurParTag[tag.trim().uppercase()]

    fun versJson(): String = JSONObject().apply {
        put("version", version)
        put("participants", JSONObject(parDossard as Map<*, *>))
        put("blocs", JSONObject(parTag as Map<*, *>))
        put("couleurs", JSONObject(couleurParTag as Map<*, *>))
    }.toString()

    companion object {
        val VIDE = Catalogue(emptyMap(), emptyMap(), version = 0)

        /**
         * Lit la réponse de `GET /api/v2/catalog`.
         *
         * Tolérante par construction : une entrée mal formée est ignorée, elle
         * ne fait pas perdre les 97 autres. Renvoie `null` si le corps est
         * inexploitable — l'appelant garde alors ce qu'il avait.
         */
        fun depuisReponseServeur(corps: String): Catalogue? = try {
            val o = JSONObject(corps)
            val dossards = mutableMapOf<String, String>()
            val tags = mutableMapOf<String, String>()
            val couleurs = mutableMapOf<String, String>()

            o.optJSONArray("participants")?.let { tableau ->
                for (i in 0 until tableau.length()) {
                    val p = tableau.optJSONObject(i) ?: continue
                    val dossard = p.opt("dossard")?.toString()?.takeIf { it != "null" }
                    val nom = p.optString("nom")
                    if (!dossard.isNullOrBlank() && nom.isNotBlank()) dossards[dossard] = nom
                }
            }
            o.optJSONArray("blocs")?.let { tableau ->
                for (i in 0 until tableau.length()) {
                    val b = tableau.optJSONObject(i) ?: continue
                    val tag = b.optString("tag")
                    if (tag.isBlank()) continue
                    tags[tag.uppercase()] = tag
                    // Facultative : un bloc sans couleur reste utilisable.
                    b.optString("couleur").takeIf { it.isNotBlank() }
                        ?.let { couleurs[tag.uppercase()] = it }
                }
            }
            Catalogue(dossards, tags, o.optInt("version", 0), couleurs)
        } catch (e: Exception) {
            null
        }

        /** Relit un catalogue rangé sur le disque. `null` si le fichier est abîmé. */
        fun depuisDisque(fichier: File): Catalogue? {
            if (!fichier.exists()) return null
            return try {
                val o = JSONObject(fichier.readText(Charsets.UTF_8))
                val p = o.getJSONObject("participants")
                val b = o.getJSONObject("blocs")
                // Les couleurs sont arrivees apres : un catalogue range par une
                // version anterieure n'en a pas, et doit rester lisible. Sans
                // ce `opt`, la relecture echouait et le catalogue entier etait
                // jete a la premiere mise a jour.
                val c = o.optJSONObject("couleurs")
                Catalogue(
                    p.keys().asSequence().associateWith { p.getString(it) },
                    b.keys().asSequence().associateWith { b.getString(it) },
                    o.optInt("version", 0),
                    c?.keys()?.asSequence()?.associateWith { c.getString(it) } ?: emptyMap(),
                )
            } catch (e: Exception) {
                // Un catalogue abîmé se retélécharge. Il ne doit jamais empêcher
                // l'application de démarrer.
                null
            }
        }
    }
}

/**
 * Garde le catalogue courant et décide quand le rafraîchir.
 *
 * Séparé de [Catalogue] pour la même raison que partout ailleurs : la donnée est
 * immuable et triviale à tester, la décision de rafraîchir a des règles qui,
 * elles, méritent des tests.
 */
class DepotCatalogue(private val fichier: File) {

    @Volatile
    private var courant: Catalogue = Catalogue.VIDE

    /** Charge ce qui est sur le disque. Appelé au démarrage. */
    @Synchronized
    fun charger(): Catalogue {
        courant = Catalogue.depuisDisque(fichier) ?: Catalogue.VIDE
        return courant
    }

    @Synchronized
    fun courant(): Catalogue = courant

    /** Range un catalogue fraîchement téléchargé. */
    @Synchronized
    fun enregistrer(catalogue: Catalogue) {
        courant = catalogue
        fichier.parentFile?.mkdirs()
        // Écriture voisine puis renommage : un catalogue à moitié écrit serait
        // relu comme corrompu au démarrage suivant, et retéléchargé pour rien.
        val provisoire = File(fichier.parentFile, "${fichier.name}.tmp")
        provisoire.writeText(catalogue.versJson(), Charsets.UTF_8)
        provisoire.renameTo(fichier)
    }

    /**
     * Faut-il retélécharger ?
     *
     * Quatre déclencheurs, chacun pour une raison distincte :
     *
     * | Quand | Pourquoi |
     * | --- | --- |
     * | catalogue vide | premier lancement, ou fichier abîmé |
     * | version du serveur ≠ la nôtre | connue gratuitement, elle voyage dans la réponse de chaque lot |
     * | un QR est inconnu localement | le signal le plus direct qu'on a du retard |
     * | plus de [PERIODE_MS] écoulées | filet, pour un téléphone qui n'envoie rien |
     */
    fun doitRafraichir(
        versionServeur: Int? = null,
        qrInconnu: Boolean = false,
        maintenantMs: Long,
        dernierRafraichissementMs: Long,
    ): Boolean = when {
        courant.estVide -> true
        qrInconnu -> true
        versionServeur != null && versionServeur != courant.version -> true
        maintenantMs - dernierRafraichissementMs >= PERIODE_MS -> true
        else -> false
    }

    companion object {
        /** Cinq minutes. Un filet, pas le mécanisme principal. */
        const val PERIODE_MS = 5 * 60 * 1000L
        const val FICHIER = "catalogue.json"
    }
}
