package com.adn.dev.climbcontest

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.util.UUID

class MainActivity : ComponentActivity() {

    // Define the scanner as a class member
    private lateinit var scanner: GmsBarcodeScanner
    private var climberId: String? = null
    private var blocId: String? = null
    private var coupleId: String = UUID.randomUUID().toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClimbContestTheme {
                MainScreen(
                    onScanClimber = { startScanning("Climber") },
                    onScanBloc = { startScanning("Bloc") })
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
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val scannedValue = barcode.displayValue ?: "Unknown"
                when (scanType) {
                    "Climber" -> handleScannedValue("Climber", scannedValue)
                    "Bloc" -> handleScannedValue("Bloc", scannedValue)
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(this, "Failed to scan: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleScannedValue(scanType: String, scannedValue: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val isAccepted = sendToServer(scanType, scannedValue)
            withContext(Dispatchers.Main) {
                if (isAccepted) {
                    when (scanType) {
                        "Climber" -> {
                            climberId = scannedValue
                            Toast.makeText(this@MainActivity, "Climber ID accepted", Toast.LENGTH_SHORT).show()
                        }
                        "Bloc" -> {
                            blocId = scannedValue
                            Toast.makeText(this@MainActivity, "Bloc ID accepted", Toast.LENGTH_SHORT).show()
                        }
                    }
                    checkCompletion()
                } else {
                    Toast.makeText(this@MainActivity, "$scanType ID rejected. Please scan again.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun sendToServer(scanType: String, scannedValue: String): Boolean {
        // Mocked API request and response
        // Replace with actual HTTP POST logic using libraries like Retrofit or OkHttp
        delay(1000) // Simulate network delay
        return true // Assume server responds with `true` for successful handling
    }

    private fun checkCompletion() {
        if (climberId != null && blocId != null) {
            Toast.makeText(
                this,
                "Climber and Bloc successfully registered. Generating new couple ID...",
                Toast.LENGTH_SHORT
            ).show()

            // Reset state and generate new couple ID
            climberId = null
            blocId = null
            coupleId = UUID.randomUUID().toString()
        }
    }
}

@Composable
fun MainScreen(onScanClimber: () -> Unit, onScanBloc: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onScanClimber) {
            Text("Grimpeur")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onScanBloc) {
            Text("Bloc")
        }
    }
}