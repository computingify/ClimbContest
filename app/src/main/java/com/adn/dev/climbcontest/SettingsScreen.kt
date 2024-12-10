import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adn.dev.climbcontest.R

@Composable
fun SettingsScreen(
    currentAddress: String,
    onAddressChange: (String) -> Unit,
    onBack: () -> Unit,
    context: Context // Pass the context to retrieve version name
) {
    var address by remember { mutableStateOf(currentAddress) }

    // Retrieve the app version name from the context
    val versionName = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }

    // Handle the Android back button
    BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Display the version name on the top left corner
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.version, versionName),
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Server Settings", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text(stringResource(R.string.server_address)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onAddressChange(address)
                onBack()
            })
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            onAddressChange(address)
            onBack()
        }) {
            Text(stringResource(R.string.save))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onBack) {
            Text(stringResource(R.string.back))
        }
    }
}
