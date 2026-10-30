package org.stypox.dicio.skills.telephone

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The contact's photo (or initial), name and number, as the phone's own dialer shows them. While
 * [calling], the photo pulses with rings going out, like a call being placed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContactHeader(
    name: String,
    number: String?,
    modifier: Modifier = Modifier,
    calling: Boolean = false,
    avatarSize: Dp = 56.dp,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.padding(4.dp)) {
        ContactAvatar(name = name, number = number, size = avatarSize, calling = calling)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLargeEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (number != null) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContactAvatar(name: String, number: String?, size: Dp, calling: Boolean) {
    val context = LocalContext.current
    val photo by produceState<Uri?>(null, number) {
        value = number?.let { withContext(Dispatchers.IO) { photoForNumber(context, it) } }
    }
    val shape = MaterialShapes.Cookie9Sided.toShape()
    val ring = rememberInfiniteTransition(label = "callingRing")
    val ringProgress by ring.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(RING_MILLIS), RepeatMode.Restart),
        label = "ringProgress",
    )
    val pulse by ring.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(RING_MILLIS / 2), RepeatMode.Reverse),
        label = "pulse",
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        if (calling) {
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        val scale = 1f + ringProgress * 0.5f
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - ringProgress
                    }
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), shape)
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val scale = if (calling) pulse else 1f
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Text(
                text = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (photo != null) {
                AsyncImage(
                    model = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size),
                )
            }
        }
    }
}

private fun photoForNumber(context: Context, number: String): Uri? = try {
    context.contentResolver.query(
        Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
        arrayOf(ContactsContract.PhoneLookup.PHOTO_URI),
        null, null, null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0)?.let(Uri::parse) else null
    }
} catch (e: SecurityException) {
    null // no contacts permission: the initial is shown instead
}

private const val RING_MILLIS = 1400
