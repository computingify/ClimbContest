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

class MainActivity : ComponentActivity() {

    // Define the scanner as a class member
    private lateinit var scanner: GmsBarcodeScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClimbContestTheme {
                MainScreen(onScanClimber = { startScanning("Climber") }, onScanBloc = { startScanning("Bloc") })
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
                val message = when (scanType) {
                    "Climber" -> "Climber Barcode: $scannedValue"
                    "Bloc" -> "Bloc Barcode: $scannedValue"
                    else -> "Unknown Barcode: $scannedValue"
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(this, "Failed to scan: ${e.message}", Toast.LENGTH_SHORT).show()
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
            Text("Scan Climber Barcode")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onScanBloc) {
            Text("Scan Bloc Barcode")
        }
    }
}