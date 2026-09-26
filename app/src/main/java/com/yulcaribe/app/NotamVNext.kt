package com.yulcaribe.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@Composable
internal fun NotamScreenV(prefs: VPreferences) {
    val focus = LocalFocusManager.current
    var activeAirport by remember { mutableStateOf(ltaiFallbackV) }
    var query by rememberSaveable { mutableStateOf(prefs.mainAirportIcao) }
    var matches by remember { mutableStateOf<List<Airport>>(emptyList()) }
    var searchFocused by remember { mutableStateOf(false) }
    var searchBusy by remember { mutableStateOf(false) }
    var searchGeneration by remember { mutableIntStateOf(0) }
    var page by remember { mutableStateOf<NotamPage?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(prefs.mainAirportIcao) {
        runCatching { YcApi.airportDetail(prefs.mainAirportIcao) }
            .onSuccess {
                activeAirport = it
                query = it.icao
            }
            .onFailure {
                if (prefs.mainAirportIcao == "LTAI") activeAirport = ltaiFallbackV
            }
    }

    LaunchedEffect(query, searchFocused) {
        if (!searchFocused) return@LaunchedEffect
        val typed = query.trim()
        val normalized = YcApi.normalizeAirportQuery(typed)
        if (normalized.length < 2) {
            matches = emptyList()
            searchBusy = false
            return@LaunchedEffect
        }
        val generation = ++searchGeneration
        searchBusy = true
        delay(160)
        val result = runCatching { YcApi.airportSearch(normalized, 20) }
            .getOrDefault(emptyList())
        if (generation == searchGeneration && query.trim() == typed) {
            matches = result
            searchBusy = false
        }
    }

    LaunchedEffect(activeAirport.icao, refreshToken) {
        loading = true
        error = null
        page = null
        runCatching { YcApi.notamsForAirport(activeAirport.icao) }
            .onSuccess { page = it }
            .onFailure { error = it.message ?: "NOTAM request failed." }
        loading = false
    }

    fun selectAirport(airport: Airport) {
        activeAirport = airport
        query = airport.icao
        matches = emptyList()
        searchFocused = false
        focus.clearFocus()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 38.dp)
    ) {
        item {
            BrandBarV("NOTAM")
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "NOTAM",
                    color = YcText,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 25.sp
                )
                Text(
                    "Current valid notices are requested live and are not stored offline.",
                    color = YcMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { searchFocused = it.isFocused },
                    singleLine = true,
                    placeholder = { Text("Search airport by ICAO, IATA or city", color = YcMuted) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = YcCyan) },
                    trailingIcon = {
                        if (searchBusy) Text("…", color = YcCyan, fontSize = 18.sp)
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { matches.firstOrNull()?.let(::selectAirport) }
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                if (searchFocused && query.trim().length >= 2) {
                    Surface(
                        color = YcSurface,
                        border = BorderStroke(1.dp, YcHairline),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp)
                    ) {
                        when {
                            searchBusy && matches.isEmpty() -> {
                                Text("Searching…", color = YcMuted, modifier = Modifier.padding(14.dp))
                            }
                            matches.isEmpty() -> {
                                Text("No airport match.", color = YcMuted, modifier = Modifier.padding(14.dp))
                            }
                            else -> {
                                LazyColumn(Modifier.heightIn(max = 224.dp)) {
                                    items(matches, key = { it.icao }) { airport ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { selectAirport(airport) }
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
                                                airport.iata?.let {
                                                    Text(it, color = YcMuted, fontSize = 9.sp)
                                                }
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    airport.name,
                                                    color = YcText,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    airport.city ?: "",
                                                    color = YcMuted,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            activeAirport.icao + (activeAirport.iata?.let { " / $it" } ?: ""),
                            color = YcText,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(activeAirport.name, color = YcMuted, fontSize = 11.sp)
                    }
                    TextButton(onClick = { refreshToken++ }) {
                        Icon(Icons.Outlined.Refresh, null, tint = YcCyan)
                        Spacer(Modifier.width(5.dp))
                        Text("REFRESH", color = YcCyan, fontSize = 9.sp)
                    }
                }

                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    ErrorBoxV(error!!)
                }
                Spacer(Modifier.height(18.dp))
                NotamListV(page = page, prefs = prefs, loading = loading)
            }
        }
    }
}
