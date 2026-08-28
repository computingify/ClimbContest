package com.adn.dev.climbcontest

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Le dialogue HTTP avec le backend, et rien d'autre.
 *
 * Extrait de [Server] pour une raison précise : cette classe ne dépend ni d'un
 * `Context` Android, ni du `MainViewModel`, ni d'un `Toast`. Elle est donc
 * **testable sur la JVM**, avec un serveur factice, sans émulateur.
 *
 * C'est ce qui rend les tests de la couche réseau stables et rapides —
 * l'émulateur, lui, s'est révélé trop instable sur la machine de dev pour servir
 * de socle de test (voir docs/tester-avec-l-emulateur.md).
 *
 * [Server] garde ce qui touche à l'interface : les toasts, le ViewModel, les
 * coroutines.
 */
class ClimbContestApi(
    baseUrl: String = BuildConfig.SERVER_URL,
    private val client: OkHttpClient = defaultClient(),
) {

    // Normalise ici, pas chez l'appelant : une adresse collee depuis un
    // navigateur porte un slash final, et « .../ » + « /api/... » donnerait
    // « //api/v2/contest/success ». Certains proxys le suivent, d'autres non.
    private val baseUrl: String = baseUrl.trimEnd('/')

    companion object {
        private val JSON = "application/json".toMediaTypeOrNull()

        /**
         * Client par défaut.
         *
         * La vérification du nom d'hôte n'est **pas** désactivée : elle l'était
         * du temps du certificat auto-signé du Raspberry Pi, ce qui revenait à
         * accepter n'importe quel certificat valide pour n'importe quel domaine
         * (risque R10). Le serveur présente un vrai certificat depuis, et le
         * développement local passe en HTTP en clair.
         *
         * Les délais sont courts volontairement : un juge qui attend plus de
         * dix secondes devant son téléphone recommence, et le double appui
         * n'est pas un problème puisque l'envoi est idempotent côté serveur.
         *
         * Le délai de connexion est aligné sur les autres (10 s et non 5 s) :
         * vingt-cinq téléphones sur le même point d'accès font monter la poignée
         * de main TCP bien au-delà de cinq secondes, et abandonner là affiche
         * « aucun accès au serveur » alors que le serveur répond très bien. Ça
         * ne coûte rien quand tout va bien — une connexion saine s'établit en
         * quelques dizaines de millisecondes.
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Vérifie un QR code de grimpeur. Renvoie son nom, ou l'échec. */
    fun verifierGrimpeur(dossard: String): ApiResult =
        poster("climber/name", JSONObject().put("id", dossard))

    /** Vérifie un QR code de bloc. */
    fun verifierBloc(tag: String): ApiResult =
        poster("bloc/name", JSONObject().put("id", tag))

    /**
     * Enregistre une réussite.
     *
     * Idempotent côté serveur : renvoyer deux fois le même couple ne crée
     * qu'une seule réussite et répond succès les deux fois. Un double appui sur
     * « Envoyer » ne doit jamais ressembler à une erreur.
     */
    fun enregistrerReussite(dossard: String, tag: String): ApiResult =
        poster("success", JSONObject().put("bib", dossard).put("bloc", tag))

    /**
     * Télécharge le catalogue.
     *
     * [versionConnue] est envoyée en `If-None-Match` : si rien n'a bougé, le
     * serveur répond `304` avec un corps vide — ~150 octets au lieu de 15 ko.
     * C'est le cas le plus fréquent, et de loin.
     */
    fun telechargerCatalogue(versionConnue: Int? = null): ResultatCatalogue {
        val requete = Request.Builder()
            .url("$baseUrl/api/v2/catalog")
            .get()
            .apply { versionConnue?.let { header("If-None-Match", "\"$it\"") } }
            .build()

        return try {
            client.newCall(requete).execute().use { reponse ->
                when {
                    reponse.code == 304 -> ResultatCatalogue.DejaAJour
                    reponse.isSuccessful -> {
                        val texte = reponse.body?.string().orEmpty()
                        Catalogue.depuisReponseServeur(texte)
                            ?.let { ResultatCatalogue.Recu(it) }
                            ?: ResultatCatalogue.Echec("Catalogue illisible", reseau = true)
                    }
                    else -> ResultatCatalogue.Echec(
                        "Catalogue refuse (${reponse.code})",
                        reseau = reponse.code >= 500,
                    )
                }
            }
        } catch (e: Exception) {
            ResultatCatalogue.Echec("Catalogue injoignable : ${e.message}", reseau = true)
        }
    }

    /**
     * Envoie un lot de réussites.
     *
     * Le serveur répond **par élément**. Une `ref` absente de la réponse n'a pas
     * été traitée : l'appelant doit la garder en file. Le défaut est de garder —
     * perdre une réussite est le seul résultat inacceptable.
     */
    fun envoyerLot(reussites: List<ReussiteEnAttente>): ResultatLot {
        if (reussites.isEmpty()) return ResultatLot(emptySet(), emptyList(), null)

        val items = JSONArray()
        reussites.forEach { items.put(JSONObject(it.versJson())) }
        val corps = JSONObject().put("items", items)

        val requete = Request.Builder()
            .url("$baseUrl/api/v3/successes")
            .post(corps.toString().toRequestBody(JSON))
            .build()

        return try {
            client.newCall(requete).execute().use { reponse ->
                val texte = reponse.body?.string().orEmpty()
                if (!reponse.isSuccessful) {
                    // 401, 409, 413... Rien n'est acquitte : la file reste
                    // intacte. Un 5xx ou un 409 « pas de competition active »
                    // se reessaient ; un 401 aussi, faute de mieux, mais il est
                    // signale au juge.
                    return ResultatLot(
                        emptySet(), emptyList(), null,
                        echec = "Envoi refuse (${reponse.code})",
                        codeHttp = reponse.code,
                    )
                }
                val json = try {
                    JSONObject(texte)
                } catch (e: Exception) {
                    return ResultatLot(emptySet(), emptyList(), null,
                                       echec = "Reponse illisible", codeHttp = reponse.code)
                }
                val acquittees = mutableSetOf<String>()
                val refusees = mutableListOf<RefusServeur>()
                json.optJSONArray("resultats")?.let { tableau ->
                    for (i in 0 until tableau.length()) {
                        val r = tableau.optJSONObject(i) ?: continue
                        val ref = r.optString("ref")
                        if (ref.isBlank()) continue
                        when (r.optString("etat")) {
                            // Les trois etats sont DEFINITIFS : la reussite
                            // quitte la file dans les trois cas.
                            "enregistree", "deja_connue" -> acquittees += ref
                            "refusee" -> {
                                acquittees += ref
                                refusees += RefusServeur(ref, r.optString("message"))
                            }
                            // Tout autre etat : on ne sait pas, donc on garde.
                        }
                    }
                }
                ResultatLot(
                    acquittees, refusees,
                    json.optInt("catalogue_version", -1).takeIf { it >= 0 },
                )
            }
        } catch (e: Exception) {
            ResultatLot(emptySet(), emptyList(), null,
                        echec = "Serveur injoignable : ${e.message}")
        }
    }

    private fun poster(chemin: String, corps: JSONObject): ApiResult {
        val requete = Request.Builder()
            .url("$baseUrl/api/v2/contest/$chemin")
            .post(corps.toString().toRequestBody(JSON))
            .build()

        return try {
            client.newCall(requete).execute().use { reponse ->
                val texte = reponse.body?.string().orEmpty()
                val json = try {
                    JSONObject(texte)
                } catch (e: Exception) {
                    // Un corps illisible, c'est presque toujours une page HTML
                    // d'erreur : 502 de Render au reveil a froid, 503 d'un
                    // proxy. Ce n'est PAS un refus metier, et le juge doit
                    // reessayer -- l'envoi est idempotent, ca ne coute rien.
                    return ApiResult.Echec(
                        "Reponse illisible du serveur",
                        codeHttp = reponse.code,
                        reseau = true,
                    )
                }
                // Le serveur repond 201 en cas de succes, 400 sinon, et porte
                // toujours un champ "success" : c'est lui qui fait foi, pas le
                // seul code HTTP.
                if (json.optBoolean("success", false)) {
                    ApiResult.Succes(
                        libelle = json.optString("id", ""),
                        message = json.optString("message", ""),
                    )
                } else {
                    ApiResult.Echec(
                        json.optString("message", "Refuse par le serveur"),
                        codeHttp = reponse.code,
                        // Un 5xx est une panne du serveur, pas un refus : « envoi
                        // echoue » se lit comme definitif et le juge passe au
                        // grimpeur suivant, alors qu'il suffisait de reessayer.
                        reseau = reponse.code >= 500,
                    )
                }
            }
        } catch (e: Exception) {
            // Reseau coupe, delai depasse, TLS refuse : tout arrive dans une
            // salle en sous-sol. On ne distingue pas -- le juge a besoin de
            // savoir que ca n'est pas parti, pas de la cause.
            ApiResult.Echec("Aucun acces au serveur : ${e.message}", reseau = true)
        }
    }
}

/** Ce que le serveur a dit d'un envoi de lot. */
data class ResultatLot(
    /** Les `ref` sur lesquelles le serveur a **statué**. Elles quittent la file. */
    val acquittees: Set<String>,
    /** Celles qu'il a refusées — à signaler au juge, pas à réessayer. */
    val refusees: List<RefusServeur>,
    /** Version du catalogue côté serveur, si elle est venue. */
    val catalogueVersion: Int?,
    /** Renseigné si l'envoi n'a pas abouti. La file reste alors intacte. */
    val echec: String? = null,
    val codeHttp: Int? = null,
) {
    val aReussi: Boolean get() = echec == null
}

/** Un élément que le serveur a refusé, avec sa raison. */
data class RefusServeur(val ref: String, val message: String)

/** Résultat d'un téléchargement de catalogue. */
sealed class ResultatCatalogue {
    data class Recu(val catalogue: Catalogue) : ResultatCatalogue()
    /** `304` : rien n'a bougé, ~150 octets échangés. */
    object DejaAJour : ResultatCatalogue()
    data class Echec(val message: String, val reseau: Boolean = false) : ResultatCatalogue()
}

/** Résultat d'un appel. Volontairement plus riche que le booléen d'avant. */
sealed class ApiResult {
    /** [libelle] est le nom du grimpeur, ou le tag du bloc. */
    data class Succes(val libelle: String, val message: String = "") : ApiResult()

    /** [reseau] distingue « le serveur a refusé » de « on n'a pas pu lui parler ». */
    data class Echec(
        val message: String,
        val codeHttp: Int? = null,
        val reseau: Boolean = false,
    ) : ApiResult()

    val estSucces: Boolean get() = this is Succes
}
