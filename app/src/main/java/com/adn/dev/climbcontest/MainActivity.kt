package com.adn.dev.climbcontest

import SettingsScreen
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.random.Random

const val RUN_ON_EMULATOR = 1
const val RUN_LOCAL_SERVER = 0

class MainActivity : ComponentActivity() {

    // Define the scanner as a class member
    private lateinit var scanner: GmsBarcodeScanner
    private val mainViewModel: MainViewModel by viewModels()
    // Create a custom OkHttpClient with self-signed certificate support
    private var client = OkHttpClient.Builder().build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Load server address from shared preferences
        mainViewModel.loadServerAddress(this)

        setContent {
            ClimbContestTheme {
                AppContent(mainViewModel)
            }
        }

    }

    override fun onPause() {
        super.onPause()
        mainViewModel.saveServerAddress(this) // Save server address on pause
    }

    @Composable
    fun AppContent(viewModel: MainViewModel) {
        var isSettingsScreen by remember { mutableStateOf(false) }

        if (isSettingsScreen) {
            SettingsScreen(
                currentAddress = viewModel.serverAddress.collectAsState().value,
                onAddressChange = { newAddress ->
                    viewModel.updateServerAddress(newAddress)
                },
                onBack = { isSettingsScreen = false },
                this
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
                    onSubmit = { submit() },
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
        client = createHttpClientWithSelfSignedCert()
    }

    private fun startScanning(scanType: String) {
        if (("climber" == scanType && null == mainViewModel.climberId.value)
            || ("bloc" == scanType && null == mainViewModel.blocId.value)
        ) {
            if (1 == RUN_ON_EMULATOR) {
                handleScannedValue(scanType, "")
                return
            }
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
            var localScannedValue = scannedValue
            if (1 == RUN_ON_EMULATOR) {
                when (scanType) {
                    "climber" -> {
                        localScannedValue = (1..39).random().toString()
                    }

                    "bloc" -> {
                        localScannedValue = (Random.nextInt(
                            from = 'A'.code,
                            until = 'N'.code + 1
                        )).toChar() + (1..5).random().toString()
                        localScannedValue = "F3"
                    }
                }
            }
            val isAccepted = checkOnServer(scanType, localScannedValue)
            withContext(Dispatchers.Main) {
                if (isAccepted) {
                    when (scanType) {
                        "climber" -> mainViewModel.setClimberId(localScannedValue)
                        "bloc" -> mainViewModel.setBlocId(localScannedValue)
                    }
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.id_accepted, scanType, localScannedValue),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        getString(
                            R.string.id_rejected_please_scan_again,
                            scanType,
                            localScannedValue
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun checkOnServer(scanType: String, scannedValue: String): Boolean {

        // Create JSON payload
        val payload = JSONObject().apply {
            put("id", scannedValue)
        }

        var uri = ""
        when (scanType) {
            "climber" -> uri = "climber/name"
            "bloc" -> uri = "bloc/name"
        }

        when (val result = sendPostToServer(payload, uri)) {
            is ServerResponse.Success -> {
                return try {
                    val isSuccess = result.data.optBoolean("success", false)
                    if (isSuccess) {
                        // Extract the "id" value from the response
                        val id = result.data.optString("id", "")
                        if (id != "") {
                            when (scanType) {
                                "climber" -> mainViewModel.setClimberName(id)
                                "bloc" -> mainViewModel.setBlocName(id)
                            }
                        }
                    }
                    isSuccess
                } catch (jsonException: Exception) {
                    println("JSON parsing error: ${jsonException.message}")
                    false
                }
            }
            is ServerResponse.Failure -> {
                // Handle the failure case
                println("Error: ${result.errorMessage}")
                // Show error to the user or log it
                return false
            }
        }
    }

    private fun submit() {
        CoroutineScope(Dispatchers.IO).launch {
            // Create JSON payload
            val payload = JSONObject().apply {
                put("bib", mainViewModel.climberId.value)
                put("bloc", mainViewModel.blocId.value)
            }

            val uri = "success"

            val result = sendPostToServer(payload, uri)
            withContext(Dispatchers.Main) {
                when (result) {
                    is ServerResponse.Success -> {
                        try {
                            val isSuccess = result.data.optBoolean("success", false)
                            if (isSuccess) {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.climber_and_bloc_successfully_registered),
                                    Toast.LENGTH_SHORT
                                ).show()
                                // Delay and reset on success
                                CoroutineScope(Dispatchers.IO).launch {
                                    delay(2000)
                                    mainViewModel.reset()
                                }
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.submit_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (jsonException: Exception) {
                            println("JSON parsing error: ${jsonException.message}")
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.json_parsing_error),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    is ServerResponse.Failure -> {
                        // Handle the failure case
                        println("Error: ${result.errorMessage}")
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.network_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }


    private fun sendPostToServer(payload: JSONObject, requestedApi: String): ServerResponse{
        var url = if (1 == RUN_LOCAL_SERVER) {
            "https://10.0.2.2"
        } else {
            "https://${mainViewModel.serverAddress.value}"
        }
        url += ":5007/api/v2/contest/$requestedApi"

        // Convert payload to JSON string and create request body
        val requestBody: RequestBody = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())

        // Build the HTTP request
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    responseBody?.let {
                        try {
                            ServerResponse.Success(JSONObject(it))
                        } catch (jsonException: Exception) {
                            ServerResponse.Failure("JSON parsing error: ${jsonException.message}")
                        }
                    } ?: ServerResponse.Failure("Empty response body")
                } else {
                    ServerResponse.Failure("HTTP request failed with code: ${response.code}")
                }
            }
        } catch (networkException: Exception) {
            ServerResponse.Failure("Network error: ${networkException.message}")
        }
    }

    private fun createHttpClientWithSelfSignedCert(): OkHttpClient {
        // Load the self-signed certificate
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certificate: X509Certificate = resources.openRawResource(R.raw.cert).use { certStream ->
            certificateFactory.generateCertificate(certStream) as X509Certificate
        }

        // Create a KeyStore and add the certificate
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("self-signed", certificate)
        }

        // Create a TrustManager using the KeyStore
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore)
        }

        // Create an SSLContext
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustManagerFactory.trustManagers, null)
        }

        // Build the OkHttpClient with the custom SSL settings
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManagerFactory.trustManagers[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true } // Disable hostname verification for development
            .build()
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
            horizontalAlignment = Alignment.CenterHorizontally
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
                Text(
                    stringResource(R.string.climber) + ": $climberName",
                    fontSize = infoTextSize.sp,
                    modifier = Modifier
                        .background(Color.LightGray) // Set your background color here
                        .padding(6.dp)
                )
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
                Text(
                    stringResource(R.string.block) + ": $blocName",
                    fontSize = infoTextSize.sp,
                    modifier = Modifier
                        .background(Color.LightGray) // Set your background color here
                        .padding(6.dp)
                )
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
                    .height(buttonSize.dp) // Set button height
                    .fillMaxWidth() // Make the button take full width
            ) {
                Text(stringResource(R.string.reset), fontSize = buttonTextSize.sp)
            }
        }
    }
}
