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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

/**
 * La politique d'envoi — quand partir, et combien attendre après un échec.
 *
 * Pure : ni réseau, ni horloge. C'est elle qui arbitre entre « trop de
 * requêtes » et « écran de résultats en retard ».
 */
class PolitiqueEnvoiTest {

    @Test
    fun `rien a envoyer, on ne part pas`() {
        assertFalse(PolitiqueEnvoi.doitEnvoyer(0, 99_999, 0))
    }

    @Test
    fun `un lot plein part sans attendre`() {
        assertTrue(PolitiqueEnvoi.doitEnvoyer(PolitiqueEnvoi.LOT_PLEIN, 0, 0))
    }

    @Test
    fun `un lot incomplet attend le delai`() {
        assertFalse("avant le delai", PolitiqueEnvoi.doitEnvoyer(2, 5_000, 0))
        assertTrue("apres le delai", PolitiqueEnvoi.doitEnvoyer(2, PolitiqueEnvoi.DELAI_MS, 0))
    }

    @Test
    fun `une seule reussite finit par partir`() {
        // Le cas de la fin de competition : plus rien n'arrive, mais la
        // derniere reussite ne doit pas rester coincee.
        assertTrue(PolitiqueEnvoi.doitEnvoyer(1, PolitiqueEnvoi.DELAI_MS, 0))
    }

    @Test
    fun `le retrait double a chaque echec, puis plafonne`() {
        assertEquals(0L, PolitiqueEnvoi.attenteApresEchec(0))
        assertEquals(2_000L, PolitiqueEnvoi.attenteApresEchec(1))
        assertEquals(4_000L, PolitiqueEnvoi.attenteApresEchec(2))
        assertEquals(8_000L, PolitiqueEnvoi.attenteApresEchec(3))
        assertEquals(PolitiqueEnvoi.RETRAIT_MAX_MS, PolitiqueEnvoi.attenteApresEchec(20))
    }

    @Test
    fun `le plafond compte autant que la croissance`() {
        // Sans plafond, un backend eteint une heure ferait attendre le premier
        // renvoi une demi-heure APRES son retour.
        assertTrue(PolitiqueEnvoi.attenteApresEchec(100) <= PolitiqueEnvoi.RETRAIT_MAX_MS)
    }

    @Test
    fun `on n'envoie pas pendant le retrait`() {
        assertFalse(PolitiqueEnvoi.doitEnvoyer(10, 1_000, echecsConsecutifs = 1))
        assertTrue(PolitiqueEnvoi.doitEnvoyer(10, 3_000, echecsConsecutifs = 1))
    }

    @Test
    fun `forcer ignore le lot et le delai`() {
        // Le bouton « tout envoyer maintenant », en fin de competition.
        assertFalse(PolitiqueEnvoi.doitEnvoyer(1, 0, 0))
        assertTrue(PolitiqueEnvoi.doitEnvoyer(1, 0, 0, forcer = true))
    }

    @Test
    fun `forcer ne contourne PAS le retrait`() {
        // Sinon appuyer en boucle sur un serveur eteint noierait le telephone.
        assertFalse(PolitiqueEnvoi.doitEnvoyer(10, 500, echecsConsecutifs = 3, forcer = true))
    }

    @Test
    fun `la taille du lot est plafonnee`() {
        assertEquals(3, PolitiqueEnvoi.tailleLot(3))
        assertEquals(PolitiqueEnvoi.LOT_MAX, PolitiqueEnvoi.tailleLot(500))
    }
}

/**
 * L'expéditeur, contre un vrai serveur factice.
 *
 * L'invariant vérifié partout ici : **une réussite ne quitte la file que si le
 * serveur a explicitement statué sur elle.**
 */
class ExpediteurTest {

    @get:Rule
    val dossierTemporaire = TemporaryFolder()

    private lateinit var serveur: MockWebServer
    private lateinit var file: FileDeReussites
    private lateinit var expediteur: Expediteur

    @Before
    fun preparer() {
        serveur = MockWebServer()
        serveur.start()
        file = FileDeReussites(dossierTemporaire.newFolder("file"))
        expediteur = Expediteur(
            file,
            ClimbContestApi(
                baseUrl = serveur.url("/").toString(),
                client = OkHttpClient.Builder()
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(2, TimeUnit.SECONDS).build(),
            ),
        )
    }

    @After
    fun arreter() = serveur.close()

    private fun repondre(code: Int, corps: String) {
        serveur.enqueue(MockResponse.Builder().code(code).body(corps)
            .setHeader("Content-Type", "application/json").build())
    }

    private fun remplir(n: Int) = (1..n).forEach {
        file.ajouter(ReussiteEnAttente("r$it", "$it", "ZJ$it", "2026-11-15T09:00:0${it}Z"))
    }

    private fun reponseOk(vararg refs: String, version: Int = 7): String {
        val resultats = refs.joinToString(",") { """{"ref":"$it","etat":"enregistree"}""" }
        return """{"success":true,"resultats":[$resultats],"catalogue_version":$version}"""
    }

    // --- Le cas nominal -------------------------------------------------------

    @Test
    fun `rien a envoyer rend null`() {
        assertEquals(null, expediteur.tenter())
    }

    @Test
    fun `un lot accepte vide la file`() {
        remplir(3)
        repondre(200, reponseOk("r1", "r2", "r3"))

        val bilan = expediteur.tenter()!!

        assertTrue(bilan.aReussi)
        assertEquals(3, bilan.envoyees)
        assertEquals(0, bilan.restantes)
        assertEquals(0, file.nombreEnAttente())
    }

