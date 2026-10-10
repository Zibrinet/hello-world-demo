package com.tenniscut

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.io.File

class MainActivity : ComponentActivity() {
    /** Set by the record screen so clicker presses reach it before anything else sees them. */
    var clicker: Clicker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TennisTheme {
                Surface(Modifier.fillMaxSize()) { App(this) }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = clicker ?: return super.dispatchKeyEvent(event)
        val handled = when (event.action) {
            KeyEvent.ACTION_DOWN -> handler.onKeyDown(event)
            KeyEvent.ACTION_UP -> handler.onKeyUp(event)
            else -> false
        }
        return handled || super.dispatchKeyEvent(event)
    }

    val sessionsRoot: File get() = File(getExternalFilesDir(null), "sessions")
}

private const val HOME = "home"
private const val SETUP = "setup"
private const val RECORD = "record:"
private const val MATCH = "match:"
private const val SCORE = "score:"

@Composable
private fun App(activity: MainActivity) {
    // "home", "setup", or "<screen>:<match folder>"; saveable so the screen survives the app being recreated.
    var screen by rememberSaveable { mutableStateOf(HOME) }
    fun session(prefix: String) = SessionDir(File(activity.sessionsRoot, screen.removePrefix(prefix)))
    when {
        screen == HOME -> HomeScreen(
            activity,
            onNewMatch = { screen = SETUP },
            onOpen = { screen = MATCH + it.dir.name },
        )
        screen == SETUP -> SetupScreen(
            activity,
            onBack = { screen = HOME },
            onStart = { screen = RECORD + it.dir.name },
        )
        screen.startsWith(RECORD) -> RecordScreen(
            activity,
            session(RECORD),
            onFinished = { screen = MATCH + it.dir.name },
            onCancel = { screen = HOME },
        )
        screen.startsWith(MATCH) -> MatchScreen(
            session(MATCH),
            onBack = { screen = HOME },
            onScore = { screen = SCORE + screen.removePrefix(MATCH) },
        )
        screen.startsWith(SCORE) -> ScoringScreen(session(SCORE), onDone = { screen = MATCH + screen.removePrefix(SCORE) })
    }
}
