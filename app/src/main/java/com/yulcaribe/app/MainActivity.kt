package com.yulcaribe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        scheduleWeatherRefresh(this)
        enableEdgeToEdge()
        setContent {
            var appearance by remember { mutableStateOf(loadAppearanceMode(this)) }
            YulCaribeTheme(appearance = appearance) {
                YulCaribeApp(onAppearanceChanged = { appearance = it })
            }
        }
    }
}
