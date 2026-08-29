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
import com.adn.dev.climbcontest.ui.theme.Carte
import com.adn.dev.climbcontest.ui.theme.Carte2
import com.adn.dev.climbcontest.ui.theme.Encre
import com.adn.dev.climbcontest.ui.theme.Encre2
import com.adn.dev.climbcontest.ui.theme.EtatFait
import com.adn.dev.climbcontest.ui.theme.EtatVide
import com.adn.dev.climbcontest.ui.theme.Trait
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

    @Composable
    fun AppContent() {
        var isSettingsScreen by remember { mutableStateOf(false) }

        if (isSettingsScreen) {
            SettingsScreen(
                onBack = { isSettingsScreen = false },
                mainViewModel,
                this,
                onToutEnvoyer = { server.toutEnvoyerMaintenant(lifecycleScope) },
                onRenvoyerRefusees = { server.renvoyerLesRefusees(lifecycleScope) },
            )
        } else {
            // Plus de photo de fond : du texte pose sur une photo donne un
            // contraste imprevisible, different sur chaque telephone. Ici, la
            // couleur porte de l'information -- elle doit etre fiable.
            MainScreen(
                viewModel = mainViewModel,
                onScanClimber = { startScanning("climber") },
                onScanBloc = { startScanning("bloc") },
                onSubmit = { server.submit() },
                onReset = { mainViewModel.reset() },
                onOpenSettings = { isSettingsScreen = true }
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel,
               onScanClimber: () -> Unit,
               onScanBloc: () -> Unit,
               onSubmit: () -> Unit,
               onReset: () -> Unit,
               onOpenSettings: () -> Unit) {

    val climberId by viewModel.climberId.collectAsState()
    val climberName by viewModel.climberName.collectAsState()
    val blocId by viewModel.blocId.collectAsState()
    val blocName by viewModel.blocName.collectAsState()
    val enAttente by viewModel.enAttente.collectAsState()
    val refusees by viewModel.refusees.collectAsState()
    val historique by viewModel.historique.collectAsState()
    val serveurJoignable by viewModel.serveurJoignable.collectAsState()

    val pretAEnvoyer = climberId != null && blocId != null
    var confirmerReset by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Barre(
            serveurJoignable = serveurJoignable,
            onOpenSettings = onOpenSettings,
        )

        // La file ne s'affiche que quand elle a quelque chose a dire. Elle
        // etait dans la barre, en pastilles, ou elle recouvrait « Serveur
        // joignable » des qu'il y avait deux compteurs -- exactement le jour ou
        // les deux comptent.
        if (enAttente > 0 || refusees > 0) {
            BandeFile(enAttente, refusees, onOpenSettings)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(12.dp))

            // Les deux scans. Le libelle porte ce qui a ete scanne : avant, le
            // nom s'affichait dans une boite separee sous le bouton, ce qui
            // faisait deux endroits a regarder pour une seule information.
            //
            // `nom ?: id` : le nom peut manquer -- un serveur qui accepte le QR
            // sans renvoyer de libelle. La carte passait alors au vert tout en
            // affichant « A scanner », soit deux informations contradictoires.
            // Le dossard brut n'est pas ideal, mais il est vrai.
            BoutonScan(
                titre = stringResource(R.string.climber),
                valeur = climberName ?: climberId,
                complement = if (climberName != null)
                                 climberId?.let { "n\u00b0$it" } else null,
                fait = climberId != null,
                onClick = onScanClimber,
            )

            Spacer(Modifier.height(14.dp))

            BoutonScan(
                titre = stringResource(R.string.block),
                valeur = blocName ?: blocId,
                complement = null,
                fait = blocId != null,
                onClick = onScanBloc,
            )

            Spacer(Modifier.height(24.dp))

            // « Envoyer » domine : c'est l'action terminale. Avant, les trois
            // boutons avaient exactement le meme poids.
            Button(
                onClick = onSubmit,
                enabled = pretAEnvoyer,
                shape = RoundedCornerShape(14.dp),
                // Desactive, il etait rempli d'`EtatVide` -- exactement la
                // couleur d'une carte de scan non faite. L'ecran montrait donc
                // trois cartes grises identiques, et rien ne disait laquelle
                // etait l'action. Vide et cercle d'un trait, il se lit comme un
                // emplacement qui attend d'etre rempli.
                border = if (pretAEnvoyer) null else BorderStroke(1.dp, Trait),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = Encre2,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            ) {
                Text(
                    stringResource(R.string.send),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (!pretAEnvoyer) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.rien_a_envoyer),
                    fontSize = 14.sp,
                    color = Encre2,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(20.dp))

            // « Effacer » s'efface. Avant, il faisait la meme taille
            // qu'« Envoyer » et se trouvait juste dessous : un pouce qui glisse
            // perdait le scan.
            TextButton(
                onClick = { if (pretAEnvoyer || climberId != null || blocId != null)
                                confirmerReset = true else onReset() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.reset), fontSize = 16.sp, color = Encre2)
            }

            if (historique.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Journal(historique)
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmerReset) {
        AlertDialog(
            onDismissRequest = { confirmerReset = false },
            title = { Text(stringResource(R.string.effacer_titre)) },
            text = { Text(stringResource(R.string.effacer_detail)) },
            confirmButton = {
                TextButton(onClick = { confirmerReset = false; onReset() }) {
                    Text(stringResource(R.string.effacer_oui), color = Alerte)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmerReset = false }) {
                    Text(stringResource(R.string.effacer_non))
                }
            },
            containerColor = Carte,
        )
    }
}

/**
 * Un bouton de scan qui porte ce qu'il a scanné.
 *
 * Avant, le nom du grimpeur s'affichait dans une boîte séparée sous le bouton :
 * deux endroits à regarder pour une seule information, et le bouton restait
 * identique qu'on ait scanné ou non — seule sa couleur changeait.
 */
