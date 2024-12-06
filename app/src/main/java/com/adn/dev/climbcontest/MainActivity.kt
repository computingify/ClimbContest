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
import okhttp3.Response
import org.json.JSONObject
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.random.Random

const val RUN_ON_EMULATOR = 1

class MainActivity : ComponentActivity() {

    // Define the scanner as a class member
    private lateinit var scanner: GmsBarcodeScanner
    private val mainViewModel: MainViewModel by viewModels()

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
                onBack = { isSettingsScreen = false }
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
                    onReset = { mainViewModel.reset() },
                    onOpenSettings = { isSettingsScreen = true }
                )
            }
        }

        // Set the barcode format to detect only QR Code, and enable the automatic zoom
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_AZTEC)
            .enableAutoZoom() // available on 16.1.0 and higher
            .build()

        // Create scanner instance
        scanner = GmsBarcodeScanning.getClient(this, options)
    }

    private fun startScanning(scanType: String) {
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
                Toast.makeText(this,
                    getString(R.string.failed_to_scan, e.message), Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleScannedValue(scanType: String, scannedValue: String) {
        CoroutineScope(Dispatchers.IO).launch {
            var localScannedValue = scannedValue
            if (("climber" == scanType && null == mainViewModel.climberId.value)
                || ("bloc" == scanType && null == mainViewModel.blocId.value)
            ) {
                if (1 == RUN_ON_EMULATOR) {
                    when (scanType) {
                        "climber" -> {
                            localScannedValue = (1..84).random().toString()
                        }

                        "bloc" -> {
                            localScannedValue = (Random.nextInt(
                                from = 'A'.code,
                                until = 'N'.code + 1
                            )).toChar() + (1..5).random().toString()
                        }
                    }
                }
                val isAccepted = sendToServer(scanType, localScannedValue, mainViewModel.uuid.value)
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
                        checkCompletion()
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
    }

    private fun sendToServer(scanType: String, scannedValue: String, uuid: String): Boolean {
        var url = ""
        if (1 == RUN_ON_EMULATOR) {
            url = "https://10.0.2.2:5007/api/v1/contest/$scanType"
        } else {
            url = "https://${mainViewModel.serverAddress.value}:5007/api/v1/contest/$scanType"
        }

        // Create JSON payload
        val payload = JSONObject().apply {
            put("id", scannedValue)
            put("uuid", uuid) // Use the generated UUID for the transaction
        }

        // Convert payload to JSON string and create request body
        val requestBody: RequestBody = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())

        // Build the HTTP request
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        // Create a custom OkHttpClient with self-signed certificate support
        val client = createHttpClientWithSelfSignedCert()

        return try {
            client.newCall(request).execute().use { response: Response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    responseBody?.let {
                        try {
                            val jsonResponse = JSONObject(it)
                            val isSuccess = jsonResponse.optBoolean("success", false)
                            if (isSuccess) {
                                // Extract the "id" value from the response
                                val id = jsonResponse.optString("id", "")
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
                    } ?: false
                } else {
                    println("HTTP request failed with code: ${response.code}")
                    false
                }
            }
        } catch (networkException: Exception) {
            println("Network error: ${networkException.message}")
            false
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

    private suspend fun checkCompletion() {
        if (mainViewModel.climberId.value != null && mainViewModel.blocId.value != null) {
            Toast.makeText(this,
                getString(R.string.climber_and_bloc_successfully_registered),Toast.LENGTH_SHORT).show()

            delay(2000)
            // Reset state and generate new couple ID
            mainViewModel.reset()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel,
               onScanClimber: () -> Unit,
               onScanBloc: () -> Unit,
               onReset: () -> Unit,
               onOpenSettings: () -> Unit) {

    val spacer_size = 45
    val button_size = 80
    val button_text_size = 42
    val button_info_space_size = 8
    val info_text_size = 20

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
                    .height(button_size.dp) // Set button height
                    .fillMaxWidth() // Make the button take full width
            ) {
                Text(stringResource(R.string.climber), fontSize = button_text_size.sp)
            }

            if (climberName != null) {
                Spacer(modifier = Modifier.height(button_info_space_size.dp))
                Text(
                    stringResource(R.string.climber) + ": $climberName",
                    fontSize = info_text_size.sp,
                    modifier = Modifier
                        .background(Color.LightGray) // Set your background color here
                        .padding(6.dp)
                )
            }

            Spacer(modifier = Modifier.height(spacer_size.dp))

            Button(
                onClick = onScanBloc,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = blocButtonColor),
                modifier = Modifier
                    .height(button_size.dp) // Set button height
                    .fillMaxWidth() // Make the button take full width
            ) {
                Text(stringResource(R.string.block), fontSize = button_text_size.sp)
            }

            if (blocName != null) {
                Spacer(modifier = Modifier.height(button_info_space_size.dp))
                Text(
                    stringResource(R.string.block) + ": $blocName",
                    fontSize = info_text_size.sp,
                    modifier = Modifier
                        .background(Color.LightGray) // Set your background color here
                        .padding(6.dp)
                )
            }

            Spacer(modifier = Modifier.height((spacer_size*4).dp))

            Button(
                onClick = onReset,
                modifier = Modifier
                    .height(button_size.dp) // Set button height
                    .fillMaxWidth() // Make the button take full width
            ) {
                Text(stringResource(R.string.reset), fontSize = button_text_size.sp)
            }
        }
    }
}
