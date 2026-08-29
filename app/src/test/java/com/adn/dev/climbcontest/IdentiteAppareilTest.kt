package com.adn.dev.climbcontest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * L'identité du téléphone, sur la JVM.
 *
 * Ce qui compte ici tient en une phrase : **l'identifiant ne doit jamais
 * changer tout seul.** S'il changeait à chaque lancement, la page de contrôle
 * afficherait vingt-cinq appareils au lieu d'un, et ne servirait plus à rien.
 */
class IdentiteAppareilTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private lateinit var fichier: File

    @org.junit.Before
    fun preparer() {
        fichier = File(dossier.newFolder("donnees"), DepotIdentite.FICHIER)
    }

    @Test
    fun `le premier lancement cree un identifiant`() {
        val identite = DepotIdentite(fichier).courante()

        assertTrue(identite.id.isNotBlank())
        assertNull(identite.nom)
        assertTrue(fichier.exists())
    }

    @Test
    fun `le lancement suivant retrouve le meme identifiant`() {
        val premier = DepotIdentite(fichier).courante().id

        // Un nouveau depot : c'est ce qui se passe au redemarrage.
        assertEquals(premier, DepotIdentite(fichier).courante().id)
    }

    @Test
    fun `un fichier illisible donne un identifiant neuf, sans exception`() {
        fichier.parentFile?.mkdirs()
        fichier.writeText("{ceci n'est pas du json")

        val identite = DepotIdentite(fichier).courante()

        assertTrue(identite.id.isNotBlank())
    }

    @Test
    fun `un fichier sans identifiant donne un identifiant neuf`() {
        fichier.parentFile?.mkdirs()
        fichier.writeText("""{"nom":"Mur jaune"}""")

        assertTrue(DepotIdentite(fichier).courante().id.isNotBlank())
    }

    @Test
    fun `renommer ne change pas l'identifiant`() {
        val depot = DepotIdentite(fichier)
        val avant = depot.courante().id

        depot.renommer("Mur jaune")

        assertEquals(avant, depot.courante().id)
        assertEquals("Mur jaune", depot.courante().nom)
        // Et apres un redemarrage.
        assertEquals("Mur jaune", DepotIdentite(fichier).courante().nom)
    }

    @Test
    fun `un nom vide ou blanc revient a ne pas en avoir`() {
        val depot = DepotIdentite(fichier)
        depot.renommer("Mur jaune")

        depot.renommer("   ")

        assertNull(depot.courante().nom)
    }

    @Test
    fun `un nom est coupe, jamais refuse`() {
        val depot = DepotIdentite(fichier)

        depot.renommer("x".repeat(200))

        assertEquals(IdentiteAppareil.LONGUEUR_NOM, depot.courante().nom?.length)
    }

    @Test
    fun `deux telephones n'ont pas le meme identifiant`() {
        val autre = File(dossier.newFolder("autre"), DepotIdentite.FICHIER)

        assertNotEquals(
            DepotIdentite(fichier).courante().id,
            DepotIdentite(autre).courante().id,
        )
    }

    @Test
    fun `le json omet le nom quand il n'y en a pas`() {
        val sansNom = IdentiteAppareil("abc", null).versJson()

        assertTrue(sansNom.contains("abc"))
        assertTrue(!sansNom.contains("nom"))
    }
}
