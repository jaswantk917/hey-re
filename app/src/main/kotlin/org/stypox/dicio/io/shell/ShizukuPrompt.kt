package org.stypox.dicio.io.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.Headline

/** What's missing before Hey re can use Shizuku, with the one button that fixes it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShizukuPrompt() {
    val context = LocalContext.current
    val status by Shell.status.collectAsState()
    val notRunning = status == Shell.Status.NotRunning
    Column {
        Headline(
            text = stringResource(
                if (notRunning) R.string.skill_toggle_start_shizuku else R.string.skill_toggle_allow_shizuku
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        FilledTonalButton(
            onClick = {
                if (notRunning) {
                    context.packageManager.getLaunchIntentForPackage(Shell.SHIZUKU_PACKAGE)
                        ?.let { context.startActivity(it) }
                } else {
                    Shell.requestPermission()
                }
            },
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(if (notRunning) R.string.skill_toggle_open_shizuku else R.string.skill_toggle_allow))
        }
    }
}
