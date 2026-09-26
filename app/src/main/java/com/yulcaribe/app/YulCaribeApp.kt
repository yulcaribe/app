package com.yulcaribe.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    NavMap("Map", Icons.Outlined.Map),
    Briefing("Pilot Briefing", Icons.Outlined.FlightTakeoff),
    Settings("Settings", Icons.Outlined.Settings)
}

data class AppPreferences(
    val showRaw: Boolean = true,
    val showDecoded: Boolean = true,
    val showExplanation: Boolean = true
)

private data class MapLayers(
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

private val ltfmFallback = Airport(
    id = 0,
    icao = "LTFM",
    iata = "IST",
    name = "Istanbul Airport",
    city = "Istanbul",
    lat = 41.2753,
    lon = 28.7519,
    elevationFt = null
)

@Composable
fun YulCaribeApp() {
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var prefs by remember { mutableStateOf(AppPreferences()) }

    Scaffold(
        containerColor = YcVoid,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xF20A0E13),
                tonalElevation = 0.dp,
                modifier = Modifier.border(1.dp, YcHairline)
            ) {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = {
                            Text(
                                item.label,
                                fontSize = if (item == Destination.Briefing) 9.sp else 10.sp,
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
                Destination.Home -> HomeScreen(prefs)
                Destination.Briefing -> BriefingScreen()
                Destination.NavMap -> NavMapScreen()
                Destination.Settings -> SettingsScreen(prefs) { prefs = it }
            }
        }
    }
}

@Composable
private fun TopBrand(
    subtitle: String,
    live: Boolean
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "YULCARIBE",
                color = YcText,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                letterSpacing = 2.1.sp
            )
            Text(
                subtitle.uppercase(),
                color = YcMuted,
                fontSize = 9.sp,
                letterSpacing = 1.0.sp
            )
        }
        Text(
            if (live) "● API V1" else "○ OFFLINE",
            color = if (live) YcGreen else YcMuted,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun HomeScreen(prefs: AppPreferences) {
    val focus = LocalFocusManager.current
    var apiOk by remember { mutableStateOf(false) }
    var activeAirport by remember { mutableStateOf(ltfmFallback) }
    var query by rememberSaveable { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Airport>>(emptyList()) }
    var searchBusy by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    var searchGeneration by remember { mutableIntStateOf(0) }
    var showResults by rememberSaveable { mutableStateOf(false) }
    var weather by remember { mutableStateOf<WeatherBundle?>(null) }
    var charts by remember { mutableStateOf(emptyGeoJson()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        apiOk = YcApi.catalogOk()
        runCatching { YcApi.airportDetail("LTFM") }
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
        delay(140)

        val result = runCatching { YcApi.airportSearch(normalized, 20) }
            .getOrDefault(emptyList())

        if (generation == searchGeneration && query.trim() == typed) {
            matches = result
            searchBusy = false
        }
    }

    LaunchedEffect(activeAirport.icao, showResults, refreshToken) {
        loading = true
        error = null
        val viewport = airportViewport(activeAirport, 7)
        val chartResult = runCatching {
            YcApi.chartViewport(
                viewport,
                setOf("airport", "navaid", "airway", "airspace")
            )
        }
        chartResult.onSuccess { charts = it }

        if (showResults) {
            runCatching { YcApi.weather(activeAirport.icao) }
                .onSuccess {
                    weather = it
                    apiOk = true
                }
                .onFailure {
                    error = it.message ?: "Weather request failed."
                }
        }
        loading = false
    }

    fun selectAirport(airport: Airport) {
        activeAirport = airport
        query = airport.icao
        matches = emptyList()
        weather = null
        showResults = true
        searchFocused = false
        focus.clearFocus()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 38.dp)
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (showResults) 340.dp else 520.dp)
                    .background(YcVoid)
            ) {
                NativeAviationMap(
                    center = activeAirport,
                    zoom = 7.0,
                    interactive = false,
                    chartsGeoJson = charts,
                    notamGeoJson = emptyGeoJson(),
                    flightsGeoJson = emptyGeoJson(),
                    wafsFrame = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.52f)
                )

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to YcVoid.copy(alpha = 0.22f),
                                0.58f to YcVoid.copy(alpha = 0.42f),
                                1f to YcVoid
                            )
                        )
                )

                Column(Modifier.fillMaxSize()) {
                    TopBrand("Airport intelligence", apiOk)
                    Spacer(Modifier.weight(1f))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Kicker(if (showResults) "ACTIVE AIRPORT" else "LIVE NAV CONTEXT")
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                activeAirport.icao,
                                color = YcText,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 31.sp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                listOfNotNull(activeAirport.iata, activeAirport.city)
                                    .joinToString(" · "),
                                color = YcMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))

                        Box(Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = {
                                    query = it.uppercase()
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
                                    Text("Airport / ICAO / IATA / city", color = YcMuted)
                                },
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

                            DropdownMenu(
                                expanded = searchFocused && query.trim().length >= 2 && !showResults,
                                onDismissRequest = { searchFocused = false },
                                modifier = Modifier
                                    .fillMaxWidth(0.94f)
                                    .heightIn(max = 300.dp)
                                    .background(Color(0xFF0B1016)),
                                properties = PopupProperties(focusable = false)
                            ) {
                                if (searchBusy && matches.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Searching…", color = YcMuted) },
                                        onClick = {}
                                    )
                                } else if (matches.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No airport match.", color = YcMuted) },
                                        onClick = {}
                                    )
                                } else {
                                    matches.forEach { airport ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Column(Modifier.width(82.dp)) {
                                                        Text(
                                                            airport.icao,
                                                            color = YcCyan,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Bold
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
                                            },
                                            onClick = { selectAirport(airport) }
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
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                    Kicker("AIRPORT SEARCH")
                    Text(
                        "Search an airport. The background is real NavMap API v1 chart data centered on that airport.",
                        color = YcMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (error != null) {
                        Spacer(Modifier.height(14.dp))
                        ErrorBox(error!!)
                    }
                }
            }
        } else {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                activeAirport.icao + (activeAirport.iata?.let { " / " + it } ?: ""),
                                color = YcText,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                            Text(activeAirport.name, color = YcMuted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { refreshToken++ }) {
                            Icon(Icons.Outlined.Refresh, null, tint = YcCyan)
                            Spacer(Modifier.width(5.dp))
                            Text("REFRESH", color = YcCyan, fontSize = 10.sp)
                        }
                    }

                    if (loading && weather == null) {
                        Spacer(Modifier.height(16.dp))
                        Text("Loading live API v1 data…", color = YcMuted)
                    }
                    if (error != null) {
                        Spacer(Modifier.height(14.dp))
                        ErrorBox(error!!)
                    }

                    weather?.let { wx ->
                        Spacer(Modifier.height(22.dp))
                        WeatherSection(
                            title = "METAR",
                            product = wx.metar,
                            decoded = AviationDecoder.decodeMetar(wx.metar.raw),
                            explanation = AviationDecoder.explainMetar(wx.metar.raw),
                            prefs = prefs
                        )
                        WeatherSection(
                            title = "TAF",
                            product = wx.taf,
                            decoded = AviationDecoder.decodeTaf(wx.taf.raw),
                            explanation = AviationDecoder.explainTaf(wx.taf.raw),
                            prefs = prefs
                        )
                    }

                }
            }
        }
    }
}

