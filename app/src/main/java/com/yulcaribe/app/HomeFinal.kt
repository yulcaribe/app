package com.yulcaribe.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun HomeScreenFinalV(
    prefs: VPreferences,
    store: YcLocalStore
) {
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var activeAirport by remember { mutableStateOf(ltaiFallbackV) }
    var query by rememberSaveable { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Airport>>(emptyList()) }
    var searchBusy by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    var searchGeneration by remember { mutableIntStateOf(0) }
    var showResults by rememberSaveable { mutableStateOf(false) }

    var charts by remember { mutableStateOf(emptyGeoJsonV()) }
    var weather by remember { mutableStateOf<WeatherBundle?>(null) }
    var weatherCachedAt by remember { mutableStateOf(0L) }
    var weatherLive by remember { mutableStateOf(false) }
    var favorite by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(prefs.mainAirportIcao) {
        val main = prefs.mainAirportIcao.uppercase()
        store.loadAirport(main)?.let { cached ->
            activeAirport = cached.airport
            cached.chartGeoJson?.let { charts = it }
            weather = cached.weather
            weatherCachedAt = cached.weatherCachedAtMs
            favorite = cached.favorite
        }

        runCatching { YcApi.airportDetail(main) }
            .getOrNull()
            ?.let { activeAirport = it }
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
        delay(170)
        val found = runCatching { YcApi.airportSearch(normalized, 20) }
            .getOrDefault(emptyList())
        if (generation == searchGeneration && query.trim() == typed) {
            matches = found
            searchBusy = false
        }
    }

    LaunchedEffect(activeAirport.icao, refreshToken) {
        loading = showResults
        error = null
        weatherLive = false

        val cached = store.loadAirport(activeAirport.icao)
        if (cached != null) {
            activeAirport = cached.airport
            charts = cached.chartGeoJson ?: emptyGeoJsonV()
            weather = cached.weather
            weatherCachedAt = cached.weatherCachedAtMs
            favorite = cached.favorite
            loading = showResults && cached.weather == null
        } else {
            charts = emptyGeoJsonV()
            weather = null
            weatherCachedAt = 0L
            favorite = false
        }

        store.touchAirport(activeAirport, prefs.mainAirportIcao)

        coroutineScope {
            val chartTask = async {
                runCatching {
                    YcApi.chartViewport(
                        airportViewportV(activeAirport, 7),
                        setOf("airport", "navaid", "airway", "airspace")
                    )
                }
            }
            val weatherTask = async { runCatching { YcApi.weather(activeAirport.icao) } }

            chartTask.await().onSuccess { geo ->
                charts = geo
                store.saveChart(activeAirport, geo, prefs.mainAirportIcao)
            }

            weatherTask.await().fold(
                onSuccess = { fresh ->
                    weather = fresh
                    weatherCachedAt = System.currentTimeMillis()
                    weatherLive = true
                    store.saveWeather(activeAirport, fresh, prefs.mainAirportIcao)
                },
                onFailure = { failure ->
                    if (showResults && weather == null) {
                        error = failure.message ?: "Weather request failed."
                    }
                }
            )
        }
        loading = false
    }

    fun selectAirport(airport: Airport) {
        activeAirport = airport
        query = airport.icao
        matches = emptyList()
        showResults = true
        searchFocused = false
        weatherLive = false
        focus.clearFocus()
    }

    Box(Modifier.fillMaxSize().background(YcVoid)) {
        // The airport context now covers the complete Home surface. There is no
        // separate fixed-height map header, so an empty black area cannot appear.
        NativeAviationMapV2(
            center = activeAirport,
            zoom = 7.0,
            interactive = false,
            chartsGeoJson = charts,
            modifier = Modifier.fillMaxSize()
        )

        // MapLibre is left hardware-rendered and untouched. A soft translucent
        // veil provides the blur/dim impression without blanking MapView on Samsung.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = when {
                            searchFocused -> listOf(
                                YcVoid.copy(alpha = 0.22f),
                                YcVoid.copy(alpha = 0.38f),
                                YcVoid.copy(alpha = 0.48f)
                            )
                            showResults -> listOf(
                                YcVoid.copy(alpha = 0.18f),
                                YcVoid.copy(alpha = 0.34f),
                                YcVoid.copy(alpha = 0.54f)
                            )
                            else -> listOf(
                                YcVoid.copy(alpha = 0.10f),
                                YcVoid.copy(alpha = 0.16f),
                                YcVoid.copy(alpha = 0.22f)
                            )
                        }
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 42.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth()) {
                    BrandBarV()
                    Spacer(Modifier.height(if (showResults) 42.dp else 104.dp))

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    activeAirport.icao,
                                    color = YcText,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (showResults) 25.sp else 32.sp
                                )
                                Text(
                                    listOfNotNull(
                                        activeAirport.iata,
                                        activeAirport.city,
                                        activeAirport.name.takeIf {
                                            !it.equals(activeAirport.city, true)
                                        }
                                    ).distinct().joinToString(" · "),
                                    color = YcMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Surface(
                                onClick = {
                                    val next = !favorite
                                    favorite = next
                                    scope.launch {
                                        store.setFavorite(activeAirport, next, prefs.mainAirportIcao)
                                    }
                                },
                                color = YcSurface.copy(alpha = 0.70f),
                                border = BorderStroke(1.dp, YcHairline.copy(alpha = 0.75f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = if (favorite) "Remove favorite" else "Add favorite",
                                    tint = if (favorite) YcAmber else YcMuted,
                                    modifier = Modifier.padding(9.dp).size(19.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        Surface(
                            color = YcSurface.copy(alpha = if (searchFocused) 0.82f else 0.66f),
                            border = BorderStroke(
                                1.dp,
                                if (searchFocused) YcCyan.copy(alpha = 0.85f)
                                else YcHairline.copy(alpha = 0.86f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = {
                                    query = it
                                    if (!it.equals(activeAirport.icao, true)) showResults = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged {
                                        searchFocused = it.isFocused
                                        if (!it.isFocused) searchBusy = false
                                    },
                                singleLine = true,
                                placeholder = {
                                    Text("Search METAR by ICAO, IATA or city", color = YcMuted)
                                },
                                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = YcCyan) },
                                trailingIcon = {
                                    if (searchBusy) Text("…", color = YcCyan, fontSize = 18.sp)
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = { matches.firstOrNull()?.let(::selectAirport) }
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        if (searchFocused && query.trim().length >= 2 && !showResults) {
                            Surface(
                                color = YcSurface.copy(alpha = 0.92f),
                                border = BorderStroke(1.dp, YcHairline.copy(alpha = 0.9f)),
                                shape = RoundedCornerShape(9.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                            ) {
                                when {
                                    searchBusy && matches.isEmpty() -> Text(
                                        "Searching…",
                                        color = YcMuted,
                                        modifier = Modifier.padding(15.dp),
                                        fontSize = 12.sp
                                    )

                                    matches.isEmpty() -> Text(
                                        "No airport match.",
                                        color = YcMuted,
                                        modifier = Modifier.padding(15.dp),
                                        fontSize = 12.sp
                                    )

                                    else -> LazyColumn(Modifier.heightIn(max = 228.dp)) {
                                        items(matches, key = { it.icao }) { airport ->
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectAirport(airport) }
                                                    .padding(horizontal = 14.dp, vertical = 11.dp),
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
                                                        Text(
                                                            it,
                                                            color = YcMuted,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 9.sp
                                                        )
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
                                                        fontSize = 10.sp,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!showResults) {
                        // Keeps the search composition naturally centred while the full map
                        // remains visible through the entire rest of the screen.
                        Spacer(Modifier.height(320.dp))
                    }
                }
            }

            if (showResults) {
                item {
                    Spacer(Modifier.height(24.dp))
                    Surface(
                        color = YcSurface.copy(alpha = 0.80f),
                        border = BorderStroke(1.dp, YcHairline.copy(alpha = 0.68f)),
                        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 18.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        activeAirport.icao + (activeAirport.iata?.let { " / $it" } ?: ""),
                                        color = YcText,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                    Text(activeAirport.name, color = YcMuted, fontSize = 11.sp)
                                }
                                TextButton(onClick = { refreshToken++ }) {
                                    Icon(Icons.Outlined.Refresh, null, tint = YcCyan)
                                    Spacer(Modifier.width(5.dp))
                                    Text("REFRESH", color = YcCyan, fontSize = 9.sp)
                                }
                            }

                            if (loading && weather == null) {
                                Spacer(Modifier.height(14.dp))
                                Text("Loading weather…", color = YcMuted, fontSize = 12.sp)
                            }
                            error?.let {
                                Spacer(Modifier.height(12.dp))
                                ErrorBoxV(it)
                            }

                            weather?.let { wx ->
                                Spacer(Modifier.height(20.dp))
                                WeatherSectionV(
                                    title = "METAR",
                                    product = wx.metar,
                                    decoded = AviationDecoder.decodeMetar(wx.metar.raw),
                                    explanation = AviationDecoder.explainMetar(wx.metar.raw),
                                    prefs = prefs,
                                    cachedAtMs = weatherCachedAt,
                                    live = weatherLive
                                )
                                WeatherSectionV(
                                    title = "TAF",
                                    product = wx.taf,
                                    decoded = AviationDecoder.decodeTaf(wx.taf.raw),
                                    explanation = AviationDecoder.explainTaf(wx.taf.raw),
                                    prefs = prefs,
                                    cachedAtMs = weatherCachedAt,
                                    live = weatherLive
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
