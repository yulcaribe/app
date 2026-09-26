package com.yulcaribe.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun SettingsScreenV(
    prefs: VPreferences,
    store: YcLocalStore,
    onChange: (VPreferences) -> Unit
) {
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var airportQuery by rememberSaveable(prefs.mainAirportIcao) { mutableStateOf(prefs.mainAirportIcao) }
    var airportMatches by remember { mutableStateOf<List<Airport>>(emptyList()) }
    var airportFocused by remember { mutableStateOf(false) }
    var airportBusy by remember { mutableStateOf(false) }
    var airportGeneration by remember { mutableIntStateOf(0) }
    var favorites by remember { mutableStateOf<List<YcLocalStore.AirportCache>>(emptyList()) }
    var favoriteToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(favoriteToken) {
        favorites = store.favoriteAirports()
    }

    LaunchedEffect(airportQuery, airportFocused) {
        if (!airportFocused) return@LaunchedEffect
        val normalized = YcApi.normalizeAirportQuery(airportQuery).trim()
        if (normalized.length < 2) {
            airportMatches = emptyList()
            airportBusy = false
            return@LaunchedEffect
        }
        val generation = ++airportGeneration
        airportBusy = true
        delay(160)
        val rows = runCatching { YcApi.airportSearch(normalized, 20) }
            .getOrDefault(emptyList())
        if (generation == airportGeneration) {
            airportMatches = rows
            airportBusy = false
        }
    }

    fun chooseMainAirport(airport: Airport) {
        airportQuery = airport.icao
        airportMatches = emptyList()
        airportFocused = false
        focus.clearFocus()
        onChange(prefs.copy(mainAirportIcao = airport.icao))
        scope.launch { store.touchAirport(airport, airport.icao) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 38.dp)
    ) {
        item {
            BrandBarV("SETTINGS")
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "SETTINGS",
                    color = YcText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp
                )
                Spacer(Modifier.height(22.dp))

                KickerV("MAIN AIRPORT")
                Text(
                    "Used for Home startup, map center and pinned offline cache.",
                    color = YcMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(9.dp))

                OutlinedTextField(
                    value = airportQuery,
                    onValueChange = { airportQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { airportFocused = it.isFocused },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = YcCyan) },
                    trailingIcon = {
                        if (airportBusy) Text("…", color = YcCyan, fontSize = 18.sp)
                    },
                    placeholder = { Text("ICAO, IATA or city", color = YcMuted) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { airportMatches.firstOrNull()?.let(::chooseMainAirport) }
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                if (airportFocused && airportQuery.trim().length >= 2) {
                    Surface(
                        color = YcSurface,
                        border = BorderStroke(1.dp, YcHairline),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
                    ) {
                        when {
                            airportBusy && airportMatches.isEmpty() -> {
                                Text("Searching…", color = YcMuted, modifier = Modifier.padding(14.dp))
                            }
                            airportMatches.isEmpty() -> {
                                Text("No airport match.", color = YcMuted, modifier = Modifier.padding(14.dp))
                            }
                            else -> {
                                LazyColumn(Modifier.heightIn(max = 224.dp)) {
                                    items(airportMatches, key = { it.icao }) { airport ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { chooseMainAirport(airport) }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(Modifier.width(72.dp)) {
                                                Text(
                                                    airport.icao,
                                                    color = YcCyan,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                                airport.iata?.let { Text(it, color = YcMuted, fontSize = 9.sp) }
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    airport.name,
                                                    color = YcText,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(airport.city ?: "", color = YcMuted, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    "Current: ${prefs.mainAirportIcao}",
                    color = YcCyanSoft,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 7.dp)
                )

                Spacer(Modifier.height(26.dp))
                KickerV("APPEARANCE")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    OptionChipV("SYSTEM", prefs.appearance == AppearanceMode.System) {
                        onChange(prefs.copy(appearance = AppearanceMode.System))
                    }
                    OptionChipV("LIGHT", prefs.appearance == AppearanceMode.Light) {
                        onChange(prefs.copy(appearance = AppearanceMode.Light))
                    }
                    OptionChipV("DARK", prefs.appearance == AppearanceMode.Dark) {
                        onChange(prefs.copy(appearance = AppearanceMode.Dark))
                    }
                }

                Spacer(Modifier.height(26.dp))
                KickerV("MAP DEFAULTS")
                SettingsToggleV("Charts", "Load chart layers when Map opens.", prefs.mapCharts) {
                    onChange(prefs.copy(mapCharts = it))
                }
                SettingsToggleV("NOTAM", "Load live NOTAM map layer when Map opens.", prefs.mapNotam) {
                    onChange(prefs.copy(mapNotam = it))
                }
                SettingsToggleV("WAFS", "Load the WAFS layer when Map opens.", prefs.mapWafs) {
                    onChange(prefs.copy(mapWafs = it))
                }
                SettingsToggleV("ADS-B", "Show live aircraft by default.", prefs.mapAdsb) {
                    onChange(prefs.copy(mapAdsb = it))
                }

                Spacer(Modifier.height(26.dp))
                KickerV("AIRPORT RESULTS")
                SettingsToggleV("Show RAW", "Original METAR and TAF source text.", prefs.showRaw) {
                    onChange(prefs.copy(showRaw = it))
                }
                SettingsToggleV("Show decoded", "Structured aviation fields parsed on device.", prefs.showDecoded) {
                    onChange(prefs.copy(showDecoded = it))
                }
                SettingsToggleV("Show explanation", "Human-readable summary below each product.", prefs.showExplanation) {
                    onChange(prefs.copy(showExplanation = it))
                }

                Spacer(Modifier.height(26.dp))
                KickerV("FAVORITE AIRPORTS")
                if (favorites.isEmpty()) {
                    Text(
                        "Star an airport on Home to keep its chart and weather cache pinned.",
                        color = YcMuted,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                } else {
                    favorites.forEach { cached ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = YcAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    cached.airport.icao + (cached.airport.iata?.let { " / $it" } ?: ""),
                                    color = YcText,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    cached.airport.name,
                                    color = YcMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                onClick = {
                                    scope.launch {
                                        store.setFavorite(cached.airport, false, prefs.mainAirportIcao)
                                        favoriteToken++
                                    }
                                },
                                color = YcSurfaceHigh,
                                border = BorderStroke(1.dp, YcHairline),
                                shape = RoundedCornerShape(5.dp)
                            ) {
                                Text(
                                    "REMOVE",
                                    color = YcMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = YcHairline.copy(alpha = 0.7f))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsToggleV(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = YcText, fontSize = 13.sp)
            Text(
                detail,
                color = YcMuted,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 2.dp, end = 16.dp)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = YcVoid,
                checkedTrackColor = YcCyan,
                uncheckedThumbColor = YcMuted,
                uncheckedTrackColor = YcSurfaceHigh
            )
        )
    }
    HorizontalDivider(color = YcHairline.copy(alpha = 0.7f))
}
