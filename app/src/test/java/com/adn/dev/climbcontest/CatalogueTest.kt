package com.adn.dev.climbcontest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Le catalogue local — ce qui supprime deux des trois allers-retours du juge.
 *
 * Le contrat testé ici est celui de `GET /api/v2/catalog`, tel que le serveur le
 * sert réellement (vérifié contre la VM le 28/08).
 */
class CatalogueTest {

    private val reponseServeur = """
        {
          "competition": {"id": 1, "nom": "Test", "statut": "en_cours"},
          "version": 7,
          "participants": [
            {"id": 1, "dossard": 12, "nom": "Dupont Lea", "club": "Les Lezards", "categorie": "U11 F"},
            {"id": 2, "dossard": 13, "nom": "Martin Tom", "club": "La Grimpe", "categorie": "U13 H"}
          ],
          "blocs": [
            {"id": 1, "tag": "ZJ6", "numero": 6, "couleur": "Jaune", "circuits": ["U11"]},
            {"id": 2, "tag": "DV21", "numero": 21, "couleur": "Bleu", "circuits": ["U13"]}
          ],
          "circuits": ["U11", "U13"]
        }
    """.trimIndent()

    // --- Ce qu'on lit du serveur ---------------------------------------------

    @Test
    fun `les participants et les blocs sont indexes`() {
        val c = Catalogue.depuisReponseServeur(reponseServeur)!!
        assertEquals(2, c.nombreParticipants)
        assertEquals(2, c.nombreBlocs)
        assertEquals(7, c.version)
    }

    @Test
    fun `un dossard connu rend le nom du grimpeur`() {
        // C'est ce que le juge lit pour confirmer qu'il a scanne la bonne personne.
        val c = Catalogue.depuisReponseServeur(reponseServeur)!!
        assertEquals("Dupont Lea", c.grimpeur("12"))
    }

    @Test
    fun `un dossard inconnu rend null, pas une exception`() {
        val c = Catalogue.depuisReponseServeur(reponseServeur)!!
        assertNull(c.grimpeur("999"))
    }

    @Test
    fun `un tag de bloc est reconnu quelle que soit la casse`() {
        // Les QR sont imprimes en majuscules, mais rien ne le garantit.
        val c = Catalogue.depuisReponseServeur(reponseServeur)!!
        assertEquals("ZJ6", c.bloc("ZJ6"))
        assertEquals("ZJ6", c.bloc("zj6"))
    }

    @Test
    fun `les espaces autour d'un scan sont ignores`() {
        val c = Catalogue.depuisReponseServeur(reponseServeur)!!
        assertEquals("Dupont Lea", c.grimpeur(" 12 "))
        assertEquals("ZJ6", c.bloc(" ZJ6 "))
    }

    @Test
    fun `un participant sans dossard est ignore`() {
        // L'inscrit qui n'est pas venu : aucun QR ne le designe, il n'a rien a
        // faire dans un index de scan.
        val c = Catalogue.depuisReponseServeur("""
            {"version":1,
             "participants":[{"id":9,"dossard":null,"nom":"Absent Paul"},
                             {"id":1,"dossard":12,"nom":"Dupont Lea"}],
             "blocs":[]}
        """.trimIndent())!!
        assertEquals(1, c.nombreParticipants)
        assertEquals("Dupont Lea", c.grimpeur("12"))
    }

    @Test
    fun `une entree abimee ne fait pas perdre les autres`() {
        val c = Catalogue.depuisReponseServeur("""
            {"version":1,
             "participants":[{"nom":"sans dossard"},
                             {"dossard":12,"nom":"Dupont Lea"},
                             {"dossard":13}],
             "blocs":[{"tag":"ZJ6"},{"numero":2}]}
        """.trimIndent())!!
        assertEquals(1, c.nombreParticipants)
        assertEquals(1, c.nombreBlocs)
    }

    @Test
    fun `un corps illisible rend null au lieu de lever`() {
        assertNull(Catalogue.depuisReponseServeur("pas du json"))
        assertNull(Catalogue.depuisReponseServeur("[1,2]"))
        assertNull(Catalogue.depuisReponseServeur(""))
    }

    @Test
    fun `un catalogue vide n'est pas une erreur`() {
        val c = Catalogue.depuisReponseServeur("""{"version":3,"participants":[],"blocs":[]}""")!!
        assertTrue(c.estVide)
        assertEquals(3, c.version)
    }

