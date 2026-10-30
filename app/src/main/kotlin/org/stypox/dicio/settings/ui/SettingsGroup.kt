package org.stypox.dicio.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.stypox.dicio.ui.home.groupedItemShape

/**
 * A titled block of settings drawn as one rounded card split into rows, like Android's own
 * Settings: big corners on the outside of the group, tight ones where rows meet.
 */
@Composable
fun SettingsGroup(title: String, rows: List<@Composable () -> Unit>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 10.dp),
        )
        rows.forEachIndexed { index, row ->
            if (index > 0) Spacer(modifier = Modifier.height(2.dp))
            Surface(
                shape = groupedItemShape(index, rows.size),
                color = MaterialTheme.colorScheme.surfaceBright,
                modifier = Modifier.fillMaxWidth(),
            ) {
                row()
            }
        }
    }
}
