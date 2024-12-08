import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.adn.dev.climbcontest.R

@Composable
fun SettingsScreen(
    currentAddress: String,
    onAddressChange: (String) -> Unit,
    onBack: () -> Unit
) {
    var address by remember { mutableStateOf(currentAddress) }

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
        Text(text = "Server Settings", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text(stringResource(R.string.server_address)) },
            modifier = Modifier.fillMaxWidth()
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
