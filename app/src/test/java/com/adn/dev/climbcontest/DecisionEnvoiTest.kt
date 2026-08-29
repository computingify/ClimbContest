package com.adn.dev.climbcontest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la logique d'envoi côté juge.
 *
 * Ces quelques lignes décident de ce qu'un juge fait après avoir appuyé sur
 * « Envoyer » : réessayer, ou passer au grimpeur suivant. Se tromper ici perd
 * des réussites en silence — c'est pour ça qu'elles ont été sorties de la
 * coroutine, où aucun test ne pouvait les atteindre sans émulateur.
 */
class DecisionEnvoiTest {

    // --- Avant l'appel réseau -------------------------------------------------

    @Test
    fun `un scan complet part vers le serveur`() {
        assertNull(DecisionEnvoi.avantEnvoi("12", "ZJ6"))
    }

    @Test
    fun `sans grimpeur, on previent au lieu d'envoyer`() {
        assertEquals(MessageJuge.RIEN_A_ENVOYER, DecisionEnvoi.avantEnvoi(null, "ZJ6"))
    }

    @Test
    fun `sans bloc, on previent au lieu d'envoyer`() {
        assertEquals(MessageJuge.RIEN_A_ENVOYER, DecisionEnvoi.avantEnvoi("12", null))
    }

    @Test
    fun `un ecran vide ne doit jamais rester muet`() {
        // Le cas du juge qui appuie deux fois : apres un envoi reussi l'ecran
        // s'est vide, le second appui ne doit pas ressembler a un plantage.
        assertEquals(MessageJuge.RIEN_A_ENVOYER, DecisionEnvoi.avantEnvoi(null, null))
    }

    @Test
    fun `une valeur vide vaut une valeur absente`() {
        // Le ViewModel peut porter une chaine vide plutot qu'un null selon le
        // chemin de reset : les deux doivent se comporter pareil.
        assertEquals(MessageJuge.RIEN_A_ENVOYER, DecisionEnvoi.avantEnvoi("", "ZJ6"))
        assertEquals(MessageJuge.RIEN_A_ENVOYER, DecisionEnvoi.avantEnvoi("12", "   "))
    }

    // --- Après la réponse du serveur ------------------------------------------

    @Test
    fun `un succes est annonce comme valide`() {
        assertEquals(
            MessageJuge.VALIDE,
            DecisionEnvoi.apresEnvoi(ApiResult.Succes("Dupont Lea", "Well done")),
        )
    }

    @Test
    fun `une panne invite a reessayer`() {
        val panne = ApiResult.Echec("Aucun acces au serveur", reseau = true)
        assertEquals(MessageJuge.ERREUR_RESEAU, DecisionEnvoi.apresEnvoi(panne))
    }

    @Test
    fun `un refus metier n'invite pas a reessayer`() {
        // « Dossard inconnu » ne se resoudra pas en reappuyant : il faut
        // rescanner. Confondre les deux fait perdre du temps au juge.
        val refus = ApiResult.Echec("Dossard 999 inconnu", codeHttp = 400)
        assertEquals(MessageJuge.ENVOI_REFUSE, DecisionEnvoi.apresEnvoi(refus))
    }

    @Test
    fun `un 500 est presente comme une panne, pas comme un refus`() {
        // Ce test couvre UNIQUEMENT la traduction : on lui donne a la main ce
        // que ClimbContestApi est cense produire sur un 5xx. Que l'API le
        // produise vraiment est verifie ailleurs, avec un serveur factice
        // (ClimbContestApiTest, « une erreur 500 est traitee comme une panne »).
        val cinqCents = ApiResult.Echec("An error occurred", codeHttp = 500, reseau = true)
        assertEquals(MessageJuge.ERREUR_RESEAU, DecisionEnvoi.apresEnvoi(cinqCents))
    }

    // --- Le scan, avant même l'envoi ------------------------------------------

    @Test
    fun `un scan reconnu est accepte`() {
        assertEquals(MessageScan.ACCEPTE,
            DecisionEnvoi.apresScan(ApiResult.Succes("Dupont Lea")))
    }

    @Test
    fun `un qr refuse par le serveur dit de rescanner`() {
        val refus = ApiResult.Echec("Dossard 999 inconnu", codeHttp = 400)
        assertEquals(MessageScan.REFUSE, DecisionEnvoi.apresScan(refus))
    }

