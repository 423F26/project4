package com.example.liftingapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.liftingapp.ui.theme.LIFTINGAPPTheme

import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LIFTINGAPPTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "MSU Lifting Tracker",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    LIFTINGAPPTheme {
        Greeting("Android")
    }
}

fun requestPinShortcut(context: Context) {
    val shortcutManager = context.getSystemService(ShortcutManager::class.java)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (shortcutManager?.isRequestPinShortcutSupported == true) {

            val shortcut = ShortcutInfo.Builder(context, "my_shortcut_id")
                .setShortLabel("My App")
                .setLongLabel("My App Shortcut")
                .setIcon(Icon.createWithResource(context, R.drawable.ic_launcher))
                .setIntent(
                    Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_MAIN
                    }
                )
                .build()

            shortcutManager.requestPinShortcut(shortcut, null)
        }
    }
}

fun main() {

}