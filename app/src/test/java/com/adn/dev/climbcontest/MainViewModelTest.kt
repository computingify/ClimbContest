package com.adn.dev.climbcontest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La machine à états de l'écran du juge.
 *
 * Aucune coroutine, aucun Android : les `StateFlow` sont écrits de façon
 * synchrone, donc tout se lit avec `.value`. C'est voulu — cette classe est le
 * SEUL endroit où l'état de l'écran vit, et il doit être observable tel quel.
 */
class MainViewModelTest {

    private fun vm() = MainViewModel()

    @Test
    fun `reset complet efface le grimpeur ET le bloc`() {
        val vm = vm()
        vm.setClimberId("42"); vm.setClimberName("Camille")
        vm.setBlocId("ZJ1"); vm.setBlocName("ZJ1"); vm.setBlocCouleur("Jaune")

        vm.reset()

        assertNull(vm.climberId.value)
        assertNull(vm.climberName.value)
        assertNull(vm.blocId.value)
        assertNull(vm.blocName.value)
        assertNull(vm.blocCouleur.value)
    }

    @Test
    fun `reset partiel garde le grimpeur pour enchainer ses blocs`() {
        // Le reglage « garder le grimpeur entre deux blocs » : seul le bloc
        // est remis a zero apres un envoi.
        val vm = vm()
        vm.setClimberId("42"); vm.setClimberName("Camille")
        vm.setBlocId("ZJ1"); vm.setBlocCouleur("Jaune")

        vm.reset(all = false)

        assertEquals("42", vm.climberId.value)
        assertEquals("Camille", vm.climberName.value)
        assertNull(vm.blocId.value)
        assertNull(vm.blocCouleur.value)
    }

    @Test
    fun `le journal garde les cinq dernieres, la plus recente en tete`() {
        val vm = vm()
        repeat(7) { n ->
            vm.ajouterAuJournal(Validation("G$n", "B$n", "10:0$n"))
        }
        val journal = vm.historique.value
        assertEquals(5, journal.size)
        assertEquals("G6", journal.first().grimpeur)   // la derniere ecrite
        assertEquals("G2", journal.last().grimpeur)    // la plus ancienne gardee
    }

    @Test
    fun `le compteur de validations ne fait que monter`() {
        // C'est un SIGNAL, pas un affichage : l'ecran compare ce qu'il a deja
        // fete a ce que le compteur dit. Une rotation ne doit pas le rejouer.
        val vm = vm()
        assertEquals(0, vm.validations.value)
        vm.signalerValidation()
        vm.signalerValidation()
        assertEquals(2, vm.validations.value)
    }

    @Test
    fun `l'envoi en cours est un drapeau, pas un compteur`() {
        val vm = vm()
        assertEquals(false, vm.envoiEnCours.value)
        vm.setEnvoiEnCours(true)
        assertEquals(true, vm.envoiEnCours.value)
        vm.setEnvoiEnCours(false)
        assertEquals(false, vm.envoiEnCours.value)
    }

    @Test
    fun `le voyant a trois etats et null est celui du doute`() {
        val vm = vm()
        assertNull(vm.serveurJoignable.value)          // au lancement : on verifie
        vm.setServeurJoignable(true)
        assertEquals(true, vm.serveurJoignable.value)
        vm.setServeurEnVerification()                  // retour au premier plan
        assertNull(vm.serveurJoignable.value)
    }
}
