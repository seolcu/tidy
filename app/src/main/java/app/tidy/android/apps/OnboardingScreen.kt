package app.tidy.android.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tidy.android.R
import app.tidy.android.ui.components.SettingsRow
import app.tidy.android.ui.components.TidyMark

/** First launch: what Tidy just did, and whether to make it the home app. */
@Composable
fun OnboardingScreen(state: AppsState?, onSetHome: () -> Unit, onKeepHome: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 40.dp),
        ) {
            TidyMark()
            Text(
                if (state == null) stringResource(R.string.onboarding_loading)
                else pluralStringResource(R.plurals.onboarding_title, state.all.size, state.all.size, state.folders.size),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                stringResource(R.string.onboarding_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    SettingsRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = stringResource(R.string.onboarding_new_apps),
                        supporting = stringResource(R.string.onboarding_new_apps_hint),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        icon = Icons.Rounded.PushPin,
                        title = stringResource(R.string.onboarding_moves_stay),
                        supporting = stringResource(R.string.onboarding_moves_stay_hint),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        icon = Icons.Rounded.Lock,
                        title = stringResource(R.string.onboarding_private),
                        supporting = stringResource(R.string.onboarding_private_hint),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
            Button(
                onClick = onSetHome,
                enabled = state != null,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.onboarding_set_home)) }
            TextButton(
                onClick = onKeepHome,
                enabled = state != null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(R.string.onboarding_keep_home)) }
        }
    }
}
