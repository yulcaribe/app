package com.yulcaribe.app

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class VDestination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Map("Map", Icons.Outlined.Map),
    Notam("NOTAM", Icons.Outlined.Description),
    Briefing("Pilot Briefing", Icons.Outlined.FlightTakeoff),
    Settings("Settings", Icons.Outlined.Settings)
}

internal data class VPreferences(
    val showRaw: Boolean = true,
    val showDecoded: Boolean = true,
    val showExplanation: Boolean = true,
    val mainAirportIcao: String = "LTAI",
    val appearance: AppearanceMode = AppearanceMode.System,
    val mapCharts: Boolean = false,
    val mapNotam: Boolean = false,
    val mapWafs: Boolean = false,
    val mapAdsb: Boolean = true
)

internal data class VMapLayers(
    val charts: Boolean = false,
    val airports: Boolean = true,
    val navaids: Boolean = true,
    val waypoints: Boolean = true,
    val airways: Boolean = true,
    val sid: Boolean = true,
    val star: Boolean = true,
    val airspace: Boolean = true,
    val notam: Boolean = false,
    val wafs: Boolean = false,
    val adsb: Boolean = true
) {
    fun chartSet(): Set<String> = buildSet {
        if (!charts) return@buildSet
        if (airports) add("airport")
        if (navaids) add("navaid")
        if (waypoints) add("waypoint")
        if (airways) add("airway")
        if (sid) add("sid")
        if (star) add("star")
        if (airspace) add("airspace")
    }
}

internal val ltaiFallbackV = Airport(
    id = 0,
    icao = "LTAI",
    iata = "AYT",
    name = "Antalya Airport",
    city = "Antalya",
    lat = 36.8987,
    lon = 30.8005,
    elevationFt = 177
)

@Composable
fun YulCaribeAppVNext(
    onAppearanceChanged: (AppearanceMode) -> Unit = {}
) {
    val context = LocalContext.current
    val store = remember { YcLocalStore.get(context) }
    var destination by rememberSaveable { mutableStateOf(VDestination.Home) }
    var prefs by remember { mutableStateOf(loadVPreferences(context)) }

    fun updatePreferences(value: VPreferences) {
        val oldAppearance = prefs.appearance
        prefs = value
        saveVPreferences(context, value)
        if (oldAppearance != value.appearance) onAppearanceChanged(value.appearance)
    }

    Scaffold(
        containerColor = YcVoid,
        bottomBar = {
            NavigationBar(
                containerColor = YcSurface.copy(alpha = 0.97f),
                tonalElevation = 0.dp,
                modifier = Modifier.border(1.dp, YcHairline)
            ) {
                VDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = {
                            Text(
                                item.label,
                                fontSize = if (item == VDestination.Briefing) 8.sp else 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = YcCyan,
                            selectedTextColor = YcText,
                            indicatorColor = YcCyan.copy(alpha = 0.12f),
                            unselectedIconColor = YcMuted,
                            unselectedTextColor = YcMuted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            when (destination) {
                VDestination.Home -> HomeScreenFinalV(prefs, store)
                VDestination.Map -> MapScreenFinalV(prefs, ::updatePreferences)
                VDestination.Notam -> NotamScreenV(prefs)
                VDestination.Briefing -> BriefingScreenV(store)
                VDestination.Settings -> SettingsScreenV(prefs, store, ::updatePreferences)
            }
        }
    }
}

internal fun loadVPreferences(context: Context): VPreferences {
    val store = context.getSharedPreferences("yulcaribe_preferences", Context.MODE_PRIVATE)
    return VPreferences(
        showRaw = store.getBoolean("show_raw", true),
        showDecoded = store.getBoolean("show_decoded", true),
        showExplanation = store.getBoolean("show_explanation", true),
        mainAirportIcao = store.getString("main_airport", "LTAI")
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.matches(Regex("^[A-Z0-9]{3,4}$")) }
            ?: "LTAI",
        appearance = AppearanceMode.fromStored(store.getString("appearance", "system")),
        mapCharts = store.getBoolean("map_charts", false),
        mapNotam = store.getBoolean("map_notam", false),
        mapWafs = store.getBoolean("map_wafs", false),
        mapAdsb = store.getBoolean("map_adsb", true)
    )
}

internal fun saveVPreferences(context: Context, prefs: VPreferences) {
    context.getSharedPreferences("yulcaribe_preferences", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("show_raw", prefs.showRaw)
        .putBoolean("show_decoded", prefs.showDecoded)
        .putBoolean("show_explanation", prefs.showExplanation)
        .putString("main_airport", prefs.mainAirportIcao.uppercase())
        .putString("appearance", prefs.appearance.storedValue)
        .putBoolean("map_charts", prefs.mapCharts)
        .putBoolean("map_notam", prefs.mapNotam)
        .putBoolean("map_wafs", prefs.mapWafs)
        .putBoolean("map_adsb", prefs.mapAdsb)
        .apply()
}
