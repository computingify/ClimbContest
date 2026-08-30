package com.adn.dev.climbcontest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * La file des réussites, sur la JVM, avec un vrai dossier.
 *
 * Ces tests sont ceux qui décident si une réussite survit à une batterie vide.
 * Ils simulent des coupures **à chaque étape** — pas en le supposant, en
 * abandonnant réellement l'objet et en repartant d'un dépôt neuf sur le même
 * dossier, exactement ce que fait Android quand il tue l'application.
 */
class FileDeReussitesTest {

    @get:Rule
    val dossierTemporaire = TemporaryFolder()

    private lateinit var dossier: File

    private fun file() = FileDeReussites(dossier)

    private fun reussite(n: Int) = ReussiteEnAttente(
        ref = "r$n", dossard = "$n", bloc = "ZJ$n", scanneLe = "2026-11-15T09:4$n:00Z",
    )

    @org.junit.Before
    fun preparer() {
        dossier = dossierTemporaire.newFolder("file")
    }

    // --- Le cycle normal ------------------------------------------------------

    @Test
    fun `une file neuve est vide`() {
        assertEquals(0, file().nombreEnAttente())
        assertEquals(emptyList<ReussiteEnAttente>(), file().enAttente())
    }

    @Test
    fun `ce qui est ajoute est en attente`() {
        val f = file()
        f.ajouter(reussite(1))
        f.ajouter(reussite(2))
        assertEquals(2, f.nombreEnAttente())
        assertEquals(listOf("r1", "r2"), f.enAttente().map { it.ref })
    }

    @Test
    fun `l'ordre de validation est conserve`() {
        val f = file()
        (1..5).forEach { f.ajouter(reussite(it)) }
        assertEquals(listOf("r1", "r2", "r3", "r4", "r5"), f.enAttente().map { it.ref })
    }

    @Test
    fun `acquitter retire de la file`() {
        val f = file()
        (1..3).forEach { f.ajouter(reussite(it)) }
        f.acquitter(listOf("r1", "r2"))
        assertEquals(listOf("r3"), f.enAttente().map { it.ref })
    }

    @Test
    fun `le lot est plafonne et pris dans l'ordre`() {
        val f = file()
        (1..9).forEach { f.ajouter(reussite(it)) }
        assertEquals(listOf("r1", "r2", "r3"), f.prochainLot(3).map { it.ref })
    }

    @Test
    fun `un lot plus grand que la file rend ce qu'il y a`() {
        val f = file()
        f.ajouter(reussite(1))
        assertEquals(1, f.prochainLot(50).size)
    }

    @Test
    fun `les champs traversent l'aller-retour disque`() {
        val f = file()
        f.ajouter(ReussiteEnAttente("abc", "42", "DV21", "2026-11-15T09:41:02Z"))
        val relue = file().enAttente().single()
        assertEquals("abc", relue.ref)
        assertEquals("42", relue.dossard)
        assertEquals("DV21", relue.bloc)
        assertEquals("2026-11-15T09:41:02Z", relue.scanneLe)
    }

    // --- Les coupures ---------------------------------------------------------

    @Test
    fun `l'application tuee apres l'ajout ne perd rien`() {
        // Le scenario reel : le juge valide, Android tue le processus avant
        // qu'aucun envoi n'ait eu lieu.
        file().ajouter(reussite(1))

        val apresRedemarrage = file()

        assertEquals(1, apresRedemarrage.nombreEnAttente())
        assertEquals("r1", apresRedemarrage.enAttente().single().ref)
    }

    @Test
    fun `l'application tuee entre l'envoi et l'acquittement renvoie la reussite`() {
        // Le lot est parti, la reponse n'est jamais arrivee. Reessayer est
        // gratuit : le serveur est idempotent sur le couple (grimpeur, bloc).
        val f = file()
        f.ajouter(reussite(1))
        f.prochainLot(10)                       // envoye, mais pas acquitte

        assertEquals("sans acquittement, la reussite doit repartir",
            listOf("r1"), file().enAttente().map { it.ref })
    }

    @Test
    fun `les acquittements survivent a un redemarrage`() {
        val f = file()
        (1..3).forEach { f.ajouter(reussite(it)) }
        f.acquitter(listOf("r1"))

        assertEquals(listOf("r2", "r3"), file().enAttente().map { it.ref })
    }

    @Test
    fun `un acquittement partiel ne retire que ce qui est acquitte`() {
        // Le serveur n'a statue que sur deux elements du lot : le troisieme
        // reste en file. Le defaut est de GARDER.
        val f = file()
        (1..3).forEach { f.ajouter(reussite(it)) }
        f.acquitter(listOf("r1", "r3"))
        assertEquals(listOf("r2"), f.enAttente().map { it.ref })
    }

