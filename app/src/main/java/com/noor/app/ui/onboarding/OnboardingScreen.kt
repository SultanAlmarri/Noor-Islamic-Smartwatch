package com.noor.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.app.R
import com.noor.app.data.LocationMode
import com.noor.app.ui.settings.SettingsViewModel
import com.noor.app.ui.theme.NoorTheme

/**
 * First run: explain why location is needed before Android asks (Play policy: prominent disclosure),
 * then offer background updates separately, or a manual city.
 */
@Composable
fun OnboardingScreen(contentPadding: PaddingValues, onChooseCity: () -> Unit, onFinished: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val c = NoorTheme.colors
    var step by remember { mutableStateOf(0) }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setBackgroundUpdates(granted); vm.completeOnboarding(); onFinished()
    }
    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.any { it }
        if (granted) { vm.setLocationMode(LocationMode.AUTO); step = 1 } else { vm.completeOnboarding(); onChooseCity() }
    }
    Box(Modifier.fillMaxSize().padding(contentPadding).padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(painterResource(R.drawable.ic_splash_mark), null, tint = Color.Unspecified, modifier = Modifier.size(120.dp))
            Text(stringResource(if (step == 0) R.string.perm_title else R.string.bg_perm_title), style = MaterialTheme.typography.headlineSmall, color = c.textPrimary, textAlign = TextAlign.Center)
            Text(stringResource(if (step == 0) R.string.perm_body else R.string.bg_perm_body), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    // Android shows its precise/approximate choice; Noor works with either.
                    if (step == 0) foreground.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    else { vm.setBackgroundUpdates(true); vm.completeOnboarding(); onFinished() }
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.gold, contentColor = Color(0xFF1A1206)),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(stringResource(if (step == 0) R.string.perm_allow else R.string.bg_perm_continue), style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = {
                vm.completeOnboarding()
                if (step == 0) onChooseCity() else onFinished()
            }) { Text(stringResource(if (step == 0) R.string.perm_manual else R.string.bg_perm_not_now), color = c.textSecondary) }
        }
    }
}