    @Test
    fun `une coupure reseau au scan ne dit pas que le code est mauvais`() {
        // Le defaut d'origine : « Identifiant incorrect. Recommencez. » sur un
        // wifi qui hoquette. Le juge en concluait que le grimpeur n'etait pas
        // inscrit et allait chercher un organisateur, pour un QR valide.
        val panne = ApiResult.Echec("Aucun acces au serveur", reseau = true)
        assertEquals(MessageScan.ERREUR_RESEAU, DecisionEnvoi.apresScan(panne))
    }

    @Test
    fun `un 500 au scan est une panne, pas un refus`() {
        val cinqCents = ApiResult.Echec("boom", codeHttp = 500, reseau = true)
        assertEquals(MessageScan.ERREUR_RESEAU, DecisionEnvoi.apresScan(cinqCents))
    }

    @Test
    fun `seul un scan accepte est retenu`() {
        // Retenir un scan non confirme laisserait le juge envoyer un dossard
        // que le serveur n'a jamais valide.
        assertTrue(DecisionEnvoi.doitRetenirLeScan(MessageScan.ACCEPTE))
        for (autre in MessageScan.entries.filter { it != MessageScan.ACCEPTE }) {
            assertFalse("$autre ne doit pas etre retenu",
                DecisionEnvoi.doitRetenirLeScan(autre))
        }
    }

    // --- Ce qui efface l'écran ------------------------------------------------

    @Test
    fun `seul un succes efface l'ecran`() {
        // La regle la plus importante du fichier : effacer le scan apres un
        // echec perdrait la reussite sans que personne ne s'en apercoive.
        assertTrue(DecisionEnvoi.doitReinitialiser(MessageJuge.VALIDE))
        for (autre in MessageJuge.entries.filter { it != MessageJuge.VALIDE }) {
            assertFalse("$autre ne doit pas effacer l'ecran",
                DecisionEnvoi.doitReinitialiser(autre))
        }
    }

    // --- Ce que le journal des scans retient d'un envoi (spec 011) ----------

    private fun bilan(acquittees: Set<String>, refusees: List<RefusServeur>) =
        BilanEnvoi(envoyees = acquittees.size - refusees.size, refusees = refusees,
                   restantes = 0, acquittees = acquittees)

    @Test
    fun `un envoi propre fait passer chaque reference a partie`() {
        val suites = DecisionEnvoi.pourLeJournal(bilan(setOf("r1", "r2"), emptyList()))

        assertEquals(2, suites.size)
        assertTrue(suites.all { it.etat == EtatScan.PARTIE })
        assertTrue(suites.all { it.motif == null })
    }

    /**
     * La regle qui compte. Une reussite refusee est AUSSI acquittee : le serveur
     * a statue sur elle, donc elle quitte la file. Si l'acquittement l'emportait,
     * le journal afficherait « arrive » a cote d'un scan que le serveur vient de
     * rejeter.
     */
    @Test
    fun `un refus l'emporte sur l'acquittement qui l'accompagne`() {
        val suites = DecisionEnvoi.pourLeJournal(
            bilan(setOf("r1", "r2"), listOf(RefusServeur("r2", "dossard inconnu")))
        )

        assertEquals(EtatScan.PARTIE, suites.first { it.ref == "r1" }.etat)
        val refusee = suites.first { it.ref == "r2" }
        assertEquals(EtatScan.REFUSEE, refusee.etat)
        assertEquals("dossard inconnu", refusee.motif)
    }

    @Test
    fun `un envoi rate ne dit rien au journal`() {
        // Rien n'est acquitte : la file garde tout, et le journal ne bouge pas.
        // Surtout pas pour passer les scans a « refuse » -- ils repartiront.
        val rate = BilanEnvoi(envoyees = 0, refusees = emptyList(), restantes = 3,
                              echec = "Serveur injoignable")

        assertEquals(emptyList<SuiteDeScan>(), DecisionEnvoi.pourLeJournal(rate))
    }

    @Test
    fun `un refus sans acquittement correspondant est ignore`() {
        // Ne devrait pas arriver -- l'Expediteur acquitte tout ce qu'il refuse.
        // Mais inventer une entree pour une reference dont on ne sait rien
        // serait pire que de ne rien faire.
        val suites = DecisionEnvoi.pourLeJournal(
            bilan(setOf("r1"), listOf(RefusServeur("inconnue", "peu importe")))
        )

        assertEquals(listOf("r1"), suites.map { it.ref })
        assertEquals(EtatScan.PARTIE, suites.single().etat)
    }
}
