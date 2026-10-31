package org.stypox.dicio.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.util.Collections
import java.util.WeakHashMap

private val shown: MutableSet<Any> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

/**
 * True only the first time [key] (usually the answer itself) is on screen. Scrolling back to an
 * answer, coming back from Settings or from recents composes it again, and its haptic and entrance
 * must not play again then.
 */
@Composable
fun rememberFirstShow(key: Any): Boolean = remember(key) { shown.add(key) }
