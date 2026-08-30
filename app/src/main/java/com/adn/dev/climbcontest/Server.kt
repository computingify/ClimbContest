package com.adn.dev.climbcontest

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
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
    private val historique = HistoriqueScans(File(dossierDonnees, "reussites"))
    private val depotIdentite = DepotIdentite(File(dossierDonnees, DepotIdentite.FICHIER))
    private val depotCatalogue = DepotCatalogue(File(dossierDonnees, DepotCatalogue.FICHIER))
    private val expediteur = Expediteur(file, api) { depotIdentite.courante() }

    @Volatile private var dernierEnvoiMs = 0L
    @Volatile private var dernierRafraichissementMs = 0L
    @Volatile private var dernierContactMs = 0L
    @Volatile private var versionServeurConnue: Int? = null

    init {
        depotCatalogue.charger()
        mainViewModel.setEnAttente(file.nombreEnAttente())
        mainViewModel.setRefusees(file.nombreRefusees())
        // Au demarrage, une fois : ce qui a plus de trente jours s'en va. Ne
        // touche jamais a `file.jsonl`, donc ne peut pas perdre une reussite.
        historique.purger()
    }

    /** Le journal de tous les scans, pour l'ecran qui les liste. */
    fun historiqueDesScans(): HistoriqueScans = historique

    /** Qui est ce telephone. Affiche et modifiable dans les reglages. */
    fun identite(): DepotIdentite = depotIdentite

    /**
     * Le catalogue courant, pour afficher un nom en face d'un dossard.
     *
     * L'ecran des scans ne stocke aucun nom : il les retrouve ici. Un scan
     * d'une competition passee n'en a donc plus, et montre son dossard.
     */
    fun catalogue(): Catalogue = depotCatalogue.courant()

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
            afficher(scanType, libelleLocal, catalogue.couleurDuBloc(scannedValue))
            return MessageScan.ACCEPTE
        }

        // Inconnu localement : repli réseau, et on note qu'on a du retard.
        val resultat = when (scanType) {
            "climber" -> api.verifierGrimpeur(scannedValue)
            "bloc" -> api.verifierBloc(scannedValue)
            else -> return MessageScan.REFUSE
        }
        if (resultat is ApiResult.Succes && resultat.libelle.isNotEmpty()) {
            // Le catalogue vient d'etre rafraichi juste apres : la couleur
            // arrivera au prochain scan. Un bloc sans couleur reste utilisable,
            // l'ecran garde simplement sa teinte neutre.
            afficher(scanType, resultat.libelle, depotCatalogue.courant()
                .couleurDuBloc(scannedValue))
        }
        if (resultat is ApiResult.Echec) println("ClimbContest: ${resultat.message}")

        // Un QR inconnu est le signal le plus direct qu'on a du retard.
        rafraichirCatalogue()

        return DecisionEnvoi.apresScan(resultat)
    }

    private fun afficher(scanType: String, libelle: String, couleur: String? = null) {
        when (scanType) {
            // Cote grimpeur, c'est son NOM : ce que le juge lit pour confirmer
            // qu'il a scanne la bonne personne.
            "climber" -> mainViewModel.setClimberName(libelle)
            "bloc" -> {
                mainViewModel.setBlocName(libelle)
                mainViewModel.setBlocCouleur(couleur)
            }
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
                    val reussite = ReussiteEnAttente(
                        ref = UUID.randomUUID().toString(),
                        dossard = dossard!!, bloc = bloc!!,
                        scanneLe = horodatage(),
                    )
                    file.ajouter(reussite)
                    // L'ordre compte : la file d'abord. Elle porte la reussite ;
                    // le journal n'en garde qu'une trace. Si l'ecriture du
                    // journal echouait, on perdrait une ligne d'historique, pas
                    // une reussite.
                    historique.noter(reussite)
                    // Au journal de l'ecran, pour que le juge puisse relire ce
                    // qu'il vient de faire. C'est la reponse a « est-ce que j'ai
                    // bien envoye ? », qui n'en avait aucune.
                    mainViewModel.ajouterAuJournal(Validation(
                        grimpeur = mainViewModel.climberName.value ?: "dossard $dossard",
                        bloc = mainViewModel.blocName.value ?: bloc!!,
                        heure = heureCourte(),
                        couleur = mainViewModel.blocCouleur.value,
                    ))
                    // L'ecran joue sa confirmation. Le toast « Valide » qui
                    // faisait ce travail est retire : deux confirmations pour
                    // un seul geste, dont une qui recouvre le bouton pendant
                    // deux secondes -- exactement la ou le pouce va ensuite.
                    mainViewModel.signalerValidation()
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
                // Un succes ne fait plus de toast : l'ecran le montre.
                if (message != MessageJuge.VALIDE) toast(texteDe(message))
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

    /**
     * Remet les réussites refusées dans la file, et tente de les renvoyer.
     *
     * Le geste du juge une fois qu'un organisateur a ajouté le participant
     * manquant. Sans lui, ces réussites seraient perdues — et c'est le cas le
     * plus fréquent de refus, pas un cas rare.
     */
    fun renvoyerLesRefusees(portee: CoroutineScope) {
        portee.launch(Dispatchers.IO) {
            val nombre = expediteur.renvoyerLesRefusees(historique::reprendre)
            mainViewModel.setEnAttente(file.nombreEnAttente())
            mainViewModel.setRefusees(file.nombreRefusees())
            withContext(Dispatchers.Main) {
                toast(if (nombre == 0) R.string.aucun_refus else R.string.refus_renvoyes)
            }
            envoyer(forcer = true)
            mainViewModel.setEnAttente(file.nombreEnAttente())
            mainViewModel.setRefusees(file.nombreRefusees())
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

    /**
     * Le voyant de connexion, **tant que l'ecran est au premier plan**.
     *
     * Le principe est celui de sowel : une petite icone de connexion sans fil,
     * verte quand la liaison est la, rouge sinon ; elle passe tranquillement en
     * hors ligne quand l'application n'est plus au premier plan, et un controle
     * est refait des qu'on y revient.
     *
     * Pourquoi ne pas simplement sonder en permanence : en arriere-plan,
     * personne ne regarde le voyant. Continuer a interroger le serveur toutes
     * les trente secondes couterait de la batterie et du reseau pour une
     * information que nul ne lit -- et Android finirait de toute facon par
     * geler la boucle, ce qui donnerait un voyant fige au lieu d'un voyant
     * honnete.
     *
     * Ce qu'il ne faut surtout pas, c'est afficher a la reprise l'etat d'avant
     * la mise en veille. D'ou la sequence : « je verifie » d'abord, un
     * aller-retour immediat ensuite, et le rythme de croisiere apres.
     *
     * Le cout en croisiere est negligeable : le sondage est un `304` du
     * catalogue, ~150 octets, toutes les [PERIODE_PRESENCE_MS] et par
     * telephone -- moins d'une requete par seconde pour les vingt-cinq
     * telephones d'une competition, a comparer aux soixante telephones de
     * spectateurs qui rafraichissent le classement toutes les quinze secondes.
     * Et il est saute des qu'autre chose vient de parler : un juge qui scanne
     * en continu ne genere rien de plus.
     *
     * A appeler sous `repeatOnLifecycle(RESUMED)` : la fonction ne rend la main
     * que lorsqu'on l'annule.
     */
    suspend fun suivreLaPresence() {
        try {
            mainViewModel.setServeurEnVerification()
            verifierPresence()
            while (currentCoroutineContext().isActive) {
                delay(1_000)
                verifierPresenceSiNecessaire()
            }
        } finally {
            // On quitte le premier plan. Plus personne ne verifie, donc plus
            // rien a affirmer : hors ligne, sans bruit.
            mainViewModel.setServeurJoignable(false)
        }
    }

    private suspend fun verifierPresenceSiNecessaire() {
        val dernier = maxOf(dernierContactMs, dernierEnvoiMs, dernierRafraichissementMs)
        if (System.currentTimeMillis() - dernier < PERIODE_PRESENCE_MS) return
        verifierPresence()
    }

    /**
     * Le sondage lui-meme : un rafraichissement du catalogue.
     *
     * ⚠️ Il tapait `/health`, et c'etait FAUX sur le terrain. Caddy ferme
     * `/health` a tout ce qui n'est pas le LAN de la maison (spec 001) : le
     * jour de la competition, les telephones des juges sont sur le wifi de la
     * salle, donc du cote Internet. Ils auraient recu `404` a chaque sondage et
     * affiche « Serveur injoignable » en permanence, pendant que tout
     * fonctionnait. Un voyant qui ment est pire que pas de voyant, et
     * celui-la aurait menti tout le temps.
     *
     * Le catalogue, lui, est une route que les juges atteignent forcement --
     * sans quoi l'application ne marcherait pas du tout. Avec `If-None-Match`,
     * c'est un `304` a ~150 octets, et le sondage devient exactement la bonne
     * question : « puis-je encore parler utilement au serveur ? ». Depuis la
     * spec 012, il valide aussi la cle d'API de bout en bout.
     *
     * Effet de bord assume, et bienvenu : le catalogue est desormais rafraichi
     * toutes les trente secondes au premier plan, au lieu des cinq minutes du
     * filet de [DepotCatalogue]. Un participant inscrit dix minutes avant son
     * passage arrive donc dans le telephone du juge en trente secondes.
     *
     * `suivreLaPresence` tourne sous `repeatOnLifecycle`, donc sur le fil
     * principal : l'appel reseau doit explicitement partir ailleurs.
     */
    private suspend fun verifierPresence() {
        dernierContactMs = System.currentTimeMillis()
        withContext(Dispatchers.IO) { rafraichirCatalogue() }
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
        // Ce que le juge doit savoir AVANT que quelque chose echoue : le
        // serveur repond-il ? Il ne l'apprenait qu'en plein geste.
        bilan?.let { mainViewModel.setServeurJoignable(it.aReussi) }
        bilan?.let(::noterAuJournal)
        bilan?.catalogueVersion?.let { versionServeurConnue = it }
        mainViewModel.setEnAttente(file.nombreEnAttente())
        mainViewModel.setRefusees(file.nombreRefusees())
        bilan?.refusees?.forEach { println("ClimbContest: refus serveur — ${it.message}") }
        return bilan
    }

    /** Reporte au journal ce que le serveur vient de dire. La regle est ailleurs. */
    private fun noterAuJournal(bilan: BilanEnvoi) {
        DecisionEnvoi.pourLeJournal(bilan).forEach {
            historique.changerEtat(it.ref, it.etat, it.motif)
        }
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
                mainViewModel.setServeurJoignable(true)
            }
            // 304 : rien n'a bouge. ~150 octets, et c'est le cas le plus frequent.
            is ResultatCatalogue.DejaAJour -> {
                versionServeurConnue = version
                mainViewModel.setServeurJoignable(true)
            }
            // Tout echec eteint le voyant, reseau ou non. Un `401` sur une cle
            // refusee veut dire que rien ne passera : du point de vue du juge,
            // c'est la meme chose qu'un serveur absent, et lui afficher « tout
            // va bien » serait un mensonge. La distinction reste au journal,
            // pour celui qui diagnostique.
            is ResultatCatalogue.Echec -> {
                println("ClimbContest: ${r.message} (reseau=${r.reseau})")
                mainViewModel.setServeurJoignable(false)
            }
        }
    }

    /** L'heure, pour le journal a l'ecran. Le juge lit « 10:42 », pas un ISO. */
    private fun heureCourte(): String =
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE)
            .format(java.util.Date())

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

    companion object {
        /**
         * Trente secondes : le voyant peut mentir au plus une demi-minute.
         *
         * Assez court pour qu'un juge s'en apercoive avant d'avoir scanne dix
         * grimpeurs, assez long pour ne rien peser sur le reseau de la salle.
         */
        const val PERIODE_PRESENCE_MS = 30_000L
    }
}
