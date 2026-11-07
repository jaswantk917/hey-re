package org.stypox.dicio.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.stypox.dicio.io.input.SttState

class TypingOrListeningTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var sttState by mutableStateOf<SttState?>(SttState.Loaded)
    private var typedText by mutableStateOf<String?>(null)
    private var cancels = 0

    private fun setUp() = composeRule.setContent {
        TypingOrListening(sttState, typedText, { typedText = it }, { cancels += 1 })
    }

    @Test
    fun openingTheKeyboardWhileListeningStopsTheMicrophone() {
        sttState = SttState.Listening
        setUp()
        typedText = ""
        composeRule.waitForIdle()
        assertEquals(1, cancels)
        assertEquals("", typedText)
    }

    @Test
    fun openingTheKeyboardWhileQuietLeavesTheMicrophoneAlone() {
        setUp()
        typedText = ""
        composeRule.waitForIdle()
        assertEquals(0, cancels)
    }

    @Test
    fun listeningOnAnEmptyComposerGoesBackToTheOrb() {
        setUp()
        typedText = ""
        composeRule.waitForIdle()
        sttState = SttState.Listening
        composeRule.waitForIdle()
        assertEquals(null, typedText)
        assertEquals(0, cancels)
    }

    @Test
    fun listeningOnAHalfTypedMessageKeepsItAndLetsTheMicrophoneGo() {
        setUp()
        typedText = "kal subah"
        composeRule.waitForIdle()
        sttState = SttState.Listening
        composeRule.waitForIdle()
        assertEquals("kal subah", typedText)
        assertEquals(1, cancels)
    }
}
