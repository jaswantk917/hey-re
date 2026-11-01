package org.stypox.dicio.io.graphical

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Says something to Hey re as if typed, with the tap haptic, for answers with things to tap ("what can you do"). */
val LocalAsk = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
fun Headline(text: String) {
    Text(
        text = text,
        textAlign = TextAlign.Start,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/** An answer's words: headline-sized when it's a few, easy-reading body text when it runs on. */
@Composable
fun AnswerText(text: String) {
    if (text.length <= SHORT_ANSWER_CHARS) {
        Headline(text = text)
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

private const val SHORT_ANSWER_CHARS = 80

@Composable
fun Subtitle(text: String) {
    Text(
        text = text,
        textAlign = TextAlign.Start,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun Body(text: String) {
    Text(
        text = text,
        textAlign = TextAlign.Start,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