    // --- Le disque ------------------------------------------------------------

    @get:Rule
    val dossierTemporaire = TemporaryFolder()

    @Test
    fun `un catalogue survit a un aller-retour disque`() {
        val f = File(dossierTemporaire.newFolder(), "cat.json")
        val depot = DepotCatalogue(f)
        depot.enregistrer(Catalogue.depuisReponseServeur(reponseServeur)!!)

        val relu = DepotCatalogue(f).charger()

        assertEquals(7, relu.version)
        assertEquals("Dupont Lea", relu.grimpeur("12"))
        assertEquals("ZJ6", relu.bloc("ZJ6"))
    }

    @Test
    fun `un fichier abime donne un catalogue vide, pas un plantage`() {
        // Il sera simplement retelecharge. L'application doit demarrer.
        val f = File(dossierTemporaire.newFolder(), "cat.json")
        f.writeText("n'importe quoi")
        assertTrue(DepotCatalogue(f).charger().estVide)
    }

    @Test
    fun `un fichier absent donne un catalogue vide`() {
        val f = File(dossierTemporaire.newFolder(), "jamais-ecrit.json")
        assertTrue(DepotCatalogue(f).charger().estVide)
    }

    @Test
    fun `aucun fichier temporaire ne traine apres enregistrement`() {
        val dossier = dossierTemporaire.newFolder()
        val f = File(dossier, "cat.json")
        DepotCatalogue(f).enregistrer(Catalogue.depuisReponseServeur(reponseServeur)!!)
        assertFalse(File(dossier, "cat.json.tmp").exists())
    }

    // --- Quand faut-il rafraîchir ? ------------------------------------------

    private fun depotAvecCatalogue(): DepotCatalogue {
        val d = DepotCatalogue(File(dossierTemporaire.newFolder(), "cat.json"))
        d.enregistrer(Catalogue.depuisReponseServeur(reponseServeur)!!)   // version 7
        return d
    }

    /** Le plancher écoulé : on teste les DÉCLENCHEURS, pas le rythme. */
    private val APRES_LE_PLANCHER = DepotCatalogue.PLANCHER_MS

    @Test
    fun `un catalogue vide declenche un rafraichissement`() {
        val d = DepotCatalogue(File(dossierTemporaire.newFolder(), "cat.json"))
        d.charger()
        assertTrue(d.doitRafraichir(maintenantMs = APRES_LE_PLANCHER,
                                    finDuDernierEssaiMs = 0))
    }

    @Test
    fun `un catalogue vide ne vaut PAS une requete par seconde`() {
        // ⚠️ Le defaut le plus couteux de cette classe, et il ne demandait
        // aucune panne : une competition creee le matin n'a pas encore ses
        // participants. Le telechargement REUSSIT et rend un catalogue vide,
        // donc la condition restait vraie -- vingt-cinq telephones tapaient une
        // fois par seconde sur un serveur en parfaite sante.
        val d = DepotCatalogue(File(dossierTemporaire.newFolder(), "cat.json"))
        d.charger()
        assertFalse("une seconde apres, on n'y retourne pas",
            d.doitRafraichir(maintenantMs = 1_000, finDuDernierEssaiMs = 0))
        assertTrue("passe le plancher, oui",
            d.doitRafraichir(maintenantMs = DepotCatalogue.PLANCHER_MS,
                             finDuDernierEssaiMs = 0))
    }

    @Test
    fun `rien a faire si tout est a jour`() {
        val d = depotAvecCatalogue()
        assertFalse(d.doitRafraichir(versionServeur = 7,
                                     maintenantMs = APRES_LE_PLANCHER,
                                     finDuDernierEssaiMs = 0))
    }

    @Test
    fun `une version differente cote serveur declenche un rafraichissement`() {
        // Elle voyage GRATUITEMENT dans la reponse de chaque lot : c'est ce qui
        // fait voir un participant ajoute a 14 h en quelques secondes.
        val d = depotAvecCatalogue()
        assertTrue(d.doitRafraichir(versionServeur = 8,
                                    maintenantMs = APRES_LE_PLANCHER,
                                    finDuDernierEssaiMs = 0))
    }

    @Test
    fun `un qr inconnu declenche un rafraichissement`() {
        val d = depotAvecCatalogue()
        d.signalerQrInconnu()
        assertTrue(d.doitRafraichir(versionServeur = 7,
                                    maintenantMs = APRES_LE_PLANCHER,
                                    finDuDernierEssaiMs = 0))
    }

