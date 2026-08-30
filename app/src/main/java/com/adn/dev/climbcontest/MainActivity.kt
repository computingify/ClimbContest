package com.adn.dev.climbcontest

import SettingsScreen
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adn.dev.climbcontest.ui.EcranJuge
import com.adn.dev.climbcontest.ui.MenuJuge
import com.adn.dev.climbcontest.ui.theme.ClimbContestTheme
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.adn.dev.climbcontest.ui.theme.Alerte
import com.adn.dev.climbcontest.ui.theme.Attention
import com.adn.dev.climbcontest.ui.theme.CarteFaite
import com.adn.dev.climbcontest.ui.theme.CarteActive
import com.adn.dev.climbcontest.ui.theme.Encre
import com.adn.dev.climbcontest.ui.theme.Encre2
import com.adn.dev.climbcontest.ui.theme.Vert
import com.adn.dev.climbcontest.ui.theme.CarteAttente
import com.adn.dev.climbcontest.ui.theme.TraitActif
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha

// L'adresse du serveur n'est plus une constante ici : elle vient de
// BuildConfig.SERVER_URL, choisie par le type de build et surchargeable
// par -PserverUrl. Voir app/build.gradle.kts.
//
// Le raccourci RUN_ON_EMULATOR a ete retire : c'etait un `const val = 0`, donc
// deux branches mortes qu'il fallait editer puis recompiler pour activer -- et
// une fois activees elles tiraient un dossard dans 1..39 alors que le jeu de
// dev en compte 98, puis forcaient le tag de bloc a "Z1", qui n'existe dans
// aucun jeu de donnees (la convention est zone + couleur + rang : ZJ1, ZV3...).
// Le scan aurait echoue a tous les coups. Pour piloter l'application sans
// camera, on frappe l'API directement : voir docs/tester-avec-l-emulateur.md.

class MainActivity : ComponentActivity() {

    // Define the scanner as a class member
    private lateinit var scanner: GmsBarcodeScanner
    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var server : Server

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // L'ecran est TOUJOURS sombre (voir ui/theme/Theme.kt). Sans ce
        // reglage, `enableEdgeToEdge()` suit le mode clair/sombre du systeme et
        // pose des icones de barre d'etat SOMBRES sur notre barre sombre :
        // l'heure et la batterie devenaient illisibles sur un telephone regle
        // en clair -- c'est-a-dire sur la plupart de ceux des benevoles.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )


        // ⚠️ Ces trois initialisations vivaient dans `AppContent()`, qui est un
        // @Composable : elles repartaient donc a CHAQUE recomposition. Ouvrir
        // puis fermer les reglages construisait un nouveau `Server` et lancait
        // une nouvelle boucle de fond -- l'ancienne, accrochee au
        // `lifecycleScope` et non a la composition, continuant de tourner. Les
        // boucles s'empilaient, et avec elles les envois et les
        // rafraichissements de catalogue.
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        scanner = GmsBarcodeScanning.getClient(this, options)
        installerLesModulesDuScanner()

        server = Server(mainViewModel, this)

        // Envoi par lots et rafraichissement du catalogue. Cette boucle-la vit
        // tant que l'ecran existe, arriere-plan compris : ce qui est dans la
        // file doit partir, que le juge regarde ou non.
        server.demarrerBoucleDeFond(lifecycleScope)

