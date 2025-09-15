package com.adn.dev.climbcontest

import android.content.Context
import android.view.Gravity
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import org.json.JSONObject
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.delay

class Server(private val mainViewModel: MainViewModel, private val context: Context, private val RUN_LOCAL_SERVER: Int) {

    // Create a custom OkHttpClient with self-signed certificate support
    private var client = createDefaultHttpClient()
    private val WAIT_TIME_RETRY_MS = 150

    // Add a listener to notify about the submit result
    interface SubmitListener {
        fun onSubmitSuccess()
        fun onSubmitFailure()
    }

    var submitListener: SubmitListener? = null

    fun checkOnServer(scanType: String, scannedValue: String): Boolean {

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

    fun submit() {
        CoroutineScope(Dispatchers.IO).launch {
            // Create JSON payload
            val payload = JSONObject().apply {
                put("bib", mainViewModel.climberId.value)
                put("bloc", mainViewModel.blocId.value)
            }

            val uri = "success"
            var retryCount = 0
            val maxRetries = 5
            var isSuccess = false
            var result: ServerResponse?

            while ((retryCount < maxRetries) && !isSuccess) {
                result = sendPostToServer(payload, uri)
                when (result) {
                    is ServerResponse.Success -> {
                        try {
                            isSuccess = result.data.optBoolean("success", false)
                            if (isSuccess) {
                                break // Exit the loop if successful
                            } else {
                                retryCount++
                                delay(WAIT_TIME_RETRY_MS.toLong()) // Wait for 1 second before retrying
                            }
                        } catch (jsonException: Exception) {
                            println("JSON parsing error: ${jsonException.message}")
                            retryCount++
                            delay(WAIT_TIME_RETRY_MS.toLong())
                        }
                    }
                    is ServerResponse.Failure -> {
                        println("Error: ${result.errorMessage}")
                        retryCount++
                        delay(WAIT_TIME_RETRY_MS.toLong())
                    }
                    else -> {
                        retryCount++
                        delay(WAIT_TIME_RETRY_MS.toLong())
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (isSuccess) {
                    val successToast = Toast.makeText(
                        context,
                        context.getString(R.string.climber_and_bloc_successfully_registered),
                        Toast.LENGTH_SHORT
                    )
                    successToast.setGravity(Gravity.TOP, 0, 0)
                    successToast.show()
                    // Notify success
                    submitListener?.onSubmitSuccess()
                    // Delay and reset on success
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(500)
                        mainViewModel.reset(!mainViewModel.autoEval)
                    }
                } else {
                    val failureToast = Toast.makeText(
                        context,
                        context.getString(R.string.submit_failed),
                        Toast.LENGTH_LONG
                    )
                    failureToast.setGravity(Gravity.TOP, 0, 0)
                    failureToast.show()
                    // Notify failure
                    submitListener?.onSubmitFailure()
                }
            }
        }
    }


    private fun sendPostToServer(payload: JSONObject, requestedApi: String): ServerResponse{
        var url = if (1 == RUN_LOCAL_SERVER) {
            "https://10.0.2.2"
        } else {
            "https://climbcontestserver.onrender.com"
        }
        url += "/api/v2/contest/$requestedApi"

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

    private fun createDefaultHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .hostnameVerifier { _, _ -> true } // Disable hostname verification for development
            .build()
    }

    private fun createHttpClientWithSelfSignedCert(): OkHttpClient {
        // Load the self-signed certificate
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certificate: X509Certificate = context.resources.openRawResource(R.raw.cert).use { certStream ->
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