package com.adn.dev.climbcontest

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class MainActivity : ComponentActivity() {

    // Define the scanner as a class member
    private lateinit var scanner: GmsBarcodeScanner
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ClimbContestTheme {
                MainScreen(
                    viewModel = mainViewModel,
                    onScanClimber = { startScanning("climber") },
                    onScanBloc = { startScanning("bloc") },
                    onReset = { mainViewModel.reset() }
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
        handleScannedValue(scanType, "") // Todo: to be deleted
        return
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val scannedValue = barcode.displayValue ?: "Unknown"
                handleScannedValue(scanType, scannedValue)
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(this, "Failed to scan: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleScannedValue(scanType: String, scannedValue: String) {
        CoroutineScope(Dispatchers.IO).launch {
            var localScannedValue = scannedValue
            // TODO: should be delete
            when (scanType) {
                "climber" -> {
                    localScannedValue = "Adrien Jouve"
                }
                "bloc" -> {
                    localScannedValue = (1..20).random().toString()
                }
            }
            // TODO: delete to here
            val isAccepted = sendToServer(scanType, localScannedValue, mainViewModel.uuid.value)
            withContext(Dispatchers.Main) {
                if (isAccepted) {
                    when (scanType) {
                        "climber" -> mainViewModel.setClimberId(localScannedValue)
                        "bloc" -> mainViewModel.setBlocId(localScannedValue)
                    }
                    Toast.makeText(this@MainActivity, "$scanType ID $localScannedValue accepted", Toast.LENGTH_SHORT).show()
                    checkCompletion()
                } else {
                    Toast.makeText(this@MainActivity, "$scanType ID $localScannedValue rejected. Please scan again.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendToServer(scanType: String, scannedValue: String, uuid: String): Boolean {
        val url = "https://10.0.2.2:5007/api/v1/contest/$scanType"

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
                        // Parse the response and check for "true" in response
                        JSONObject(it).optBoolean("success", false)
                    } ?: false
                } else {
                    false // Handle unsuccessful response
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false // Handle exceptions (e.g., network errors)
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
            Toast.makeText(this, "Climber and Bloc successfully registered.",Toast.LENGTH_SHORT).show()

            delay(2000)
            // Reset state and generate new couple ID
            mainViewModel.reset()
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel,
               onScanClimber: () -> Unit,
               onScanBloc: () -> Unit,
               onReset: () -> Unit) {

    val climberId by viewModel.climberId.collectAsState()
    val blocId by viewModel.blocId.collectAsState()

    val climberButtonColor = if (climberId != null) Color.Green else Color.Gray
    val blocButtonColor = if (blocId != null) Color.Green else Color.Gray

    Column(modifier = Modifier.fillMaxSize().padding(16.dp),
           verticalArrangement = Arrangement.Center,
           horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = {
                viewModel.isClimberButtonEnabled.value = false
                CoroutineScope(Dispatchers.IO).launch {
                    onScanClimber()
                    withContext(Dispatchers.Main) {
                        viewModel.isClimberButtonEnabled.value = true
                    }
                }
            },
            enabled = viewModel.isClimberButtonEnabled.value,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = climberButtonColor)) {
            Text("Grimpeur")
        }

        if (climberId != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Climber ID: $climberId")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.isBlocButtonEnabled.value = false
                CoroutineScope(Dispatchers.IO).launch {
                    onScanBloc()
                    withContext(Dispatchers.Main) {
                        viewModel.isBlocButtonEnabled.value = true
                    }
                }
            },
            enabled = viewModel.isBlocButtonEnabled.value,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = blocButtonColor)) {
            Text("Bloc")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (blocId != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Bloc ID: $blocId")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onReset) {
            Text("Reset")
        }
    }
}
