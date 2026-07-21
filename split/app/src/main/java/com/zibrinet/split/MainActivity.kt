package com.zibrinet.split

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zibrinet.split.ui.navigation.SplitNavHost
import com.zibrinet.split.ui.theme.SplitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SplitTheme {
                SplitNavHost()
            }
        }
    }
}
