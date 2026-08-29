package com.adn.dev.climbcontest

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Tests de la couche réseau de l'application, sur la JVM.
 *
 * **Pas d'émulateur.** Il s'est révélé trop instable sur la machine de dev pour
 * servir de socle : il plante au démarrage (crash QEMU, `GPU: UNKNOWN`). Or ce
 * qu'on a besoin de garantir ici — le format exact envoyé au serveur, la lecture
 * de sa réponse, le comportement quand le réseau tombe — n'a jamais eu besoin
 * d'un appareil Android. Un serveur factice dans le même processus suffit, et
 * s'exécute en une seconde.
 *
 * Ce que ces tests protègent : **le contrat avec le backend**. Si quelqu'un
 * change le nom d'un champ, le chemin d'une route ou la lecture d'une réponse,
 * un test tombe ici avant qu'un juge ne s'en aperçoive un dimanche matin.
 */
class ClimbContestApiTest {

    private lateinit var serveur: MockWebServer
    private lateinit var api: ClimbContestApi

    @Before
    fun demarrer() {
        serveur = MockWebServer()
        serveur.start()
        api = ClimbContestApi(
            baseUrl = serveur.url("/").toString().trimEnd('/'),
            client = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build(),
        )
    }

    @After
    fun arreter() {
        serveur.close()
    }

    // --- Le voyant « serveur joignable » de la barre du haut ---------------

    @Test
    fun `un serveur en bonne sante est joignable`() {
        repondre(200, JSONObject().put("status", "ok").toString())
        assertTrue(api.estJoignable())
        assertEquals("/health", serveur.takeRequest().target)
    }

    @Test
    fun `un serveur degrade n'est pas annonce joignable`() {
        // /health repond 503 quand la base est inutilisable. Le serveur parle,
        // mais il refusera tout : pour le juge, c'est la meme chose qu'absent.
        repondre(503, JSONObject().put("status", "degraded").toString())
        assertFalse(api.estJoignable())
    }

    @Test
    fun `un serveur eteint n'est pas joignable`() {
        serveur.close()
        assertFalse(api.estJoignable())
    }

    // --- L'identite du telephone dans le lot (spec 011) ---------------------

    private fun unLot() = listOf(
        ReussiteEnAttente(ref = "a", dossard = "1", bloc = "ZJ6",
                          scanneLe = "2026-11-08T10:00:00Z")
    )

    @Test
    fun `un lot porte l'identite du telephone`() {
        repondre(200, JSONObject().put("success", true)
            .put("resultats", org.json.JSONArray()).toString())

        api.envoyerLot(unLot(), IdentiteAppareil("8f3c1d20", "Mur jaune"))

        val corps = JSONObject(serveur.takeRequest().body!!.utf8())
        val appareil = corps.getJSONObject("appareil")
        assertEquals("8f3c1d20", appareil.getString("id"))
        assertEquals("Mur jaune", appareil.getString("nom"))
    }

    @Test
    fun `un telephone sans nom envoie quand meme son identifiant`() {
        repondre(200, JSONObject().put("success", true)
            .put("resultats", org.json.JSONArray()).toString())

        api.envoyerLot(unLot(), IdentiteAppareil("8f3c1d20", null))

        val appareil = JSONObject(serveur.takeRequest().body!!.utf8())
            .getJSONObject("appareil")
        assertEquals("8f3c1d20", appareil.getString("id"))
        assertFalse(appareil.has("nom"))
    }

    @Test
    fun `sans identite, le corps n'a pas de champ appareil`() {
        // C'est ce qui permet de livrer les deux cotes separement : le serveur
        // accepte un lot sans identite, exactement comme avant la spec 011.
        repondre(200, JSONObject().put("success", true)
            .put("resultats", org.json.JSONArray()).toString())

        api.envoyerLot(unLot())

        assertFalse(JSONObject(serveur.takeRequest().body!!.utf8()).has("appareil"))
    }

    private fun repondre(code: Int, corps: String) {
        serveur.enqueue(
            MockResponse.Builder()
                .code(code)
                .body(corps)
                .setHeader("Content-Type", "application/json")
                .build()
        )
    }

    // --- Ce que l'application ENVOIE -----------------------------------------