@Composable
private fun BoutonScan(
    titre: String,
    valeur: String?,
    complement: String?,
    fait: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (fait) EtatFait else EtatVide,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(
                text = titre.uppercase(),
                fontSize = 13.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (fait) Encre.copy(alpha = 0.75f) else Encre2,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = valeur ?: stringResource(R.string.a_scanner),
                fontSize = if (valeur != null) 28.sp else 24.sp,
                fontWeight = if (valeur != null) FontWeight.Bold else FontWeight.Normal,
                color = if (valeur != null) Encre else Encre2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (complement != null) {
                Text(
                    text = complement,
                    fontSize = 15.sp,
                    color = Encre.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/** Les dernières validations. La réponse à « est-ce que j'ai bien envoyé ? ». */
@Composable
private fun Journal(validations: List<Validation>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.dernieres_validations).uppercase(),
            fontSize = 12.sp,
            letterSpacing = 1.2.sp,
            color = Encre2,
        )
        Spacer(Modifier.height(6.dp))
        validations.forEach { v ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(v.heure, fontSize = 13.sp, color = Encre2,
                     modifier = Modifier.padding(end = 10.dp))
                Text(v.grimpeur, fontSize = 15.sp, color = Encre,
                     maxLines = 1, overflow = TextOverflow.Ellipsis,
                     modifier = Modifier.weight(1f))
                Text(v.bloc, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                     color = EtatFait)
            }
        }
    }
}

/**
 * La barre du haut : l'identite du club, et l'etat du serveur.
 *
 * Le logo occupait 100 dp — un quart de la hauteur utile sur un petit
 * telephone, pour une information que le juge connait deja.
 *
 * Elle a porte un temps les compteurs de la file, en pastilles poussees dans le
 * slot `actions`. Ce slot ne comprime pas le titre : des qu'il y avait deux
 * compteurs, les pastilles passaient PAR-DESSUS « Serveur joignable ». Ils sont
 * maintenant dans [BandeFile], juste dessous, ou ils ont la place de se lire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Barre(
    serveurJoignable: Boolean?,
    onOpenSettings: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Carte,
            titleContentColor = Encre,
        ),
        title = {
            // Le logo d'origine est un PNG 1414x1000 a fond blanc opaque, avec
            // beaucoup de marge autour du dessin. Pose dans une boite carree,
            // il se retrouvait en boite aux lettres : une bande blanche ou la
            // chevre etait minuscule. `logo_rond` est le meme dessin recadre au
            // carre, ce qui permet de le detourer en rond sans rien couper.
            Image(
                painter = painterResource(id = R.drawable.logo_rond),
                contentDescription = stringResource(R.string.app_logo),
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape),
            )
        },
        actions = {
            VoyantConnexion(serveurJoignable)
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings,
                     contentDescription = stringResource(R.string.reglages),
                     tint = Encre2)
            }
        },
    )
}

/**
 * Le voyant de connexion, en haut a droite.
 *
 * Repris du principe de sowel : une petite icone de connexion sans fil, verte
 * quand la liaison est la, rouge sinon. Elle occupait auparavant une pastille
 * et une phrase entiere a gauche de la barre — de la place prise en
 * permanence pour une information qui, presque toujours, dit « tout va bien ».
 * La phrase complete reste dans les reglages, avec l'adresse du serveur.
 *
 * L'icone est BARREE quand ca ne passe pas. Le vert et le rouge ne suffisent
 * pas : environ 8 % des hommes distinguent mal ces deux couleurs-la, et il y a
 * des juges hommes (regle posee dans ui/theme/Color.kt). La forme doit porter
 * l'information autant que la couleur.
 *
 * Pendant la verification, l'icone bat doucement : « je cherche » se distingue
 * de « c'est casse », ce qu'un simple gris ne disait pas.
 */
@Composable
private fun VoyantConnexion(serveurJoignable: Boolean?) {
    val (icone, couleur, description) = when (serveurJoignable) {
        true -> Triple(R.drawable.ic_wifi, EtatFait, R.string.serveur_ok)
        false -> Triple(R.drawable.ic_wifi_off, Alerte, R.string.serveur_ko)
        null -> Triple(R.drawable.ic_wifi, Attention, R.string.serveur_inconnu)
    }

    val battement = rememberInfiniteTransition(label = "verification")
    val opacite by battement.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "opacite",
    )

    Icon(
        painter = painterResource(id = icone),
        contentDescription = stringResource(description),
        tint = couleur,
        modifier = Modifier
            .padding(end = 4.dp)
            .size(22.dp)
            .alpha(if (serveurJoignable == null) opacite else 1f),
    )
}

/**
 * Ce qui n'est pas encore parti, quand il y a quelque chose.
 *
 * Toute la bande ouvre les reglages : c'est la que vivent « Tout envoyer » et
 * « Renvoyer », les deux gestes qui la font disparaitre.
 */
@Composable
private fun BandeFile(enAttente: Int, refusees: Int, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Carte2)
            .clickable(onClick = onOpenSettings)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (enAttente > 0) {
            Pastille(stringResource(R.string.en_attente, enAttente), Attention)
        }
        // Le rouge est reserve a ce qui demande une ACTION : une refusee ne
        // repartira pas toute seule, contrairement a une reussite en file.
        if (refusees > 0) {
            Pastille(
                pluralStringResource(R.plurals.refusees_n, refusees, refusees),
                Alerte,
            )
        }
    }
}

@Composable
private fun Pastille(texte: String, couleur: Color) {
    Text(
        text = texte,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = couleur,
        modifier = Modifier
            .padding(end = 6.dp)
            .background(couleur.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}