    @Test
    fun `le corps envoye porte les cles attendues par le serveur`() {
        remplir(1)
        repondre(200, reponseOk("r1"))

        expediteur.tenter()

        val requete = serveur.takeRequest()
        assertEquals("/api/v3/successes", requete.url.encodedPath)
        val items = JSONObject(requete.body!!.utf8()).getJSONArray("items")
        assertEquals(1, items.length())
        val item = items.getJSONObject(0)
        assertEquals("r1", item.getString("ref"))
        assertEquals("1", item.getString("bib"))
        assertEquals("ZJ1", item.getString("bloc"))
    }

    @Test
    fun `deja_connue est un succes et libere la file`() {
        // Le telephone a reessaye apres un reseau coupe : le serveur avait deja
        // la reussite. Ce n'est pas une erreur.
        remplir(1)
        repondre(200, """{"success":true,"resultats":[{"ref":"r1","etat":"deja_connue"}]}""")

        val bilan = expediteur.tenter()!!

        assertTrue(bilan.aReussi)
        assertEquals(0, file.nombreEnAttente())
    }

    @Test
    fun `un refus libere la file mais est signale`() {
        // Un QR mal imprime : reessayer ne servirait a rien, mais le juge doit
        // le savoir.
        remplir(1)
        repondre(200, """{"success":true,"resultats":[
            {"ref":"r1","etat":"refusee","message":"Dossard 1 inconnu"}]}""")

        val bilan = expediteur.tenter()!!

        assertEquals(0, file.nombreEnAttente())
        assertEquals(1, bilan.refusees.size)
        assertEquals("Dossard 1 inconnu", bilan.refusees[0].message)
        assertEquals("les refus ne comptent pas comme envoyees", 0, bilan.envoyees)
    }

    @Test
    fun `la version du catalogue est remontee`() {
        remplir(1)
        repondre(200, reponseOk("r1", version = 12))
        assertEquals(12, expediteur.tenter()!!.catalogueVersion)
    }

    // --- Ce qui ne doit RIEN perdre ------------------------------------------

    @Test
    fun `serveur injoignable - la file reste intacte`() {
        remplir(3)
        serveur.close()

        val bilan = expediteur.tenter()!!

        assertFalse(bilan.aReussi)
        assertEquals(3, file.nombreEnAttente())
    }

    @Test
    fun `un 401 ne vide pas la file`() {
        // Le jour ou le mode strict serait active par erreur : surtout ne pas
        // jeter ce que le juge a valide.
        remplir(3)
        repondre(401, """{"success":false,"message":"Cle d'API requise"}""")

        val bilan = expediteur.tenter()!!

        assertFalse(bilan.aReussi)
        assertEquals(3, file.nombreEnAttente())
    }

    @Test
    fun `un 409 sans competition active ne vide pas la file`() {
        remplir(2)
        repondre(409, """{"success":false,"message":"Aucune competition active"}""")
        expediteur.tenter()
        assertEquals(2, file.nombreEnAttente())
    }

    @Test
    fun `un corps illisible ne vide pas la file`() {
        remplir(2)
        repondre(200, "<html>502</html>")
        expediteur.tenter()
        assertEquals(2, file.nombreEnAttente())
    }

    @Test
    fun `une reponse partielle ne libere que ce qui est mentionne`() {
        // LE cas qui justifie tout le design : le serveur n'a statue que sur
        // deux elements sur trois. Le troisieme repart.
        remplir(3)
        repondre(200, reponseOk("r1", "r2"))

        expediteur.tenter()

        assertEquals(listOf("r3"), file.enAttente().map { it.ref })
    }

    @Test
    fun `un etat inconnu du serveur fait garder la reussite`() {
        // Si le serveur inventait un etat, le defaut doit rester « je garde ».
        remplir(1)
        repondre(200, """{"success":true,"resultats":[{"ref":"r1","etat":"peut-etre"}]}""")
        expediteur.tenter()
        assertEquals(1, file.nombreEnAttente())
    }

    @Test
    fun `une reponse sans resultats ne vide rien`() {
        remplir(2)
        repondre(200, """{"success":true}""")
        expediteur.tenter()
        assertEquals(2, file.nombreEnAttente())
    }

    // --- La reprise -----------------------------------------------------------

    @Test
    fun `le serveur revient apres une panne et tout part`() {
        remplir(3)
        serveur.close()
        expediteur.tenter()
        assertEquals(3, file.nombreEnAttente())

        // Nouveau serveur, comme un reseau qui revient.
        serveur = MockWebServer().also { it.start() }
        val expediteur2 = Expediteur(file, ClimbContestApi(
            baseUrl = serveur.url("/").toString(), client = OkHttpClient()))
        repondre(200, reponseOk("r1", "r2", "r3"))

        expediteur2.tenter()

        assertEquals(0, file.nombreEnAttente())
    }

    @Test
    fun `les echecs consecutifs sont comptes puis remis a zero`() {
        remplir(1)
        serveur.close()
        expediteur.tenter()
        expediteur.tenter()
        assertEquals(2, expediteur.echecsConsecutifs)

        serveur = MockWebServer().also { it.start() }
        val e2 = Expediteur(file, ClimbContestApi(
            baseUrl = serveur.url("/").toString(), client = OkHttpClient()))
        repondre(200, reponseOk("r1"))
        e2.tenter()
        assertEquals(0, e2.echecsConsecutifs)
    }

    @Test
    fun `une file plus grande que le lot part en plusieurs fois`() {
        remplir(120)
        repondre(200, reponseOk(*(1..PolitiqueEnvoi.LOT_MAX).map { "r$it" }.toTypedArray()))

        val bilan = expediteur.tenter()!!

        assertEquals(PolitiqueEnvoi.LOT_MAX, bilan.envoyees)
        assertEquals(120 - PolitiqueEnvoi.LOT_MAX, bilan.restantes)
    }
}