    @Test
    fun `verification grimpeur envoie le dossard sous la cle id`() {
        repondre(201, """{"success":true,"id":"Dupont Lea","message":"ok"}""")

        api.verifierGrimpeur("12")

        val requete = serveur.takeRequest()
        assertEquals("POST", requete.method)
        assertEquals("/api/v2/contest/climber/name", requete.url.encodedPath)
        val corps = JSONObject(requete.body!!.utf8())
        assertEquals("12", corps.getString("id"))
    }

    @Test
    fun `verification bloc envoie le tag sous la cle id`() {
        repondre(201, """{"success":true,"id":"ZJ6"}""")

        api.verifierBloc("ZJ6")

        val requete = serveur.takeRequest()
        assertEquals("/api/v2/contest/bloc/name", requete.url.encodedPath)
        assertEquals("ZJ6", JSONObject(requete.body!!.utf8()).getString("id"))
    }

    @Test
    fun `enregistrement envoie bib et bloc`() {
        repondre(201, """{"success":true,"message":"Well done"}""")

        api.enregistrerReussite("12", "ZJ6")

        val requete = serveur.takeRequest()
        assertEquals("/api/v2/contest/success", requete.url.encodedPath)
        val corps = JSONObject(requete.body!!.utf8())
        assertEquals("12", corps.getString("bib"))
        assertEquals("ZJ6", corps.getString("bloc"))
    }

    @Test
    fun `le corps est bien du json`() {
        repondre(201, """{"success":true}""")
        api.verifierGrimpeur("1")
        val type = serveur.takeRequest().headers["Content-Type"]
        assertTrue("Content-Type attendu json, obtenu $type",
            type?.startsWith("application/json") == true)
    }

    // --- Ce que l'application LIT --------------------------------------------

    @Test
    fun `le nom du grimpeur est lu dans le champ id`() {
        // Piege : le champ s'appelle "id" mais contient le NOM, pas le dossard.
        // C'est le contrat historique du backend, l'application l'affiche tel quel.
        repondre(201, """{"success":true,"id":"Dupont Lea","message":"ok"}""")

        val r = api.verifierGrimpeur("12")

        assertTrue(r is ApiResult.Succes)
        assertEquals("Dupont Lea", (r as ApiResult.Succes).libelle)
    }

    @Test
    fun `un refus du serveur remonte son message`() {
        repondre(400, """{"success":false,"message":"Dossard 999 inconnu"}""")

        val r = api.verifierGrimpeur("999")

        assertTrue(r is ApiResult.Echec)
        val echec = r as ApiResult.Echec
        assertEquals("Dossard 999 inconnu", echec.message)
        assertEquals(400, echec.codeHttp)
        assertTrue("un refus n'est pas une panne reseau", !echec.reseau)
    }

    @Test
    fun `c'est le champ success qui fait foi, pas le code http`() {
        // Un serveur mal configure pourrait repondre 200 avec success=false.
        repondre(200, """{"success":false,"message":"Refuse"}""")
        assertTrue(api.verifierGrimpeur("1") is ApiResult.Echec)
    }

    @Test
    fun `un envoi reussi est reconnu`() {
        repondre(201, """{"success":true,"message":"Well done"}""")
        assertTrue(api.enregistrerReussite("12", "ZJ6").estSucces)
    }

    @Test
    fun `le second envoi du meme couple est aussi un succes`() {
        // Portee de ce test : le COTE APPLICATION. Il ne prouve pas que le
        // backend est idempotent -- c'est test_e2e.py qui le verifie, avec un
        // vrai serveur. Ici on verifie que si le serveur repond succes deux
        // fois, l'application l'affiche deux fois comme un succes, sans etat
        // interne qui ferait rater le second envoi.
        repondre(201, """{"success":true,"message":"Well done"}""")
        repondre(201, """{"success":true,"message":"Well done"}""")

        assertTrue(api.enregistrerReussite("12", "ZJ6").estSucces)
        assertTrue(api.enregistrerReussite("12", "ZJ6").estSucces)
    }

    // --- Quand ca se passe mal ------------------------------------------------