    @Test
    fun `un qr inconnu scanne PENDANT un telechargement n'est pas perdu`() {
        // Le telechargement part, un QR inconnu arrive avant qu'il revienne.
        // Avec un simple booleen remis a false a la fin, ce signal-la serait
        // efface sans avoir servi, et le participant inscrit dix minutes plus
        // tot attendrait le filet des cinq minutes.
        val d = depotAvecCatalogue()
        d.signalerQrInconnu()
        val vus = d.qrInconnusVus()          // ce que le telechargement va couvrir
        d.signalerQrInconnu()                // arrive pendant qu'il est en vol
        d.noterSucces(vus)
        assertTrue("le second QR doit encore reclamer un rafraichissement",
            d.doitRafraichir(versionServeur = 7,
                             maintenantMs = APRES_LE_PLANCHER,
                             finDuDernierEssaiMs = 0))
    }

    @Test
    fun `un qr inconnu servi ne redeclenche plus rien`() {
        val d = depotAvecCatalogue()
        d.signalerQrInconnu()
        d.noterSucces(d.qrInconnusVus())
        assertFalse(d.doitRafraichir(versionServeur = 7,
                                     maintenantMs = APRES_LE_PLANCHER,
                                     finDuDernierEssaiMs = 0))
    }

    @Test
    fun `le filet periodique finit par declencher`() {
        val d = depotAvecCatalogue()
        assertFalse("juste avant l'echeance",
            d.doitRafraichir(maintenantMs = DepotCatalogue.PERIODE_MS - 1,
                             finDuDernierEssaiMs = 0))
        assertTrue("a l'echeance",
            d.doitRafraichir(maintenantMs = DepotCatalogue.PERIODE_MS,
                             finDuDernierEssaiMs = 0))
    }

    @Test
    fun `une version serveur inconnue ne declenche rien a elle seule`() {
        // Tant qu'on n'a pas parle au serveur, on n'invente pas de retard.
        val d = depotAvecCatalogue()
        assertFalse(d.doitRafraichir(versionServeur = null,
                                     maintenantMs = APRES_LE_PLANCHER,
                                     finDuDernierEssaiMs = 0))
    }

    // --- Le retrait apres echec ---------------------------------------------

    @Test
    fun `apres des echecs, on espace les tentatives`() {
        val d = DepotCatalogue(File(dossierTemporaire.newFolder(), "cat.json"))
        d.charger()                                   // vide : le cas le plus pressant
        repeat(3) { d.noterEchec() }                  // retrait = 8 s
        assertEquals(3, d.echecsConsecutifs)
        assertFalse("le plancher ne suffit plus",
            d.doitRafraichir(maintenantMs = DepotCatalogue.PLANCHER_MS,
                             finDuDernierEssaiMs = 0))
        assertTrue("passe le retrait, oui",
            d.doitRafraichir(maintenantMs = PolitiqueEnvoi.attenteApresEchec(3),
                             finDuDernierEssaiMs = 0))
    }

    @Test
    fun `un succes remet le retrait a zero`() {
        val d = depotAvecCatalogue()
        repeat(5) { d.noterEchec() }
        d.noterSucces(d.qrInconnusVus())
        assertEquals(0, d.echecsConsecutifs)
        assertTrue("le reseau est revenu : on repart au rythme normal",
            d.doitRafraichir(versionServeur = 8,
                             maintenantMs = DepotCatalogue.PLANCHER_MS,
                             finDuDernierEssaiMs = 0))
    }

    @Test
    fun `le retrait est plafonne, un telephone ne decroche jamais`() {
        val d = DepotCatalogue(File(dossierTemporaire.newFolder(), "cat.json"))
        d.charger()
        repeat(50) { d.noterEchec() }
        assertTrue("apres le plafond, on retente",
            d.doitRafraichir(maintenantMs = PolitiqueEnvoi.RETRAIT_MAX_MS,
                             finDuDernierEssaiMs = 0))
    }

    // --- La couleur du circuit (refonte visuelle) ---------------------------
    //
    // L'ecran prend la couleur du bloc scanne. Ce n'est pas de la decoration :
    // un juge verifie d'un coup d'oeil qu'il est sur le bon circuit, ce que le
    // tag seul ne dit pas a quelqu'un qui ne connait pas la convention par
    // coeur.