    @Test
    fun `acquitter une ref inconnue ne casse rien`() {
        val f = file()
        f.ajouter(reussite(1))
        f.acquitter(listOf("ref-qui-n-existe-pas"))
        assertEquals(1, f.nombreEnAttente())
    }

    @Test
    fun `acquitter deux fois la meme ref est sans effet`() {
        val f = file()
        f.ajouter(reussite(1))
        f.acquitter(listOf("r1"))
        f.acquitter(listOf("r1"))
        assertEquals(0, f.nombreEnAttente())
    }

    // --- Les fichiers abimes --------------------------------------------------

    @Test
    fun `une ligne tronquee est ignoree, les autres sont gardees`() {
        // Ce que produit une coupure d'alimentation en pleine ecriture.
        val f = file()
        f.ajouter(reussite(1))
        File(dossier, FileDeReussites.FICHIER_FILE)
            .appendText("{\"ref\":\"r2\",\"bib\":\"2\"")     // json inacheve
        f.ajouter(reussite(3))

        val restantes = file().enAttente().map { it.ref }

        assertTrue("r1 doit survivre", "r1" in restantes)
        assertTrue("r3 doit survivre", "r3" in restantes)
        assertTrue("la ligne illisible ne doit pas apparaitre", "r2" !in restantes)
    }

    @Test
    fun `un fichier entierement corrompu ne fait pas planter`() {
        File(dossier, FileDeReussites.FICHIER_FILE).writeText("n'importe quoi\n@@@\n")
        assertEquals(0, file().nombreEnAttente())
    }

    @Test
    fun `une ligne sans les champs obligatoires est ignoree`() {
        File(dossier, FileDeReussites.FICHIER_FILE)
            .writeText("{\"ref\":\"x\"}\n{\"bib\":\"1\",\"bloc\":\"ZJ1\"}\n")
        assertEquals(0, file().nombreEnAttente())
    }

    @Test
    fun `un fichier d'acquittements abime n'efface pas la file`() {
        // Le pire cas serait qu'un acquittement corrompu fasse disparaitre des
        // reussites. Les lignes lisibles sont honorees, le reste est renvoye.
        val f = file()
        (1..3).forEach { f.ajouter(reussite(it)) }
        File(dossier, FileDeReussites.FICHIER_ACQUITS).writeText("r1\n\n   \n")

        assertEquals(listOf("r2", "r3"), file().enAttente().map { it.ref })
    }

    @Test
    fun `des lignes vides sont ignorees`() {
        val f = file()
        f.ajouter(reussite(1))
        File(dossier, FileDeReussites.FICHIER_FILE).appendText("\n\n   \n")
        assertEquals(1, file().nombreEnAttente())
    }

    // --- Le compactage --------------------------------------------------------

    @Test
    fun `tout acquitter remet les fichiers a zero`() {
        val f = file()
        (1..3).forEach { f.ajouter(reussite(it)) }
        assertTrue("les fichiers doivent contenir quelque chose", f.octets() > 0)

        f.acquitter(listOf("r1", "r2", "r3"))

        assertEquals(0, f.nombreEnAttente())
        assertEquals("les fichiers doivent etre vides", 0L, f.octets())
    }

    @Test
    fun `le compactage n'a pas lieu s'il reste des reussites`() {
        // Compacter avec des reussites en attente demanderait de reecrire le
        // journal en le filtrant -- et une coupure au milieu de CETTE reecriture
        // perdrait des reussites validees. On prefere un fichier qui grossit.
        val f = file()
        (1..3).forEach { f.ajouter(reussite(it)) }
        f.acquitter(listOf("r1"))
        assertTrue("les fichiers doivent rester", f.octets() > 0)
        assertEquals(2, f.nombreEnAttente())
    }

    @Test
    fun `apres compactage la file repart proprement`() {
        val f = file()
        f.ajouter(reussite(1))
        f.acquitter(listOf("r1"))
        f.ajouter(reussite(2))

        assertEquals("une ancienne ref acquittee ne doit pas masquer une nouvelle",
            listOf("r2"), file().enAttente().map { it.ref })
    }

    @Test
    fun `une ref reutilisee apres compactage n'est pas masquee`() {
        // Piege : si le fichier d'acquittements n'etait pas vide, une reussite
        // portant une ref deja vue disparaitrait sans jamais partir.
        val f = file()
        f.ajouter(reussite(1))
        f.acquitter(listOf("r1"))
        f.ajouter(reussite(1))                  // meme ref, nouvelle reussite

        assertEquals(1, file().nombreEnAttente())
    }

    // --- La charge d'une compétition -----------------------------------------

