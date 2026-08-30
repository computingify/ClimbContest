package com.adn.dev.climbcontest.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dans cette application la couleur PORTE DE L'INFORMATION : un juge lit la
 * couleur d'une carte pour vérifier qu'il est sur le bon circuit. Le mapping
 * nom → couleur et le choix de l'encre sont donc du métier, pas du décor —
 * et ils se testent comme du métier.
 */
class CouleursTest {

    @Test
    fun `les six circuits du club sont reconnus`() {
        assertEquals(Jaune, couleurDeCircuit("Jaune"))
        assertEquals(Vert, couleurDeCircuit("Vert"))
        assertEquals(Bleu, couleurDeCircuit("Bleu"))
        assertEquals(Mauve, couleurDeCircuit("Mauve"))
        assertEquals(Rouge, couleurDeCircuit("Rouge"))
        assertEquals(NoirCircuit, couleurDeCircuit("Noir"))
    }

    @Test
    fun `la casse et les espaces du classeur ne changent rien`() {
        // Le classeur d'origine ecrit parfois « mauve », parfois « Mauve »,
        // parfois avec une espace de fin.
        assertEquals(Mauve, couleurDeCircuit("mauve"))
        assertEquals(Jaune, couleurDeCircuit("  JAUNE "))
        assertEquals(Vert, couleurDeCircuit("vert"))
    }

    @Test
    fun `violet est un synonyme de mauve`() {
        assertEquals(Mauve, couleurDeCircuit("violet"))
    }

    @Test
    fun `un circuit inconnu rend null, jamais une erreur`() {
        // Un circuit dont on ne connait pas la couleur ne doit pas empecher
        // de valider une reussite : l'ecran reste sur sa teinte neutre.
        assertNull(couleurDeCircuit("turquoise"))
        assertNull(couleurDeCircuit(""))
        assertNull(couleurDeCircuit(null))
    }

    @Test
    fun `le circuit noir n'est PAS rendu en noir`() {
        // Un aplat noir sur un fond presque noir ne se voit pas : le juge ne
        // saurait pas s'il a scanne. « Noir » est rendu en craie.
        val craie = couleurDeCircuit("Noir")!!
        val luminance = 0.2126f * craie.red + 0.7152f * craie.green + 0.0722f * craie.blue
        assertTrue("le noir doit etre rendu clair (craie)", luminance > 0.5f)
    }

    @Test
    fun `l'encre est sombre sur les fonds clairs, claire sur les fonds sombres`() {
        // ~8 % des hommes distinguent mal certaines couleurs : le CONTRASTE
        // doit tenir quelle que soit la teinte du circuit.
        val encreSurJaune = encreSur(Jaune)
        val encreSurMauve = encreSur(Mauve)
        assertTrue("encre sombre attendue sur jaune",
                   encreSurJaune.red < 0.2f && encreSurJaune.green < 0.2f)
        assertTrue("encre claire attendue sur mauve",
                   encreSurMauve.red > 0.8f && encreSurMauve.green > 0.8f)
    }

    @Test
    fun `chaque circuit reste lisible avec son encre`() {
        // La regle generale, pour le jour ou le club ajoute une couleur : le
        // contraste luminance(fond) vs luminance(encre) doit etre franc.
        for (fond in CIRCUITS) {
            val encre = encreSur(fond)
            val lFond = 0.2126f * fond.red + 0.7152f * fond.green + 0.0722f * fond.blue
            val lEncre = 0.2126f * encre.red + 0.7152f * encre.green + 0.0722f * encre.blue
            assertTrue("contraste insuffisant pour $fond",
                       kotlin.math.abs(lFond - lEncre) > 0.4f)
        }
    }
}