    @Test
    fun `une reponse illisible ne fait pas planter`() {
        repondre(201, "<html>502 Bad Gateway</html>")

        val r = api.verifierGrimpeur("12")

        assertTrue(r is ApiResult.Echec)
        assertTrue((r as ApiResult.Echec).message.contains("illisible", true))
    }

    @Test
    fun `une reponse vide ne fait pas planter`() {
        repondre(201, "")
        assertTrue(api.verifierGrimpeur("12") is ApiResult.Echec)
    }

    @Test
    fun `serveur injoignable est signale comme une panne reseau`() {
        // Le cas d'une salle en sous-sol, ou du backend eteint.
        serveur.close()

        val r = api.enregistrerReussite("12", "ZJ6")

        assertTrue(r is ApiResult.Echec)
        assertTrue("doit etre marque comme panne reseau", (r as ApiResult.Echec).reseau)
    }

    @Test
    fun `une reponse trop lente est traitee comme une panne reseau`() {
        serveur.enqueue(
            MockResponse.Builder()
                .code(201)
                .body("""{"success":true}""")
                .bodyDelay(5, TimeUnit.SECONDS)   // au-dela du readTimeout de 2 s
                .build()
        )

        val r = api.verifierGrimpeur("12")

        assertTrue(r is ApiResult.Echec)
        assertTrue((r as ApiResult.Echec).reseau)
    }

    @Test
    fun `une erreur 500 est traitee comme une panne, pas comme un refus`() {
        // « Envoi echoue » se lit comme definitif : le juge passe au grimpeur
        // suivant. « Erreur reseau » se lit comme « reessaie » -- et reessayer
        // est gratuit, l'envoi etant idempotent.
        repondre(500, """{"success":false,"message":"An error occurred"}""")

        val r = api.enregistrerReussite("12", "ZJ6") as ApiResult.Echec

        assertEquals(500, r.codeHttp)
        assertTrue("un 5xx doit inviter a reessayer", r.reseau)
    }

    @Test
    fun `une page html 502 est traitee comme une panne`() {
        // Ce que renvoie Render au reveil a froid, ou un proxy en surcharge.
        repondre(502, "<html>502 Bad Gateway</html>")

        val r = api.enregistrerReussite("12", "ZJ6") as ApiResult.Echec

        assertTrue("un corps illisible doit inviter a reessayer", r.reseau)
    }

    @Test
    fun `un 400 metier reste un refus, pas une panne`() {
        repondre(400, """{"success":false,"message":"Dossard 999 inconnu"}""")

        val r = api.verifierGrimpeur("999") as ApiResult.Echec

        assertTrue("un refus metier ne doit pas inviter a reessayer", !r.reseau)
    }

    @Test
    fun `un 401 est un refus, pas une panne`() {
        // Ce que renverra la production le jour ou la cle d'API passe en mode
        // strict alors que l'application n'en envoie pas.
        repondre(401, """{"success":false,"message":"Cle d'API requise"}""")

        val r = api.enregistrerReussite("12", "ZJ6") as ApiResult.Echec

        assertEquals(401, r.codeHttp)
        assertTrue(!r.reseau)
        assertTrue(r.message.contains("cle", true))
    }

    @Test
    fun `un 409 sans competition active remonte le message`() {
        repondre(409, """{"success":false,"message":"Aucune competition active"}""")

        val r = api.verifierGrimpeur("12")

        assertTrue((r as ApiResult.Echec).message.contains("competition", true))
    }

    // --- L'adresse du serveur -------------------------------------------------

    @Test
    fun `un slash final dans l'adresse ne produit pas de double slash`() {
        // Une adresse collee depuis un navigateur en porte un.
        val avecSlash = ClimbContestApi(
            baseUrl = serveur.url("/").toString(),   // volontairement NON nettoyee
            client = OkHttpClient.Builder().build(),
        )
        repondre(201, """{"success":true}""")

        avecSlash.verifierGrimpeur("1")

        assertEquals("/api/v2/contest/climber/name", serveur.takeRequest().url.encodedPath)
    }

    @Test
    fun `l'adresse de base est respectee`() {
        repondre(201, """{"success":true}""")
        api.verifierGrimpeur("1")
        val url = serveur.takeRequest().url
        assertEquals(serveur.hostName, url.host)
        assertEquals(serveur.port, url.port)
    }
}