    @Test
    fun `la couleur du bloc est lue depuis le serveur`() {
        val corps = """
            {"version": 1, "participants": [],
             "blocs": [{"tag": "ZJ1", "couleur": "Jaune"},
                       {"tag": "ZV3", "couleur": "Vert"}]}
        """.trimIndent()

        val catalogue = Catalogue.depuisReponseServeur(corps)!!

        assertEquals("Jaune", catalogue.couleurDuBloc("ZJ1"))
        assertEquals("Vert", catalogue.couleurDuBloc("zv3"))
    }

    @Test
    fun `un bloc sans couleur reste parfaitement utilisable`() {
        val corps = """{"version": 1, "participants": [], "blocs": [{"tag": "ZJ1"}]}"""

        val catalogue = Catalogue.depuisReponseServeur(corps)!!

        assertEquals("le bloc existe toujours", "ZJ1", catalogue.bloc("ZJ1"))
        assertNull(catalogue.couleurDuBloc("ZJ1"))
    }

    @Test
    fun `la couleur survit a l'aller-retour par le disque`() {
        val corps = """
            {"version": 4, "participants": [{"dossard": 1, "nom": "Dupont"}],
             "blocs": [{"tag": "ZM9", "couleur": "Mauve"}]}
        """.trimIndent()
        val fichier = File(dossierTemporaire.newFolder(), "catalogue.json")
        fichier.writeText(Catalogue.depuisReponseServeur(corps)!!.versJson())

        val relu = Catalogue.depuisDisque(fichier)!!

        assertEquals("Mauve", relu.couleurDuBloc("ZM9"))
        assertEquals(4, relu.version)
    }

    /**
     * ⚠️ Les couleurs sont arrivees APRES. Un telephone qui prend la mise a
     * jour garde un catalogue range par l'ancienne version, sans le champ. Sans
     * lecture tolerante, la relecture echouait et le catalogue entier etait
     * jete -- donc chaque scan repassait par le reseau, en silence.
     */
    @Test
    fun `un catalogue range AVANT les couleurs reste lisible`() {
        val fichier = File(dossierTemporaire.newFolder(), "ancien.json")
        fichier.writeText(
            """{"version": 2, "participants": {"1": "Dupont"}, "blocs": {"ZJ1": "ZJ1"}}"""
        )

        val relu = Catalogue.depuisDisque(fichier)

        assertNotNull("un catalogue sans couleurs ne doit pas etre jete", relu)
        assertEquals("Dupont", relu!!.grimpeur("1"))
        assertNull(relu.couleurDuBloc("ZJ1"))
    }
}

/**
 * Le parseur contre la **vraie** réponse du serveur.
 *
 * La fixture est le catalogue servi par la VM 110 le 28/08, copié tel quel. Elle
 * porte des noms **fictifs** — c'est le jeu de développement, pas une vraie
 * compétition.
 *
 * Un test écrit à la main vérifie ce que j'ai imaginé du format ; celui-ci
 * vérifie le format qui existe. C'est ce couple qui protège du jour où le
 * serveur renommera un champ.
 */
class CatalogueContratReelTest {

    private fun catalogueDeLaVm(): String =
        javaClass.classLoader!!.getResourceAsStream("catalogue-vm.json")!!
            .bufferedReader().readText()

    @Test
    fun `le catalogue reel de la vm est lu entierement`() {
        val c = Catalogue.depuisReponseServeur(catalogueDeLaVm())!!

        assertEquals("99 participants dans le jeu de dev", 99, c.nombreParticipants)
        assertEquals("67 blocs", 67, c.nombreBlocs)
        assertTrue("la version doit etre lue", c.version >= 1)
    }

    @Test
    fun `un dossard et un tag du jeu reel sont resolus`() {
        val c = Catalogue.depuisReponseServeur(catalogueDeLaVm())!!
        assertNotNull("le dossard 1 doit exister", c.grimpeur("1"))
        assertNotNull("le bloc ZJ1 doit exister", c.bloc("ZJ1"))
        assertNull("un dossard hors jeu doit rester inconnu", c.grimpeur("99999"))
    }

    @Test
    fun `le catalogue reel tient dans une taille raisonnable une fois indexe`() {
        // Ce qui voyage sur le reseau. Le chiffre de la spec 003 est ~6-8 ko
        // compresses ; ici on mesure le brut, pour qu'une derive se voie.
        val brut = catalogueDeLaVm().length
        assertTrue("catalogue brut de $brut octets", brut < 40_000)
    }

}
