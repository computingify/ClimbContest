package com.adn.dev.climbcontest

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ce qui relie l'interface au backend : ViewModel, toasts, coroutines.
 *
 * Le dialogue HTTP lui-même vit dans [ClimbContestApi], qui ne dépend d'aucune
 * classe Android et est donc couvert par des tests JVM
 * ([ClimbContestApiTest]) — sans émulateur, en quelques secondes.
 *
 * Cette séparation n'est pas cosmétique : elle est ce qui permet de vérifier
 * automatiquement que l'application envoie et lit exactement ce que le backend
 * attend. Le contrat est ce qui casserait le plus silencieusement.
 */
class Server(
    private val mainViewModel: MainViewModel,
    private val context: Context,
    private val api: ClimbContestApi = ClimbContestApi(),
) {

    /**
     * Vérifie un QR code fraîchement scanné.
     *
     * Appelé depuis un contexte d'entrée/sortie : bloquant, volontairement.
     */
    fun checkOnServer(scanType: String, scannedValue: String): Boolean {
        val resultat = when (scanType) {
            "climber" -> api.verifierGrimpeur(scannedValue)
            "bloc" -> api.verifierBloc(scannedValue)
            else -> return false
        }

        return when (resultat) {
            is ApiResult.Succes -> {
                if (resultat.libelle.isNotEmpty()) {
                    when (scanType) {
                        // Cote grimpeur, le serveur renvoie son NOM : c'est ce
                        // que le juge lit a l'ecran pour confirmer qu'il a scanne
                        // la bonne personne.
                        "climber" -> mainViewModel.setClimberName(resultat.libelle)
                        "bloc" -> mainViewModel.setBlocName(resultat.libelle)
                    }
                }
                true
            }
            is ApiResult.Echec -> {
                println("ClimbContest: ${resultat.message}")
                false
            }
        }
    }

    /**
     * Envoie la réussite en cours, puis remet l'écran à zéro si elle est passée.
     *
     * Cette méthode ne décide de rien : elle orchestre. Les décisions — faut-il
     * envoyer, que lit le juge, l'écran doit-il se vider — sont dans
     * [DecisionEnvoi], qui est testé sur la JVM.
     */
    fun submit() {
        CoroutineScope(Dispatchers.IO).launch {
            val dossard = mainViewModel.climberId.value
            val bloc = mainViewModel.blocId.value

            val message = DecisionEnvoi.avantEnvoi(dossard, bloc)
                ?: DecisionEnvoi.apresEnvoi(api.enregistrerReussite(dossard!!, bloc!!))

            withContext(Dispatchers.Main) {
                toast(texteDe(message))
                if (DecisionEnvoi.doitReinitialiser(message)) {
                    // Court delai pour que le juge voie la confirmation avant que
                    // l'ecran ne se vide.
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(500)
                        mainViewModel.reset(!mainViewModel.autoEval)
                    }
                }
            }
        }
    }

    /** Traduction pure du verdict en ressource affichable. Aucune logique ici. */
    private fun texteDe(message: MessageJuge): Int = when (message) {
        MessageJuge.RIEN_A_ENVOYER -> R.string.rien_a_envoyer
        MessageJuge.VALIDE -> R.string.climber_and_bloc_successfully_registered
        MessageJuge.ERREUR_RESEAU -> R.string.network_error
        MessageJuge.ENVOI_REFUSE -> R.string.submit_failed
    }

    private fun toast(res: Int) =
        Toast.makeText(context, context.getString(res), Toast.LENGTH_SHORT).show()
}