        // Le voyant de connexion, lui, ne vit qu'au premier plan -- le principe
        // de sowel. `repeatOnLifecycle` annule le suivi quand on quitte l'ecran
        // et le relance quand on y revient, ce qui donne exactement la sequence
        // voulue : hors ligne en arriere-plan, « je verifie » au retour, puis la
        // verite apres un aller-retour.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                server.suivreLaPresence()
            }
        }

        setContent {
            ClimbContestTheme {
                AppContent()
            }
        }
    }

    /** Le module ML Kit du scanner n'est pas toujours present sur l'appareil. */
    private fun installerLesModulesDuScanner() {
        val client = ModuleInstall.getClient(this)
        client.areModulesAvailable(scanner).addOnSuccessListener {
            if (!it.areModulesAvailable()) {
                client.installModules(
                    ModuleInstallRequest.newBuilder().addApi(scanner).build()
                )
            }
        }
    }

    /** Les trois ecrans. Une enumeration plutot qu'un booleen : ils sont trois. */
    private enum class Ecran { SAISIE, REGLAGES, SCANS }

    @Composable
    fun AppContent() {
        var ecran by remember { mutableStateOf(Ecran.SAISIE) }

        // D'ou l'on vient quand on ouvre « Mes scans » : le menu de l'ecran de
        // saisie, ou les reglages. Sans cela, le retour ramenait toujours aux
        // reglages -- un juge qui n'y etait jamais alle y atterrissait sans
        // comprendre pourquoi.
        var retourDesScans by remember { mutableStateOf(Ecran.SAISIE) }

        var menuOuvert by remember { mutableStateOf(false) }

        // « Mes scans » s'ouvre filtré quand on y arrive par « N en attente ».
        var scansFiltres by remember { mutableStateOf(false) }

        if (ecran == Ecran.SCANS) {
            // Relu a chaque ouverture de l'ecran, pas conserve en memoire : le
            // journal est un fichier, et il bouge pendant qu'on le regarde.
            val scans = remember(ecran) { server.historiqueDesScans().tous() }
            val catalogue = remember(ecran) { server.catalogue() }
            ScansScreen(
                scans = scans,
                catalogue = catalogue,
                filtreInitial = scansFiltres,
                onBack = { ecran = retourDesScans },
            )
        } else if (ecran == Ecran.REGLAGES) {
            val identite = remember { server.identite().courante() }
            SettingsScreen(
                onBack = { ecran = Ecran.SAISIE },
                mainViewModel,
                this,
                onToutEnvoyer = { server.toutEnvoyerMaintenant(lifecycleScope) },
                onRenvoyerRefusees = { server.renvoyerLesRefusees(lifecycleScope) },
                identite = identite,
                onRenommer = { server.identite().renommer(it) },
                onVoirLesScans = {
                    retourDesScans = Ecran.REGLAGES
                    scansFiltres = false
                    ecran = Ecran.SCANS
                },
            )
        } else {
            // Plus de photo de fond : du texte pose sur une photo donne un
            // contraste imprevisible, different sur chaque telephone. Ici, la
            // couleur porte de l'information -- elle doit etre fiable.
            val dossard by mainViewModel.climberId.collectAsState()
            val grimpeur by mainViewModel.climberName.collectAsState()
            val bloc by mainViewModel.blocName.collectAsState()
            val blocId by mainViewModel.blocId.collectAsState()
            val couleur by mainViewModel.blocCouleur.collectAsState()
            val enAttente by mainViewModel.enAttente.collectAsState()
            val refusees by mainViewModel.refusees.collectAsState()
            val historique by mainViewModel.historique.collectAsState()
            val joignable by mainViewModel.serveurJoignable.collectAsState()
            val validations by mainViewModel.validations.collectAsState()
            val envoiEnCours by mainViewModel.envoiEnCours.collectAsState()

            EcranJuge(
                dossard = dossard,
                grimpeur = grimpeur,
                // `bloc ?: blocId` : le serveur peut accepter un QR sans
                // renvoyer de libelle. La carte passait alors au vert en
                // affichant « A scanner », soit deux informations
                // contradictoires. Le tag brut n'est pas ideal, mais il est vrai.
                bloc = bloc ?: blocId,
                couleurDuBloc = couleur,
                enAttente = enAttente,
                refusees = refusees,
                historique = historique,
                envoiEnCours = envoiEnCours,
                validations = validations,
                serveurJoignable = joignable,
                onScanGrimpeur = { startScanning("climber") },
                onScanBloc = { startScanning("bloc") },
                onEnvoyer = { server.submit() },
                onEffacer = { mainViewModel.reset() },
                onMenu = { menuOuvert = true },
                onToutEnvoyer = { server.toutEnvoyerMaintenant(lifecycleScope) },
            )

            if (menuOuvert) {
                MenuJuge(
                    enAttente = enAttente,
                    refusees = refusees,
                    onFermer = { menuOuvert = false },
                    onMesScans = {
                        retourDesScans = Ecran.SAISIE
                        scansFiltres = false
                        ecran = Ecran.SCANS
                    },
                    onVoirEnAttente = {
                        retourDesScans = Ecran.SAISIE
                        scansFiltres = true
                        ecran = Ecran.SCANS
                    },
                    onReglages = { ecran = Ecran.REGLAGES },
                    onToutEnvoyer = { server.toutEnvoyerMaintenant(lifecycleScope) },
                    onRenvoyerRefusees = { server.renvoyerLesRefusees(lifecycleScope) },
                )
            }
        }

    }

    private fun startScanning(scanType: String) {
        if (("climber" == scanType && null == mainViewModel.climberId.value)
            || ("bloc" == scanType && null == mainViewModel.blocId.value)
        ) {
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val scannedValue = barcode.displayValue ?: "Unknown"
                    handleScannedValue(scanType, scannedValue)
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    Toast.makeText(
                        this,
                        getString(R.string.failed_to_scan, e.message), Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun handleScannedValue(scanType: String, scannedValue: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val localScannedValue = scannedValue
            val verdict = server.checkOnServer(scanType, localScannedValue)
            withContext(Dispatchers.Main) {
                if (DecisionEnvoi.doitRetenirLeScan(verdict)) {
                    when (scanType) {
                        "climber" -> mainViewModel.setClimberId(localScannedValue)
                        "bloc" -> mainViewModel.setBlocId(localScannedValue)
                    }
                }
                // `scanType` vaut « climber » ou « bloc » : des identifiants
                // internes, en anglais, qui partaient tels quels dans le toast
                // du juge (« climber Identifiant 42 valide »).
                val quoi = getString(
                    if (scanType == "climber") R.string.climber else R.string.block
                )
                val texte = when (verdict) {
                    // Rien. La carte passe au vert et affiche le nom : c'est une
                    // confirmation qui RESTE, la ou le toast recouvrait
                    // « Envoyer » deux secondes -- exactement ou le pouce va.
                    MessageScan.ACCEPTE -> null
                    MessageScan.REFUSE ->
                        getString(R.string.id_rejected_please_scan_again,
                                  quoi, localScannedValue)
                    // « Identifiant incorrect, recommencez » sur une simple
                    // coupure reseau envoyait le juge chercher un organisateur
                    // pour un QR parfaitement valide.
                    MessageScan.ERREUR_RESEAU -> getString(R.string.scan_reseau)
                }
                texte?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