@Composable
private fun WeatherSection(
    title: String,
    product: WeatherProduct,
    decoded: List<DecodedSection>,
    explanation: String,
    prefs: AppPreferences
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 30.dp)) {
        SectionHeader(
            title,
            if (product.available) "LIVE" else "NO DATA",
            if (product.available) YcGreen else YcMuted
        )

        if (prefs.showRaw) {
            Kicker("RAW")
            Text(
                product.raw ?: "No current report available.",
                color = if (product.raw == null) YcMuted else YcText.copy(alpha = 0.93f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(17.dp))
        }

        if (prefs.showDecoded) {
            Kicker("DECODED")
            if (decoded.isEmpty()) {
                Text("No decoded fields.", color = YcMuted, fontSize = 12.sp)
            } else {
                decoded.forEach { section ->
                    if (decoded.size > 1 || section.title != title) {
                        Text(
                            section.title,
                            color = YcCyanSoft,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(bottom = 4.dp, top = 4.dp)
                        )
                    }
                    section.rows.forEach { row ->
                        DataRow(row.first, row.second)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (prefs.showExplanation) {
            Kicker("EXPLANATION")
            Text(
                explanation,
                color = YcText.copy(alpha = 0.88f),
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun NotamSection(
    page: NotamPage?,
    prefs: AppPreferences
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        SectionHeader(
            "NOTAM",
            if (page == null) "LOADING" else (page.total.toString() + " VALID"),
            if (page == null) YcMuted else YcGreen
        )

        if (page == null) {
            Text("Loading active airport NOTAM…", color = YcMuted, fontSize = 12.sp)
            return@Column
        }

        if (page.items.isEmpty()) {
            Text(
                "No valid NOTAM returned by API v1 for this airport at the current UTC time.",
                color = YcMuted,
                fontSize = 12.sp
            )
        }

        page.items.forEachIndexed { index, notam ->
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        notam.ident,
                        color = YcCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        (notam.temporalState ?: notam.status ?: "").uppercase(),
                        color = YcMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                }
                Spacer(Modifier.height(8.dp))

                if (prefs.showRaw) {
                    Kicker("RAW")
                    Text(
                        notam.text ?: "NOTAM text unavailable.",
                        color = YcText.copy(alpha = 0.91f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(13.dp))
                }

                if (prefs.showDecoded) {
                    Kicker("DECODED")
                    notamDecodedRows(notam).forEach { row -> DataRow(row.first, row.second) }
                    Spacer(Modifier.height(12.dp))
                }

                if (prefs.showExplanation) {
                    Kicker("EXPLANATION")
                    Text(
                        AviationDecoder.explainNotam(notam),
                        color = YcText.copy(alpha = 0.86f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }

                if (index != page.items.lastIndex) {
                    Spacer(Modifier.height(15.dp))
                    HorizontalDivider(color = YcHairline)
                }
            }
        }
    }
}

private fun notamDecodedRows(n: Notam): List<Pair<String, String>> = buildList {
    n.selectionCode?.let { add("Q / CODE" to it) }
    n.classification?.let { add("CLASS" to it) }
    n.scope?.let { add("SCOPE" to it) }
    n.traffic?.let { add("TRAFFIC" to it) }
    n.effectiveStart?.let { add("FROM" to it) }
    add("TO" to (n.effectiveEndRaw ?: n.effectiveEnd ?: "OPEN"))
    if (n.minimumFl != null || n.maximumFl != null) {
        add("FL" to ((n.minimumFl ?: 0).toString() + " → " + (n.maximumFl?.toString() ?: "UNL")))
    } else if (n.lowerLimit != null || n.upperLimit != null) {
        add("LIMITS" to ((n.lowerLimit ?: "SFC") + " → " + (n.upperLimit ?: "UNL")))
    }
    n.schedule?.let { add("SCHEDULE" to it) }
}

@Composable
private fun BriefingScreen() {
    var apiOk by remember { mutableStateOf(false) }
    var from by rememberSaveable { mutableStateOf("LTAI") }
    var to by rememberSaveable { mutableStateOf("EDDB") }
    var etd by rememberSaveable { mutableStateOf(Instant.now().truncatedTo(ChronoUnit.MINUTES).toString()) }
    var fl by rememberSaveable { mutableStateOf("360") }
    var route by rememberSaveable { mutableStateOf("") }
    var requestToken by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var briefing by remember { mutableStateOf<Briefing?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { apiOk = YcApi.catalogOk() }

    LaunchedEffect(requestToken) {
        if (requestToken == 0) return@LaunchedEffect
        loading = true
        error = null

        val result = runCatching {
            YcApi.briefing(
                from.trim().uppercase(),
                to.trim().uppercase(),
                etd.trim(),
                fl.toIntOrNull() ?: 360,
                route.trim().uppercase()
            )
        }

        val data = result.getOrNull()
        if (data != null) {
            briefing = data
            apiOk = true
        } else {
            error = result.exceptionOrNull()?.message ?: "Pilot Briefing request failed."
        }

        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 38.dp)
    ) {
        item {
            TopBrand("Pilot Briefing", apiOk)
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(12.dp))
                Text("PILOT BRIEFING", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(5.dp))
                Text(
                    "Route, ETD and cruise-level aware data from the existing API v1 briefing service.",
                    color = YcMuted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(22.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BriefField("FROM", from, { from = it.uppercase() }, Modifier.weight(1f))
                    BriefField("TO", to, { to = it.uppercase() }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BriefField("ETD UTC", etd, { etd = it }, Modifier.weight(1.45f))
                    BriefField("FL", fl, { fl = it }, Modifier.weight(0.55f))
                }
                Spacer(Modifier.height(10.dp))
                Kicker("OFP / ROUTE · OPTIONAL")
                OutlinedTextField(
                    value = route,
                    onValueChange = { route = it.uppercase() },
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = YcText
                    ),
                    shape = RoundedCornerShape(6.dp)
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { requestToken++ },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YcCyan,
                        contentColor = YcVoid
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "GET PILOT BRIEF",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (loading) {
                    Spacer(Modifier.height(14.dp))
                    Text("Building briefing…", color = YcMuted)
                }
                if (error != null) {
                    Spacer(Modifier.height(14.dp))
                    ErrorBox(error!!)
                }
                Spacer(Modifier.height(22.dp))
            }
        }

        briefing?.let { data ->
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    BriefingMap(data)
                    Spacer(Modifier.height(18.dp))

                    BriefingTable(
                        "ROUTE",
                        data.routeMode.uppercase(),
                        listOf(
                            "DISTANCE" to (data.distanceNm.toInt().toString() + " NM"),
                            "ETD" to (data.etdUtc ?: etd),
                            "CRUISE" to ("FL" + data.cruiseFl),
                            "EET" to (data.estimatedEetMinutes.toString() + " MIN"),
                            "ETA" to (data.estimatedArrivalUtc ?: "—")
                        )
                    )

                    if (data.routeWarnings.isNotEmpty()) {
                        BriefingTable(
                            "ROUTE WARNINGS",
                            "PARSER",
                            data.routeWarnings.mapIndexed { i, value ->
                                ("WARNING " + (i + 1)) to value
                            }
                        )
                    }

                    BriefingTable(
                        "SIGMET",
                        "ROUTE RELEVANCE",
                        listOf(
                            "HAZARDS" to data.hazards.size.toString(),
                            "SOURCE" to data.source
                        )
                    )

                    data.hazards.take(20).forEach { hazard ->
                        Column(Modifier.padding(bottom = 14.dp)) {
                            Text(
                                hazard.hazard + " · " + (hazard.proximity ?: "ROUTE"),
                                color = YcAmber,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                listOfNotNull(
                                    hazard.distanceNm?.let { it.toInt().toString() + " NM" },
                                    hazard.timeRelation,
                                    hazard.cruiseRelation
                                ).joinToString(" · "),
                                color = YcMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                            hazard.raw?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    color = YcText.copy(alpha = 0.86f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(top = 5.dp)
                                )
                            }
                        }
                    }

                    BriefingTable(
                        "METAR / TAF",
                        "REPRESENTATIVE STATIONS",
                        listOf("STATIONS" to data.stations.size.toString())
                    )

                    data.stations.forEach { station ->
                        Column(Modifier.padding(bottom = 18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    station.icao,
                                    color = YcCyan,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    station.role.uppercase() +
                                        (station.flightCategory?.let { " · " + it } ?: ""),
                                    color = YcMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp
                                )
                            }
                            station.metarRaw?.let {
                                Text(
                                    "METAR  " + it,
                                    color = YcText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            station.tafRaw?.let {
                                Text(
                                    "TAF    " + it,
                                    color = YcText.copy(alpha = 0.82f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BriefField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier
) {
    Column(modifier) {
        Kicker(label)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = YcText
            ),
            shape = RoundedCornerShape(6.dp)
        )
    }
}

@Composable
private fun BriefingMap(data: Briefing) {
    var fullScreen by remember { mutableStateOf(false) }
    var layersOpen by remember { mutableStateOf(false) }
    var showRoute by remember { mutableStateOf(true) }
    var showStations by remember { mutableStateOf(true) }
    var showSigmet by remember { mutableStateOf(true) }
    var showCharts by remember { mutableStateOf(false) }
    var showWafs by remember { mutableStateOf(false) }
    var chartGeo by remember { mutableStateOf(emptyGeoJson()) }
    var wafsFrame by remember { mutableStateOf<WafsFrame?>(null) }
    var wafsProduct by remember { mutableStateOf("edr") }

    LaunchedEffect(fullScreen, showCharts, data.route) {
        chartGeo = if (fullScreen && showCharts) {
            runCatching {
                YcApi.chartViewport(
                    routeViewport(data.route),
                    setOf("airport", "navaid", "waypoint", "airway", "sid", "star", "airspace")
                )
            }.getOrDefault(emptyGeoJson())
        } else {
            emptyGeoJson()
        }
    }

    LaunchedEffect(fullScreen, showWafs, wafsProduct, data.cruiseFl, data.etdUtc, data.estimatedArrivalUtc) {
        if (!fullScreen || !showWafs) {
            wafsFrame = null
            return@LaunchedEffect
        }
        val startUtc = data.etdUtc?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
        val endUtc = data.estimatedArrivalUtc?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: startUtc
        val mid = startUtc.plusMillis(
            kotlin.math.max(0L, endUtc.toEpochMilli() - startUtc.toEpochMilli()) / 2L
        )
        wafsFrame = runCatching {
            YcApi.wafsFrame(
                wafsProduct,
                nearestSupportedWafsLevel(wafsProduct, data.cruiseFl),
                mid
            )
        }.getOrNull()
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(Color(0xFF071017), RoundedCornerShape(6.dp))
            .border(1.dp, YcHairline, RoundedCornerShape(6.dp))
    ) {
        NativeAviationMapV2(
            center = null,
            zoom = 4.0,
            interactive = false,
            routeGeoJson = routeGeoJson(data.route),
            briefingGeoJson = briefingOverlayGeoJson(data, true, true),
            routePoints = data.route,
            fitRoute = true,
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            color = Color(0xD90B1016),
            border = androidx.compose.foundation.BorderStroke(1.dp, YcHairline),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        ) {
            Text(
                "ROUTE · SIGMET · STATIONS",
                color = YcCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }

        Surface(
            onClick = { fullScreen = true },
            color = Color(0xE60B1016),
            border = androidx.compose.foundation.BorderStroke(1.dp, YcHairline),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.OpenInFull,
                    contentDescription = "Expand route map",
                    tint = YcCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "EXPAND",
                    color = YcText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp
                )
            }
        }
    }

    if (fullScreen) {
        Dialog(
            onDismissRequest = { fullScreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(Modifier.fillMaxSize().background(YcVoid)) {
                NativeAviationMapV2(
                    center = null,
                    zoom = 4.0,
                    interactive = true,
                    chartsGeoJson = chartGeo,
                    routeGeoJson = if (showRoute) routeGeoJson(data.route) else emptyGeoJson(),
                    briefingGeoJson = briefingOverlayGeoJson(data, showStations, showSigmet),
                    routePoints = data.route,
                    fitRoute = true,
                    wafsFrame = wafsFrame,
                    modifier = Modifier.fillMaxSize()
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { fullScreen = false },
                        color = Color(0xEC0B1016),
                        border = androidx.compose.foundation.BorderStroke(1.dp, YcHairline),
                        shape = RoundedCornerShape(7.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Close map",
                            tint = YcText,
                            modifier = Modifier.padding(10.dp).size(20.dp)
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    Surface(
                        onClick = { layersOpen = !layersOpen },
                        color = Color(0xEC0B1016),
                        border = androidx.compose.foundation.BorderStroke(1.dp, YcHairline),
                        shape = RoundedCornerShape(7.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Layers,
                                contentDescription = null,
                                tint = YcCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "LAYERS",
                                color = YcText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                if (layersOpen) {
                    Column(
                        Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 68.dp, end = 12.dp)
                            .width(270.dp)
                            .background(Color(0xF50B1016), RoundedCornerShape(9.dp))
                            .border(1.dp, YcHairline, RoundedCornerShape(9.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            "BRIEFING LAYERS",
                            color = YcText,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(9.dp))
                        LayerToggle("Route", showRoute) { showRoute = it }
                        LayerToggle("Stations", showStations) { showStations = it }
                        LayerToggle("SIGMET", showSigmet) { showSigmet = it }
                        LayerToggle("Charts", showCharts) { showCharts = it }
                        LayerToggle("WAFS", showWafs) { showWafs = it }

                        if (showWafs) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                listOf(
                                    "edr" to "EDR",
                                    "icing" to "ICE",
                                    "cbextent" to "CB",
                                    "wind" to "WIND"
                                ).forEach { item ->
                                    OptionChip(
                                        label = item.second,
                                        selected = wafsProduct == item.first,
                                        onClick = { wafsProduct = item.first }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun briefingOverlayGeoJson(
    data: Briefing,
    includeStations: Boolean,
    includeHazards: Boolean
): String {
    val features = JSONArray()

    if (includeStations) {
        data.stations.forEach { station ->
            val lat = station.lat ?: return@forEach
            val lon = station.lon ?: return@forEach
            features.put(
                JSONObject()
                    .put("type", "Feature")
                    .put(
                        "geometry",
                        JSONObject()
                            .put("type", "Point")
                            .put("coordinates", JSONArray().put(lon).put(lat))
                    )
                    .put(
                        "properties",
                        JSONObject()
                            .put("kind", "station")
                            .put("label", station.icao)
                            .put("role", station.role)
                    )
            )
        }
    }

    if (includeHazards) {
        data.hazards.forEach { hazard ->
            val raw = hazard.featureJson ?: return@forEach
            val feature = runCatching { JSONObject(raw) }.getOrNull() ?: return@forEach
            val props = feature.optJSONObject("properties") ?: JSONObject()
            props.put("kind", "hazard")
            props.put("hazard", hazard.hazard)
            feature.put("properties", props)
            features.put(feature)
        }
    }

    return JSONObject()
        .put("type", "FeatureCollection")
        .put("features", features)
        .toString()
}

@Composable
private fun BriefingTable(
    title: String,
    subtitle: String,
    rows: List<Pair<String, String>>
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        SectionHeader(title, subtitle, YcCyan)
        rows.forEach { row -> DataRow(row.first, row.second) }
    }
}

private enum class MapPanel {
    Charts,
    Wafs
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavMapScreen() {
    var apiOk by remember { mutableStateOf(false) }
    var centerAirport by remember { mutableStateOf(ltfmFallback) }
    var viewport by remember { mutableStateOf(airportViewport(ltfmFallback, 7)) }
    var layers by remember { mutableStateOf(MapLayers()) }
    var panel by remember { mutableStateOf<MapPanel?>(null) }

    var chartGeo by remember { mutableStateOf(emptyGeoJson()) }
    var notamGeo by remember { mutableStateOf(emptyGeoJson()) }
    var flightsGeo by remember { mutableStateOf(emptyGeoJson()) }
    var wafsFrame by remember { mutableStateOf<WafsFrame?>(null) }

    var chartsError by remember { mutableStateOf<String?>(null) }
    var notamError by remember { mutableStateOf<String?>(null) }
    var wafsError by remember { mutableStateOf<String?>(null) }
    var adsbError by remember { mutableStateOf<String?>(null) }

    var chartsLoading by remember { mutableStateOf(false) }
    var notamLoading by remember { mutableStateOf(false) }
    var wafsLoading by remember { mutableStateOf(false) }
    var adsbLoading by remember { mutableStateOf(false) }

    var refreshToken by remember { mutableIntStateOf(0) }
    var loadGeneration by remember { mutableIntStateOf(0) }
    var timeOffsetHours by remember { mutableFloatStateOf(0f) }
    var timelineExpanded by rememberSaveable { mutableStateOf(false) }
    var wafsProduct by remember { mutableStateOf("edr") }
    var wafsFl by remember { mutableIntStateOf(340) }

    LaunchedEffect(Unit) {
        apiOk = YcApi.catalogOk()
        runCatching { YcApi.airportDetail("LTFM") }.getOrNull()?.let {
            centerAirport = it
            viewport = airportViewport(it, 7)
        }
    }

    LaunchedEffect(viewport, layers, refreshToken, timeOffsetHours, wafsProduct, wafsFl) {
        val generation = ++loadGeneration
        val validTime = Instant.now().plus(timeOffsetHours.toLong(), ChronoUnit.HOURS)

        chartsLoading = layers.charts && layers.chartSet().isNotEmpty()
        notamLoading = layers.notam && viewport.zoom >= 5
        wafsLoading = layers.wafs
        adsbLoading = layers.adsb

        coroutineScope {
            val chartTask = async {
                if (layers.charts && layers.chartSet().isNotEmpty()) {
                    runCatching { YcApi.chartViewport(viewport, layers.chartSet()) }
                } else {
                    Result.success(emptyGeoJson())
                }
            }

            val notamTask = async {
                if (layers.notam && viewport.zoom >= 5) {
                    runCatching { YcApi.notamViewport(viewport, validTime) }
                } else {
                    Result.success(emptyGeoJson())
                }
            }

            val adsbTask = async {
                Result.success(flightsGeo)
            }

            val wafsTask = async {
                if (layers.wafs) {
                    runCatching { YcApi.wafsFrame(wafsProduct, wafsFl, validTime) }
                } else {
                    Result.success<WafsFrame?>(null)
                }
            }

            val chartResult = chartTask.await()
            val notamResult = notamTask.await()
            val adsbResult = adsbTask.await()
            val wafsResult = wafsTask.await()

            if (generation != loadGeneration) return@coroutineScope

            chartResult.fold(
                onSuccess = {
                    chartGeo = it
                    chartsError = null
                    apiOk = true
                },
                onFailure = {
                    chartsError = it.message ?: "CHARTS request failed."
                }
            )
            chartsLoading = false

            notamResult.fold(
                onSuccess = {
                    notamGeo = it
                    notamError = null
                    if (layers.notam) apiOk = true
                },
                onFailure = {
                    notamError = it.message ?: "NOTAM request failed."
                }
            )
            notamLoading = false

            adsbLoading = false

            wafsResult.fold(
                onSuccess = {
                    wafsFrame = it
                    wafsError = null
                    if (layers.wafs) apiOk = true
                },
                onFailure = {
                    wafsError = it.message ?: "WAFS request failed."
                }
            )
            wafsLoading = false
        }
    }

    LaunchedEffect(viewport, layers.adsb, refreshToken) {
        if (!layers.adsb) {
            flightsGeo = emptyGeoJson()
            adsbError = null
            adsbLoading = false
            return@LaunchedEffect
        }

        while (true) {
            if (viewport.zoom >= 4) {
                adsbLoading = flightsGeo == emptyGeoJson()
                runCatching { YcApi.adsb(viewport) }
                    .onSuccess {
                        flightsGeo = YcApi.flightsGeoJson(it.aircraft)
                        adsbError = null
                        apiOk = true
                    }
                    .onFailure {
                        adsbError = it.message ?: "ADS-B request failed."
                    }
                adsbLoading = false
            }
            delay(2000)
        }
    }

    Box(Modifier.fillMaxSize().background(YcVoid)) {
        NativeAviationMap(
            center = centerAirport,
            zoom = 7.0,
            interactive = true,
            chartsGeoJson = chartGeo,
            notamGeoJson = notamGeo,
            flightsGeoJson = flightsGeo,
            wafsFrame = wafsFrame,
            onViewportChanged = { viewport = it },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(YcVoid.copy(alpha = 0.80f), Color.Transparent)
                    )
                )
        ) {
            TopBrand("Aeronautical NavMap", apiOk)
        }

        Row(
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 60.dp, start = 12.dp, end = 12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MapControlChip(
                label = "CHARTS",
                active = layers.charts,
                error = chartsError,
                loading = chartsLoading,
                hasOptions = true,
                onClick = { panel = MapPanel.Charts }
            )
            MapControlChip(
                label = "NOTAM",
                active = layers.notam,
                error = notamError,
                loading = notamLoading,
                onClick = { layers = layers.copy(notam = !layers.notam) }
            )
            MapControlChip(
                label = "WAFS",
                active = layers.wafs,
                error = wafsError,
                loading = wafsLoading,
                hasOptions = true,
                onClick = { panel = MapPanel.Wafs }
            )
            MapControlChip(
                label = "ADS-B",
                active = layers.adsb,
                error = adsbError,
                loading = adsbLoading,
                onClick = { layers = layers.copy(adsb = !layers.adsb) }
            )
            Surface(
                onClick = { refreshToken++ },
                color = YcSurface.copy(alpha = 0.92f),
                border = androidx.compose.foundation.BorderStroke(1.dp, YcHairline),
                shape = RoundedCornerShape(5.dp)
            ) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "Refresh",
                    tint = YcCyan,
                    modifier = Modifier.padding(10.dp).size(18.dp)
                )
            }
        }

        if (!timelineExpanded) {
            Surface(
                onClick = { timelineExpanded = true },
                color = Color(0xF20B1016),
                border = androidx.compose.foundation.BorderStroke(1.dp, YcHairline),
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (timeOffsetHours == 0f) "NOW"
                        else (if (timeOffsetHours > 0) "+" else "") + timeOffsetHours.toInt() + "h",
                        color = YcCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "TIMELINE",
                        color = YcMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                    Icon(
                        Icons.Outlined.KeyboardArrowUp,
                        contentDescription = "Open timeline",
                        tint = YcMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .background(Color(0xF20B1016), RoundedCornerShape(9.dp))
                    .border(1.dp, YcHairline, RoundedCornerShape(9.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (timeOffsetHours == 0f) "NOW"
                        else (if (timeOffsetHours > 0) "+" else "") + timeOffsetHours.toInt() + "h",
                        color = YcCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "UTC TIMELINE",
                        color = YcMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                    Surface(
                        onClick = { timelineExpanded = false },
                        color = Color.Transparent,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "Close timeline",
                            tint = YcMuted,
                            modifier = Modifier.padding(4.dp).size(18.dp)
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = {
                            timeOffsetHours = (timeOffsetHours - 1f).coerceAtLeast(-24f)
                        },
                        color = YcSurfaceHigh,
                        border = androidx.compose.foundation.BorderStroke(1.dp, YcHairline),
                        shape = RoundedCornerShape(5.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Remove, null, tint = YcCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("-1h", color = YcText, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    Surface(
                        onClick = { timeOffsetHours = 0f },
                        color = YcSurfaceHigh,
                        border = androidx.compose.foundation.BorderStroke(1.dp, YcHairline),
                        shape = RoundedCornerShape(5.dp)
                    ) {
                        Text(
                            "NOW",
                            color = YcCyanSoft,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Surface(
                        onClick = {
                            timeOffsetHours = (timeOffsetHours + 1f).coerceAtMost(24f)
                        },
                        color = YcSurfaceHigh,
                        border = androidx.compose.foundation.BorderStroke(1.dp, YcHairline),
                        shape = RoundedCornerShape(5.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Add, null, tint = YcCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("+1h", color = YcText, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                        }
                    }
                }

                Slider(
                    value = timeOffsetHours,
                    onValueChange = { timeOffsetHours = it },
                    valueRange = -24f..24f,
                    steps = 47
                )

                LayerErrorLine("CHARTS", chartsError)
                if (layers.notam) LayerErrorLine("NOTAM", notamError)
                if (layers.wafs) LayerErrorLine("WAFS", wafsError)
                if (layers.adsb) LayerErrorLine("ADS-B", adsbError)
            }
        }
    }

    if (panel == MapPanel.Charts) {
        ModalBottomSheet(
            onDismissRequest = { panel = null },
            containerColor = YcSurface,
            contentColor = YcText
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                item {
                    Text("CHARTS", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Master switch plus individual aeronautical chart layers.",
                        color = YcMuted,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(16.dp))

                    LayerToggle("Charts", layers.charts) {
                        layers = layers.copy(charts = it)
                    }
                    HorizontalDivider(color = YcHairline)
                    LayerToggle("Airports", layers.airports) { layers = layers.copy(airports = it) }
                    LayerToggle("Navaids", layers.navaids) { layers = layers.copy(navaids = it) }
                    LayerToggle("Waypoints", layers.waypoints) { layers = layers.copy(waypoints = it) }
                    LayerToggle("Airways", layers.airways) { layers = layers.copy(airways = it) }
                    LayerToggle("SID", layers.sid) { layers = layers.copy(sid = it) }
                    LayerToggle("STAR", layers.star) { layers = layers.copy(star = it) }
                    LayerToggle("Airspace", layers.airspace) { layers = layers.copy(airspace = it) }

                    if (chartsError != null) {
                        Spacer(Modifier.height(12.dp))
                        ErrorBox("CHARTS · " + chartsError!!)
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }

    if (panel == MapPanel.Wafs) {
        ModalBottomSheet(
            onDismissRequest = { panel = null },
            containerColor = YcSurface,
            contentColor = YcText
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                item {
                    Text("WAFS", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Public WAFS raster layer and product controls.",
                        color = YcMuted,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(16.dp))

                    LayerToggle("WAFS", layers.wafs) {
                        layers = layers.copy(wafs = it)
                    }

                    Spacer(Modifier.height(14.dp))
                    Kicker("PRODUCT")
                    Row(
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "edr" to "EDR",
                            "icing" to "ICING",
                            "cbextent" to "CB EXTENT",
                            "cbtop" to "CB TOPS",
                            "wind" to "WIND"
                        ).forEach { option ->
                            OptionChip(
                                label = option.second,
                                selected = wafsProduct == option.first,
                                onClick = {
                                    wafsProduct = option.first
                                    wafsFl = nearestSupportedWafsLevel(option.first, wafsFl)
                                }
                            )
                        }
                    }

                    if (wafsProduct != "cbextent" && wafsProduct != "cbtop") {
                        Spacer(Modifier.height(8.dp))
                        Kicker("FLIGHT LEVEL")
                        Text(
                            "FL" + wafsFl,
                            color = YcText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                        Slider(
                            value = wafsFl.toFloat(),
                            onValueChange = {
                                wafsFl = nearestSupportedWafsLevel(wafsProduct, it.toInt())
                            },
                            valueRange = 60f..450f
                        )
                    }

                    if (wafsError != null) {
                        Spacer(Modifier.height(12.dp))
                        ErrorBox("WAFS · " + wafsError!!)
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun MapControlChip(
    label: String,
    active: Boolean,
    error: String?,
    loading: Boolean,
    hasOptions: Boolean = false,
    onClick: () -> Unit
) {
    val borderColor = when {
        error != null -> YcRed
        active -> YcCyan.copy(alpha = 0.70f)
        else -> YcHairline
    }
    val textColor = when {
        error != null -> YcRed
        active -> YcCyanSoft
        else -> YcMuted
    }

    Surface(
        onClick = onClick,
        color = if (active) YcCyan.copy(alpha = 0.12f) else YcSurface.copy(alpha = 0.92f),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(5.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label + if (hasOptions) " ▾" else "",
                color = textColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
            if (loading) {
                Spacer(Modifier.width(5.dp))
                Text("…", color = YcCyan, fontSize = 10.sp)
            } else if (error != null) {
                Spacer(Modifier.width(5.dp))
                Text("!", color = YcRed, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) YcCyan.copy(alpha = 0.14f) else YcSurfaceHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) YcCyan else YcHairline
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            label,
            color = if (selected) YcCyanSoft else YcMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun LayerErrorLine(
    label: String,
    error: String?
) {
    if (error == null) return
    Text(
        label + " · " + error,
        color = YcRed,
        fontFamily = FontFamily.Monospace,
        fontSize = 8.sp,
        lineHeight = 12.sp,
        maxLines = 2,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun SettingsScreen(
    prefs: AppPreferences,
    onChange: (AppPreferences) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 38.dp)
    ) {
        item {
            TopBrand("Settings", true)
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(16.dp))
                Text("SETTINGS", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(5.dp))
                Text(
                    "Control how airport reports are presented.",
                    color = YcMuted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(24.dp))

                Kicker("AIRPORT RESULTS")
                SettingToggle(
                    "Show RAW",
                    "Original METAR, TAF and NOTAM source text.",
                    prefs.showRaw
                ) { onChange(prefs.copy(showRaw = it)) }

                SettingToggle(
                    "Show decoded",
                    "Structured aviation fields parsed on device.",
                    prefs.showDecoded
                ) { onChange(prefs.copy(showDecoded = it)) }

                SettingToggle(
                    "Show explanation",
                    "Human-readable parsed summary below each product.",
                    prefs.showExplanation
                ) { onChange(prefs.copy(showExplanation = it)) }

                Spacer(Modifier.height(26.dp))
                HorizontalDivider(color = YcHairline)
                Spacer(Modifier.height(14.dp))
                Text(
                    "Data access: /main/api/v1/*",
                    color = YcMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun LayerToggle(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = YcText, fontSize = 13.sp, modifier = Modifier.weight(1f))
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
}

@Composable
private fun SettingToggle(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
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

@Composable
private fun SectionHeader(
    title: String,
    status: String,
    statusColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            color = YcText,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.width(9.dp))
        Text(
            status,
            color = statusColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp
        )
    }
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = YcHairline)
    Spacer(Modifier.height(13.dp))
}

@Composable
private fun DataRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label.uppercase(),
            color = YcMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            modifier = Modifier.weight(0.39f)
        )
        Text(
            value,
            color = YcText,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.weight(0.61f)
        )
    }
}

@Composable
private fun Kicker(text: String) {
    Text(
        text,
        color = YcCyan,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun ErrorBox(message: String) {
    Surface(
        color = YcRed.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, YcRed.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(5.dp)
    ) {
        Text(
            message,
            color = YcRed,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.padding(10.dp)
        )
    }
}

@Composable
private fun MapChip(label: String, active: Boolean) {
    Surface(
        color = if (active) YcCyan.copy(alpha = 0.12f) else YcSurface.copy(alpha = 0.92f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (active) YcCyan.copy(alpha = 0.6f) else YcHairline
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            label,
            color = if (active) YcCyanSoft else YcMuted,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
        )
    }
}

private fun routeGeoJson(points: List<RoutePoint>): String {
    if (points.size < 2) return emptyGeoJson()
    val coordinates = org.json.JSONArray()
    points.forEach { p ->
        coordinates.put(org.json.JSONArray().put(p.lon).put(p.lat))
    }
    val geometry = org.json.JSONObject()
        .put("type", "LineString")
        .put("coordinates", coordinates)
    val feature = org.json.JSONObject()
        .put("type", "Feature")
        .put("geometry", geometry)
        .put("properties", org.json.JSONObject().put("layer", "route"))
    return org.json.JSONObject()
        .put("type", "FeatureCollection")
        .put("features", org.json.JSONArray().put(feature))
        .toString()
}

private fun routeViewport(points: List<RoutePoint>): Viewport {
    if (points.isEmpty()) return airportViewport(ltfmFallback, 7)
    val minLat = points.minOf { it.lat }
    val maxLat = points.maxOf { it.lat }
    val minLon = points.minOf { it.lon }
    val maxLon = points.maxOf { it.lon }
    val latPad = max(0.35, (maxLat - minLat) * 0.12)
    val lonPad = max(0.45, (maxLon - minLon) * 0.12)
    return Viewport(
        west = (minLon - lonPad).coerceAtLeast(-180.0),
        south = (minLat - latPad).coerceAtLeast(-85.0),
        east = (maxLon + lonPad).coerceAtMost(180.0),
        north = (maxLat + latPad).coerceAtMost(85.0),
        zoom = 5
    )
}

private fun airportViewport(airport: Airport, zoom: Int): Viewport =
    Viewport(
        west = airport.lon - 1.35,
        south = airport.lat - 1.0,
        east = airport.lon + 1.35,
        north = airport.lat + 1.0,
        zoom = zoom
    )

private fun emptyGeoJson(): String =
    """{"type":"FeatureCollection","features":[]}"""

private fun midpointLongitude(west: Double, east: Double): Double =
    if (west <= east) (west + east) / 2.0
    else {
        val shiftedEast = east + 360.0
        val value = (west + shiftedEast) / 2.0
        if (value > 180.0) value - 360.0 else value
    }

private fun viewportRadiusNm(v: Viewport): Int {
    val lat = (v.north + v.south) / 2.0
    val lon = midpointLongitude(v.west, v.east)
    val distance = haversineNm(lat, lon, v.north, v.east)
    return ceil(distance).toInt().coerceIn(10, 235)
}

private fun haversineNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earth = 3440.065
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dp = Math.toRadians(lat2 - lat1)
    val dl = Math.toRadians(lon2 - lon1)
    val a = sin(dp / 2) * sin(dp / 2) +
        cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return 2 * earth * asin(min(1.0, sqrt(a)))
}

private fun nearestSupportedWafsLevel(product: String, requested: Int): Int {
    val levels = when (product) {
        "edr" -> listOf(140, 180, 240, 270, 300, 340, 390, 450)
        "icing" -> listOf(60, 100, 140, 180, 240, 300)
        "wind" -> listOf(100, 140, 180, 240, 270, 300, 340, 390, 450)
        else -> return requested.coerceIn(50, 600)
    }
    return levels.minByOrNull { kotlin.math.abs(it - requested) } ?: levels.first()
}