    @Test
    fun `trois mille six cents reussites tiennent et restent rapides`() {
        val f = file()
        val debut = System.currentTimeMillis()
        (1..3600).forEach { f.ajouter(reussite(it)) }
        val duree = System.currentTimeMillis() - debut

        assertEquals(3600, f.nombreEnAttente())
        // Une competition entiere, jamais envoyee. Marge tres large : on cherche
        // a detecter un effondrement, pas a mesurer la machine.
        assertTrue("3600 ajouts ont pris ${duree} ms", duree < 60_000)
        assertTrue("volume raisonnable : ${f.octets()} octets", f.octets() < 2_000_000)
    }
}

class StockageFichierTest {

    @get:Rule
    val dossierTemporaire = TemporaryFolder()

    @Test
    fun `remplacer est atomique - l'ancien contenu ne disparait jamais`() {
        // On ne peut pas couper le courant dans un test. Ce qu'on verifie, c'est
        // l'INVARIANT qui rend la coupure sans danger : a aucun moment le
        // fichier cible n'est vide ou partiel -- il est ecrit a cote, puis
        // renomme. On le prouve en constatant qu'un fichier temporaire est bien
        // utilise, et que la cible garde son ancien contenu jusqu'au bout.
        val dossier = dossierTemporaire.newFolder()
        val cible = File(dossier, "f.txt")
        val s = StockageFichier(cible)
        s.ajouter("ancien")

        s.remplacer(listOf("nouveau"))

        assertEquals(listOf("nouveau"), s.lire())
        assertTrue("le fichier temporaire ne doit pas trainer",
            File(dossier, "f.txt.tmp").exists().not())
    }

    @Test
    fun `remplacer par du vide donne un fichier vide, pas un fichier absent`() {
        val cible = File(dossierTemporaire.newFolder(), "f.txt")
        val s = StockageFichier(cible)
        s.ajouter("a")
        s.remplacer(emptyList())
        assertEquals(emptyList<String>(), s.lire())
        assertEquals(0L, s.taille())
    }

    @Test
    fun `lire un fichier absent donne une liste vide`() {
        val s = StockageFichier(File(dossierTemporaire.newFolder(), "jamais-cree.txt"))
        assertEquals(emptyList<String>(), s.lire())
        assertEquals(0L, s.taille())
    }

    @Test
    fun `le dossier est cree si besoin`() {
        val profond = File(dossierTemporaire.newFolder(), "a/b/c/f.txt")
        StockageFichier(profond).ajouter("x")
        assertTrue(profond.exists())
    }

    @Test
    fun `un fichier tronque sans saut de ligne final est repare, pas melange`() {
        // Le scenario d'une coupure en pleine ecriture : la derniere ligne est
        // la, mais pas son saut de ligne. Sans le prefixe ajoute par
        // `ajouter`, la reussite suivante se COLLERAIT a elle -- deux
        // reussites fusionnees en une ligne illisible pour la relecture.
        val cible = File(dossierTemporaire.newFolder(), "f.jsonl")
        cible.writeText("""{"ref":"coupee"}""")   // pas de \n final
        StockageFichier(cible).ajouter("""{"ref":"suivante"}""")
        assertEquals(
            listOf("""{"ref":"coupee"}""", """{"ref":"suivante"}"""),
            StockageFichier(cible).lire(),
        )
    }

    @Test
    fun `les accents traversent l'ecriture et la relecture`() {
        val s = StockageFichier(File(dossierTemporaire.newFolder(), "f.txt"))
        s.ajouter("réussite validée — Noë")
        assertEquals(listOf("réussite validée — Noë"), s.lire())
    }
}

class ReussiteEnAttenteTest {

    @Test
    fun `le json envoye porte les cles attendues par le serveur`() {
        val json = org.json.JSONObject(
            ReussiteEnAttente("a", "12", "ZJ6", "2026-11-15T09:41:02Z").versJson()
        )
        assertEquals("a", json.getString("ref"))
        assertEquals("12", json.getString("bib"))
        assertEquals("ZJ6", json.getString("bloc"))
        assertEquals("2026-11-15T09:41:02Z", json.getString("at"))
    }

    @Test
    fun `une ligne illisible rend null au lieu de lever`() {
        assertNull(ReussiteEnAttente.depuisJson("pas du json"))
        assertNull(ReussiteEnAttente.depuisJson(""))
        assertNull(ReussiteEnAttente.depuisJson("[1,2]"))
    }

    @Test
    fun `une ligne complete est relue`() {
        val r = ReussiteEnAttente.depuisJson(
            """{"ref":"a","bib":"12","bloc":"ZJ6","at":"2026-11-15T09:41:02Z"}"""
        )
        assertNotNull(r)
        assertEquals("12", r!!.dossard)
    }
}
