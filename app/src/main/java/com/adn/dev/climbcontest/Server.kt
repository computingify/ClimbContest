package com.adn.dev.climbcontest

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Ce qui relie l'interface au reste : ViewModel, toasts, coroutines.
 *
 * Depuis la spec 003, cette classe n'appelle plus le serveur pour valider un
 * scan. Elle consulte le **catalogue local** — une table de hachage, ~100 ns au
 * lieu de ~200 ms — et dépose les réussites dans une **file sur le disque**.
 * L'envoi part en arrière-plan, par lots.
 *
 * Le juge ne dépend donc plus du réseau pour travailler. Le réseau redevient ce
 * qu'il aurait toujours dû être : un détail d'acheminement.
 *
 * Tout ce qui décide vit ailleurs et est testé sur la JVM : [DecisionEnvoi],
 * [Catalogue], [FileDeReussites], [PolitiqueEnvoi], [Expediteur]. Ici, on
 * orchestre.
 */
class Server(
    private val mainViewModel: MainViewModel,
    private val context: Context,
    private val api: ClimbContestApi = ClimbContestApi(),
    dossierDonnees: File = context.filesDir,
) {

    private val file = FileDeReussites(File(dossierDonnees, "reussites"))
    private val depotCatalogue = DepotCatalogue(File(dossierDonnees, DepotCatalogue.FICHIER))
    private val expediteur = Expediteur(file, api)

    @Volatile private var dernierEnvoiMs = 0L
    @Volatile private var dernierRafraichissementMs = 0L
    @Volatile private var versionServeurConnue: Int? = null

    init {
        depotCatalogue.charger()
        mainViewModel.setEnAttente(file.nombreEnAttente())
    }

    /**
     * Démarre la boucle de fond : envoi par lots et rafraîchissement du catalogue.
     *
     * Volontairement une boucle simple plutôt qu'un `WorkManager` : elle ne doit
     * vivre que pendant que le juge tient son téléphone, et on veut pouvoir la
     * suivre dans le journal sans outillage.
     */
    fun demarrerBoucleDeFond(portee: CoroutineScope) {
        portee.launch(Dispatchers.IO) {
            rafraichirCatalogue()
            while (isActive) {
                delay(1_000)
                envoyerSiNecessaire()
                rafraichirSiNecessaire()
            }
        }
    }

    /**
     * Vérifie un QR fraîchement scanné, **sans réseau quand c'est possible**.
     *
     * Un QR absent du catalogue local n'est pas refusé : on demande au serveur,
     * et on rafraîchit le catalogue. C'est le cas du participant inscrit dix
     * minutes plus tôt — et le juge n'a rien à faire pour que ça marche.
     */
    fun checkOnServer(scanType: String, scannedValue: String): MessageScan {
        val catalogue = depotCatalogue.courant()
        val libelleLocal = when (scanType) {
            "climber" -> catalogue.grimpeur(scannedValue)
            "bloc" -> catalogue.bloc(scannedValue)
            else -> return MessageScan.REFUSE
        }

        if (libelleLocal != null) {
            afficher(scanType, libelleLocal)
            return MessageScan.ACCEPTE
        }

        // Inconnu localement : repli réseau, et on note qu'on a du retard.
        val resultat = when (scanType) {
            "climber" -> api.verifierGrimpeur(scannedValue)
            "bloc" -> api.verifierBloc(scannedValue)
            else -> return MessageScan.REFUSE
        }
        if (resultat is ApiResult.Succes && resultat.libelle.isNotEmpty()) {
            afficher(scanType, resultat.libelle)
        }
        if (resultat is ApiResult.Echec) println("ClimbContest: ${resultat.message}")

        // Un QR inconnu est le signal le plus direct qu'on a du retard.
        rafraichirCatalogue()

        return DecisionEnvoi.apresScan(resultat)
    }

    private fun afficher(scanType: String, libelle: String) {
        when (scanType) {
            // Cote grimpeur, c'est son NOM : ce que le juge lit pour confirmer
            // qu'il a scanne la bonne personne.
            "climber" -> mainViewModel.setClimberName(libelle)
            "bloc" -> mainViewModel.setBlocName(libelle)
        }
    }

    /**
     * Dépose la réussite dans la file, puis rend la main **immédiatement**.
     *
     * « Validé » s'affiche quand la réussite est sur le disque du téléphone, pas
     * quand elle est sur celui de la VM. C'est tout l'objet de la spec 003 : le
     * juge n'attend plus le réseau.
     */
    fun submit() {
        CoroutineScope(Dispatchers.IO).launch {
            val dossard = mainViewModel.climberId.value
            val bloc = mainViewModel.blocId.value

            val message = DecisionEnvoi.avantEnvoi(dossard, bloc) ?: run {
                try {
                    file.ajouter(ReussiteEnAttente(
                        ref = UUID.randomUUID().toString(),
                        dossard = dossard!!, bloc = bloc!!,
                        scanneLe = horodatage(),
                    ))
                    MessageJuge.VALIDE
                } catch (e: Exception) {
                    // Disque plein, dossier inaccessible. On ne dit surtout pas
                    // « Validé » : ce serait mentir au juge.
                    println("ClimbContest: ecriture impossible — ${e.message}")
                    MessageJuge.ERREUR_RESEAU
                }
            }

            mainViewModel.setEnAttente(file.nombreEnAttente())

            withContext(Dispatchers.Main) {
                toast(texteDe(message))
                if (DecisionEnvoi.doitReinitialiser(message)) {
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(500)
                        mainViewModel.reset(!mainViewModel.autoEval)
                    }
                }
            }
            envoyerSiNecessaire()
        }
    }

    /** Le bouton « tout envoyer maintenant », pour la fin de compétition. */
    fun toutEnvoyerMaintenant(portee: CoroutineScope) {
        portee.launch(Dispatchers.IO) {
            val bilan = envoyer(forcer = true)
            withContext(Dispatchers.Main) {
                val restantes = file.nombreEnAttente()
                val texte = when {
                    bilan == null && restantes == 0 -> context.getString(R.string.file_vide)
                    bilan?.aReussi == true && restantes == 0 ->
                        context.getString(R.string.file_envoyee)
                    else -> context.getString(R.string.file_reste, restantes)
                }
                Toast.makeText(context, texte, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun envoyerSiNecessaire(): BilanEnvoi? = envoyer(forcer = false)

    private fun envoyer(forcer: Boolean): BilanEnvoi? {
        val enAttente = file.nombreEnAttente()
        val depuis = System.currentTimeMillis() - dernierEnvoiMs
        if (!PolitiqueEnvoi.doitEnvoyer(enAttente, depuis, expediteur.echecsConsecutifs, forcer)) {
            return null
        }
        dernierEnvoiMs = System.currentTimeMillis()
        val bilan = expediteur.tenter()
        bilan?.catalogueVersion?.let { versionServeurConnue = it }
        mainViewModel.setEnAttente(file.nombreEnAttente())
        bilan?.refusees?.forEach { println("ClimbContest: refus serveur — ${it.message}") }
        return bilan
    }

    private fun rafraichirSiNecessaire() {
        val doit = depotCatalogue.doitRafraichir(
            versionServeur = versionServeurConnue,
            maintenantMs = System.currentTimeMillis(),
            dernierRafraichissementMs = dernierRafraichissementMs,
        )
        if (doit) rafraichirCatalogue()
    }

    private fun rafraichirCatalogue() {
        dernierRafraichissementMs = System.currentTimeMillis()
        val version = depotCatalogue.courant().version.takeIf { it > 0 }
        when (val r = api.telechargerCatalogue(version)) {
            is ResultatCatalogue.Recu -> {
                depotCatalogue.enregistrer(r.catalogue)
                versionServeurConnue = r.catalogue.version
            }
            // 304 : rien n'a bouge. ~150 octets, et c'est le cas le plus frequent.
            is ResultatCatalogue.DejaAJour -> versionServeurConnue = version
            is ResultatCatalogue.Echec -> println("ClimbContest: ${r.message}")
        }
    }

    private fun horodatage(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date())

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
