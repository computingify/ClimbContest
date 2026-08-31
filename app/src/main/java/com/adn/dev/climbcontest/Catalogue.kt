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

    // --- Le rythme des essais ------------------------------------------------
    //
    // ⚠️ C'est ici que vivait le pire défaut de cette classe, et il ne demandait
    // AUCUNE panne pour se déclencher.
    //
    // `doitRafraichir` répondait « oui » sans condition de temps dès que le
    // catalogue était vide. Or un catalogue peut être légitimement vide : la
    // compétition est créée le matin, les participants ne sont pas encore
    // saisis. Le téléchargement RÉUSSIT, rend un catalogue vide, et la
    // condition reste vraie. La boucle de fond tourne toutes les secondes :
    // vingt-cinq téléphones tapaient donc une fois par seconde chacun sur un
    // serveur en parfaite santé, pendant tout le briefing.
    //
    // Deux garde-fous, et il faut les deux :
    //
    // - un **plancher** qui vaut quelle que soit la raison, y compris les plus
    //   pressantes — c'est lui qui couvre le cas ci-dessus, où rien n'échoue ;
    // - un **retrait après échec**, qui couvre la panne : le serveur est à
    //   genoux, insister l'enfonce.

    private var echecs = 0

    /**
     * Combien de QR inconnus signalés, et combien étaient connus au début du
     * dernier téléchargement réussi.
     *
     * Deux compteurs et non un booléen, pour une raison précise : un QR peut
     * être scanné PENDANT un téléchargement. Un booléen remis à `false` à la
     * fin effacerait ce signal-là sans l'avoir servi, et le participant
     * inscrit dix minutes plus tôt attendrait le filet des cinq minutes.
     */
    private var qrInconnus = 0L
    private var qrInconnusServis = 0L

    /** Un QR absent du catalogue : le signal le plus direct qu'on a du retard. */
    @Synchronized
    fun signalerQrInconnu() {
        qrInconnus++
    }

    /** Ce qu'un téléchargement qui démarre maintenant va couvrir. */
    @Synchronized
    fun qrInconnusVus(): Long = qrInconnus

    @Synchronized
    fun noterEchec() {
        echecs++
    }

    /**
     * [vus] est ce que [qrInconnusVus] rendait au DÉBUT de ce téléchargement.
     *
     * `maxOf` et non une affectation : deux téléchargements peuvent se
     * chevaucher — la boucle de fond et le sondage de présence tournent dans
     * des coroutines distinctes. Si le plus ANCIEN finit en dernier, une
     * affectation ferait reculer le compteur. L'effet serait bénin (un
     * rafraîchissement de trop, jamais un de moins), mais un compteur qui
     * recule est le genre de bizarrerie qu'on met une heure à comprendre six
     * mois plus tard.
     */
    @Synchronized
    fun noterSucces(vus: Long) {
        echecs = 0
        qrInconnusServis = maxOf(qrInconnusServis, vus)
    }

    /** Pour les tests et le journal : combien d'échecs consécutifs. */
    @get:Synchronized
    val echecsConsecutifs: Int get() = echecs

    /**
     * Faut-il retélécharger, et a-t-on le droit de le faire maintenant ?
     *
     * Quatre déclencheurs, chacun pour une raison distincte :
     *
     * | Quand | Pourquoi |
     * | --- | --- |
     * | catalogue vide | premier lancement, ou fichier abîmé |
     * | version du serveur ≠ la nôtre | connue gratuitement, elle voyage dans la réponse de chaque lot |
     * | un QR est inconnu localement | le signal le plus direct qu'on a du retard |
     * | plus de [PERIODE_MS] écoulées | filet, pour un téléphone qui n'envoie rien |
     *
     * …mais aucun ne passe avant [PLANCHER_MS], ni avant le retrait dû aux
     * échecs. Un déclencheur dit **s'il y a lieu** de retélécharger ; il ne dit
     * pas à quel **rythme** insister, et c'est le rythme, seul, qui protège le
     * wifi de la salle.
     *
     * ⚠️ [finDuDernierEssaiMs] est la FIN de la dernière tentative, pas son
     * début. Un appel qui expire coûte dix secondes (`ClimbContestApi`) : mesuré
     * depuis le début, un retrait de deux ou quatre secondes serait déjà écoulé
     * quand on le teste, et ne freinerait rien.
     */
    @Synchronized
    fun doitRafraichir(
        versionServeur: Int? = null,
        maintenantMs: Long,
        finDuDernierEssaiMs: Long,
    ): Boolean {
        val depuis = maintenantMs - finDuDernierEssaiMs
        if (depuis < maxOf(PLANCHER_MS, PolitiqueEnvoi.attenteApresEchec(echecs))) return false
        return courant.estVide ||
            qrInconnus > qrInconnusServis ||
            (versionServeur != null && versionServeur != courant.version) ||
            depuis >= PERIODE_MS
    }

    companion object {
        /** Cinq minutes. Un filet, pas le mécanisme principal. */
        const val PERIODE_MS = 5 * 60 * 1000L

        /**
         * Le temps minimal entre deux tentatives, quelle que soit l'urgence.
         *
         * Cinq secondes : le juge ne les sent pas — quand un QR lui est inconnu,
         * le repli réseau lui a déjà rendu le nom — et vingt-cinq téléphones
         * qui insistent tombent de vingt-cinq requêtes par seconde à cinq.
         */
        const val PLANCHER_MS = 5_000L
        const val FICHIER = "catalogue.json"
    }
}
