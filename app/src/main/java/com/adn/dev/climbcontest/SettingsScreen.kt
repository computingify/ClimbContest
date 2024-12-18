import android.content.Context
import android.util.Log
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
import com.adn.dev.climbcontest.MainViewModel
import com.adn.dev.climbcontest.R

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    mainViewModel: MainViewModel,
    context: Context // Pass the context to retrieve version name
) {
    var checked by remember { mutableStateOf(mainViewModel.autoEval) }

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

        // Add the Switch button for autoEvaluate
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = stringResource(R.string.auto_evaluate), fontSize = 16.sp)
            Switch(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    Log.d("DEBUG", "onCheckedChange: $checked")
                    when (checked) {
                        true -> mainViewModel.enableAutoEval()
                        false -> mainViewModel.disableAutoEval()
                    }
                    mainViewModel.reset()
                }
            )
        }
    }
}
