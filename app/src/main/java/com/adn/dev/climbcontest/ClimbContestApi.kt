package com.adn.dev.climbcontest

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
    private val baseUrl: String = BuildConfig.SERVER_URL,
    private val client: OkHttpClient = defaultClient(),
) {

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
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
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
                    return ApiResult.Echec(
                        "Reponse illisible du serveur",
                        codeHttp = reponse.code,
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
