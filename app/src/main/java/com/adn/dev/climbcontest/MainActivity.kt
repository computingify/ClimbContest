package com.adn.dev.climbcontest

import SettingsScreen
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
        enableEdgeToEdge()

        setContent {
            ClimbContestTheme {
                AppContent()
            }
        }

    }

    override fun onPause() {
        super.onPause()
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
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize() // Make the box take the full screen size
            ) {
                // Background Image
                Image(
                    painter = painterResource(id = R.drawable.background_image), // Replace with your image name
                    contentDescription = "Background Image",
                    contentScale = ContentScale.Crop, // Adjust how the image is scaled
                    modifier = Modifier.fillMaxSize() // Make the image cover the full screen
                )
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

        // Set the barcode format to detect only QR Code, and enable the automatic zoom
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE)
            .enableAutoZoom() // available on 16.1.0 and higher
            .build()

        // Create scanner instance
        scanner = GmsBarcodeScanning.getClient(this, options)
        val moduleInstallClient = ModuleInstall.getClient(this)
        moduleInstallClient
            .areModulesAvailable(scanner)
            .addOnSuccessListener {
                if (it.areModulesAvailable()) {
                    // Modules are present on the device...
                } else {
                    // Modules are not present on the device...
                    val moduleInstallRequest =
                        ModuleInstallRequest.newBuilder()
                            .addApi(scanner)
                            .build()
                    moduleInstallClient
                        .installModules(moduleInstallRequest)
//                        .addOnSuccessListener {
//                        }
//                        .addOnFailureListener {
//                            // Handle failure…
//                        }
                }
            }
            .addOnFailureListener {
                // Handle failure...
            }
        // Initialize the server communication object
        server = Server(mainViewModel, this)
        // Envoi par lots et rafraichissement du catalogue, en arriere-plan.
        // Lie au cycle de vie : la boucle s'arrete avec l'ecran.
        server.demarrerBoucleDeFond(lifecycleScope)
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
                val texte = when (verdict) {
                    MessageScan.ACCEPTE ->
                        getString(R.string.id_accepted, scanType, localScannedValue)
                    MessageScan.REFUSE ->
                        getString(R.string.id_rejected_please_scan_again,
                                  scanType, localScannedValue)
                    // « Identifiant incorrect, recommencez » sur une simple
                    // coupure reseau envoyait le juge chercher un organisateur
                    // pour un QR parfaitement valide.
                    MessageScan.ERREUR_RESEAU -> getString(R.string.scan_reseau)
                }
                Toast.makeText(this@MainActivity, texte, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isValidServerAddress(address: String): Boolean {
        val ipRegex = Regex(
            """\b((25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2})(\.(?!$)|$)){4}\b"""
        )
        val urlRegex = Regex(
            """\b(^(([a-zA-Z0-9](-?[a-zA-Z0-9])*)\.)+[a-zA-Z]{2,}(:\d+)?(/.*)?${'$'})?\b"""
        )

        return ipRegex.matches(address) || urlRegex.matches(address)
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

    val spacerSize = 45
    val buttonSize = 80
    val buttonTextSize = 42
    val buttonInfoSpaceSize = 8
    val infoTextSize = 20

    val climberId by viewModel.climberId.collectAsState()
    val climberName by viewModel.climberName.collectAsState()
    val blocId by viewModel.blocId.collectAsState()
    val blocName by viewModel.blocName.collectAsState()

    val enAttente by viewModel.enAttente.collectAsState()

    val climberButtonColor = if (climberId != null) Color.Green else Color.Gray
    val blocButtonColor = if (blocId != null) Color.Green else Color.Gray

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("") },
            navigationIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.annonay_escalade_logo),
                    contentDescription = stringResource(R.string.app_logo),
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .size(100.dp)
                )
            },
            actions = {
                // Ce que le juge doit pouvoir voir : combien de reussites
                // n'ont pas encore atteint le serveur. Sans cet indicateur, une
                // file qui ne part jamais -- backend eteint, wifi coupe toute
                // la matinee -- resterait invisible jusqu'au depouillement.
                if (enAttente > 0) {
                    Text(
                        text = stringResource(R.string.en_attente, enAttente),
                        fontSize = 16.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.padding(end = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Open Settings"
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Button(
                onClick = onScanClimber,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = climberButtonColor),
                modifier = Modifier
                    .height(buttonSize.dp) // Set button height
                    .fillMaxWidth() // Make the button take full width
            ) {
                Text(stringResource(R.string.climber), fontSize = buttonTextSize.sp)
            }

            if (climberName != null) {
                Spacer(modifier = Modifier.height(buttonInfoSpaceSize.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.climber) + ": $climberName",
                        fontSize = infoTextSize.sp,
                        modifier = Modifier
                            .background(Color.LightGray) // Set your background color here
                            .padding(6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacerSize.dp))

            Button(
                onClick = onScanBloc,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = blocButtonColor),
                modifier = Modifier
                    .height(buttonSize.dp) // Set button height
                    .fillMaxWidth() // Make the button take full width
            ) {
                Text(stringResource(R.string.block), fontSize = buttonTextSize.sp)
            }

            if (blocName != null) {
                Spacer(modifier = Modifier.height(buttonInfoSpaceSize.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.block) + ": $blocName",
                        fontSize = infoTextSize.sp,
                        modifier = Modifier
                            .background(Color.LightGray) // Set your background color here
                            .padding(6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height((spacerSize).dp))

            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .height(buttonSize.dp) // Set button height
                    .fillMaxWidth() // Make the button take full width
            ) {
                Text(stringResource(R.string.send), fontSize = buttonTextSize.sp)
            }

            Spacer(modifier = Modifier.height((spacerSize*3).dp))

            Button(
                onClick = onReset,
                modifier = Modifier
                    .height(60.dp) // Set button height
                    .fillMaxWidth(0.5f) // Make the button take full width
            ) {
                Text(stringResource(R.string.reset), fontSize = buttonTextSize.sp)
            }
        }
    }
}
