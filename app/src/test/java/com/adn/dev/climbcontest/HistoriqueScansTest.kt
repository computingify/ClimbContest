package com.adn.dev.climbcontest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

/**
 * Le journal de tous les scans, sur la JVM.
 *
 * Ce que ces tests protègent avant tout : **la purge ne peut pas perdre une
 * réussite non envoyée.** C'est la garantie sur laquelle repose l'effacement
 * automatique à trente jours, et la seule raison pour laquelle il est
 * acceptable.
 */
class HistoriqueScansTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private lateinit var racine: File
    private lateinit var historique: HistoriqueScans

    @org.junit.Before
    fun preparer() {
        racine = dossier.newFolder("donnees")
        historique = HistoriqueScans(racine)
    }

    private fun scan(ref: String, dossard: String = "42", bloc: String = "ZV3",
                     quand: String = "2026-11-08T10:42:03Z") =
        ReussiteEnAttente(ref = ref, dossard = dossard, bloc = bloc, scanneLe = quand)

    @Test
    fun `un journal neuf est vide`() {
        assertEquals(emptyList<ScanJournalise>(), historique.tous())
    }

    @Test
    fun `un scan note apparait, en attente`() {
        historique.noter(scan("r1"))

        val tous = historique.tous()
        assertEquals(1, tous.size)
        assertEquals("42", tous[0].dossard)
        assertEquals("ZV3", tous[0].bloc)
        assertEquals(EtatScan.EN_ATTENTE, tous[0].etat)
    }

    @Test
    fun `un scan acquitte ne fait qu'une seule entree`() {
        historique.noter(scan("r1"))
        historique.changerEtat("r1", EtatScan.PARTIE)

        val tous = historique.tous()
        assertEquals(1, tous.size)
        assertEquals(EtatScan.PARTIE, tous[0].etat)
    }

    @Test
    fun `un refus garde son motif`() {
        historique.noter(scan("r1"))
        historique.changerEtat("r1", EtatScan.REFUSEE, "dossard inconnu")

        assertEquals(EtatScan.REFUSEE, historique.tous()[0].etat)
        assertEquals("dossard inconnu", historique.tous()[0].motif)
    }

    @Test
    fun `un refus puis un renvoi reussi laisse l'etat final, et le motif`() {
        historique.noter(scan("r1"))
        historique.changerEtat("r1", EtatScan.REFUSEE, "dossard inconnu")
        historique.changerEtat("r1", EtatScan.PARTIE)

        val tous = historique.tous()
        assertEquals(1, tous.size)
        assertEquals(EtatScan.PARTIE, tous[0].etat)
        // Le motif reste consultable : c'est l'histoire du scan, pas son etat.
        assertEquals("dossard inconnu", tous[0].motif)
    }

    @Test
    fun `l'ordre d'apparition est celui des scans`() {
        historique.noter(scan("r1", dossard = "1"))
        historique.noter(scan("r2", dossard = "2"))
        historique.changerEtat("r1", EtatScan.PARTIE)
        historique.noter(scan("r3", dossard = "3"))

        // Changer l'etat de r1 ne le fait pas remonter : l'ecran liste les
        // scans dans l'ordre ou le juge les a faits.
        assertEquals(listOf("1", "2", "3"), historique.tous().map { it.dossard })
    }

    @Test
    fun `une ligne illisible n'emporte pas ses voisines`() {
        historique.noter(scan("r1", dossard = "1"))
        File(racine, HistoriqueScans.FICHIER).appendText("{ceci n'est pas du json\n")
        historique.noter(scan("r2", dossard = "2"))

        assertEquals(listOf("1", "2"), historique.tous().map { it.dossard })
    }

    @Test
    fun `un changement d'etat orphelin n'invente pas de scan`() {
        historique.changerEtat("jamais-vu", EtatScan.PARTIE)

        assertEquals(emptyList<ScanJournalise>(), historique.tous())
    }

    @Test
    fun `nonArrives ne garde que ce qui n'a pas atteint le serveur`() {
        historique.noter(scan("r1", dossard = "1"))
        historique.noter(scan("r2", dossard = "2"))
        historique.noter(scan("r3", dossard = "3"))
        historique.changerEtat("r2", EtatScan.PARTIE)
        historique.changerEtat("r3", EtatScan.REFUSEE, "bloc inconnu")

        assertEquals(listOf("1", "3"), historique.nonArrives().map { it.dossard })
    }

    // --- La reprise d'un refus ----------------------------------------------

    @Test
    fun `une reprise garde une seule ligne, a sa place`() {
        historique.noter(scan("r1", dossard = "1"))
        historique.noter(scan("r2", dossard = "2"))
        historique.changerEtat("r1", EtatScan.REFUSEE, "dossard inconnu")

        historique.reprendre("r1", "r1-bis")

        val tous = historique.tous()
        assertEquals(2, tous.size)
        // Elle reste en PREMIER : c'est le meme scan, pas un nouveau.
        assertEquals(listOf("1", "2"), tous.map { it.dossard })
        assertEquals("r1-bis", tous[0].ref)
        assertEquals(EtatScan.EN_ATTENTE, tous[0].etat)
        // Le motif du refus precedent reste lisible.
        assertEquals("dossard inconnu", tous[0].motif)
    }

    @Test
    fun `apres une reprise, l'acquittement porte sur la nouvelle reference`() {
        historique.noter(scan("r1"))
        historique.changerEtat("r1", EtatScan.REFUSEE, "dossard inconnu")
        historique.reprendre("r1", "r1-bis")

        historique.changerEtat("r1-bis", EtatScan.PARTIE)

        val tous = historique.tous()
        assertEquals(1, tous.size)
        assertEquals(EtatScan.PARTIE, tous[0].etat)
    }

    @Test
    fun `deux reprises de suite suivent la chaine`() {
        historique.noter(scan("r1"))
        historique.reprendre("r1", "r2")
        historique.reprendre("r2", "r3")

        historique.changerEtat("r3", EtatScan.PARTIE)

        assertEquals(1, historique.tous().size)
        assertEquals(EtatScan.PARTIE, historique.tous()[0].etat)
    }

    @Test
    fun `une reprise orpheline n'invente pas de scan`() {
        historique.reprendre("jamais-vu", "nouvelle")

        assertEquals(emptyList<ScanJournalise>(), historique.tous())
    }

    @Test
    fun `la purge conserve la reference courante apres une reprise`() {
        historique.noter(scan("r1", quand = "2026-11-08T10:00:00Z"))
        historique.reprendre("r1", "r1-bis")
        historique.noter(scan("vieux", quand = "2026-01-01T10:00:00Z"))

        historique.purger(Instant.parse("2026-11-09T10:00:00Z"))

        // Apres consolidation, c'est la reference COURANTE qui est ecrite :
        // c'est celle que le serveur acquittera.
        assertEquals("r1-bis", historique.tous().single().ref)
        historique.changerEtat("r1-bis", EtatScan.PARTIE)
        assertEquals(EtatScan.PARTIE, historique.tous().single().etat)
    }

    // --- La purge -----------------------------------------------------------

    @Test
    fun `la purge efface ce qui a plus de trente jours et garde le reste`() {
        historique.noter(scan("vieux", dossard = "1", quand = "2026-10-01T10:00:00Z"))
        historique.noter(scan("recent", dossard = "2", quand = "2026-11-08T10:00:00Z"))

        val efface = historique.purger(Instant.parse("2026-11-09T10:00:00Z"))

        assertEquals(1, efface)
        assertEquals(listOf("2"), historique.tous().map { it.dossard })
    }

    @Test
    fun `la purge garde l'etat et le motif de ce qu'elle conserve`() {
        historique.noter(scan("r1", quand = "2026-11-08T10:00:00Z"))
        historique.changerEtat("r1", EtatScan.REFUSEE, "dossard inconnu")
        historique.noter(scan("vieux", quand = "2026-01-01T10:00:00Z"))

        historique.purger(Instant.parse("2026-11-09T10:00:00Z"))

        val restant = historique.tous().single()
        assertEquals(EtatScan.REFUSEE, restant.etat)
        assertEquals("dossard inconnu", restant.motif)
    }

    @Test
    fun `la purge consolide le fichier`() {
        historique.noter(scan("r1", quand = "2026-11-08T10:00:00Z"))
        historique.changerEtat("r1", EtatScan.REFUSEE, "dossard inconnu")
        historique.changerEtat("r1", EtatScan.PARTIE)
        historique.noter(scan("vieux", quand = "2026-01-01T10:00:00Z"))

        historique.purger(Instant.parse("2026-11-09T10:00:00Z"))

        // Quatre evenements deviennent une ligne : celle du scan survivant.
        val lignes = File(racine, HistoriqueScans.FICHIER).readLines().filter { it.isNotBlank() }
        assertEquals(1, lignes.size)
    }

    @Test
    fun `la purge ne reecrit rien quand il n'y a rien a jeter`() {
        historique.noter(scan("r1", quand = "2026-11-08T10:00:00Z"))
        val avant = File(racine, HistoriqueScans.FICHIER).readText()

        assertEquals(0, historique.purger(Instant.parse("2026-11-09T10:00:00Z")))
        assertEquals(avant, File(racine, HistoriqueScans.FICHIER).readText())
    }

    @Test
    fun `un scan dont l'heure est illisible n'est jamais efface`() {
        historique.noter(scan("sans-date", quand = "pas une date"))
        historique.noter(scan("vieux", dossard = "9", quand = "2026-01-01T10:00:00Z"))

        historique.purger(Instant.parse("2026-11-09T10:00:00Z"))

        // On n'efface pas ce qu'on ne sait pas dater.
        assertEquals(listOf("sans-date"), historique.tous().map { it.ref })
    }

    /**
     * Le test qui verrouille la garantie de la spec : la purge est une
     * opération sur une **vue**, pas sur la source de vérité de l'envoi.
     */
    @Test
    fun `la purge ne touche pas aux reussites qui attendent d'etre envoyees`() {
        val file = FileDeReussites(racine)
        val vieille = scan("vieille", dossard = "7", quand = "2026-01-01T10:00:00Z")
        file.ajouter(vieille)
        historique.noter(vieille)

        historique.purger(Instant.parse("2026-11-09T10:00:00Z"))

        // Le journal l'a oubliee — c'est ce qu'on lui demande...
        assertTrue(historique.tous().isEmpty())
        // ...mais elle est toujours en file, et elle partira.
        assertEquals(1, file.nombreEnAttente())
        assertEquals("7", file.enAttente().single().dossard)
    }

    @Test
    fun `la reference courte tient sur six caracteres`() {
        historique.noter(scan("8f3c1d20-aaaa-bbbb-cccc-ddddeeeeffff"))

        assertEquals("8f3c1d", historique.tous().single().refCourte())
    }

    @Test
    fun `un etat inconnu ne fait pas disparaitre le scan`() {
        historique.noter(scan("r1"))
        File(racine, HistoriqueScans.FICHIER)
            .appendText("""{"ref":"r1","etat":"chose_inconnue"}""" + "\n")

        // On retombe sur « en attente » : ne jamais pretendre qu'une reussite
        // est arrivee sans le savoir.
        assertEquals(EtatScan.EN_ATTENTE, historique.tous().single().etat)
        assertNull(historique.tous().single().motif)
    }
}
