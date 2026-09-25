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
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    Briefing("Pilot Briefing", Icons.Outlined.FlightTakeoff),
    NavMap("NavMap", Icons.Outlined.Map),
    Settings("Settings", Icons.Outlined.Settings)
}

data class AppPreferences(
    val showRaw: Boolean = true,
    val showDecoded: Boolean = true,
    val showExplanation: Boolean = true
)

private data class MapLayers(
    val charts: Boolean = true,
    val airports: Boolean = true,
    val navaids: Boolean = true,
    val waypoints: Boolean = false,
    val airways: Boolean = true,
    val sid: Boolean = false,
    val star: Boolean = false,
    val airspace: Boolean = true,
    val notam: Boolean = false,
    val wafs: Boolean = false,
    val adsb: Boolean = false
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
    var searchGeneration by remember { mutableIntStateOf(0) }
    var showResults by rememberSaveable { mutableStateOf(false) }
    var weather by remember { mutableStateOf<WeatherBundle?>(null) }
    var notams by remember { mutableStateOf<NotamPage?>(null) }
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

    LaunchedEffect(query) {
        val typed = query.trim()
        val normalized = YcApi.normalizeAirportQuery(typed)
        if (normalized.length < 2 || normalized.equals(activeAirport.icao, true)) {
            matches = emptyList()
            searchBusy = false
            return@LaunchedEffect
        }

        val generation = ++searchGeneration
        searchBusy = true
        delay(220)

        val result = runCatching { YcApi.airportSearch(normalized, 8) }
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
        val result = runCatching {
            coroutineScope {
                val chartTask = async {
                    YcApi.chartViewport(
                        viewport,
                        setOf("airport", "navaid", "airway", "airspace")
                    )
                }
                if (showResults) {
                    val weatherTask = async { YcApi.weather(activeAirport.icao) }
                    val notamTask = async { YcApi.notamsForAirport(activeAirport.icao, 200) }
                    Triple(chartTask.await(), weatherTask.await(), notamTask.await())
                } else {
                    Triple(chartTask.await(), null, null)
                }
            }
        }
        result.onSuccess { payload ->
            charts = payload.first
            if (showResults) {
                weather = payload.second
                notams = payload.third
                apiOk = true
            }
        }.onFailure {
            error = it.message ?: "YulCaribe API request failed."
        }
        loading = false
    }

    fun selectAirport(airport: Airport) {
        activeAirport = airport
        query = airport.icao
        matches = emptyList()
        weather = null
        notams = null
        showResults = true
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
                        .blur(4.dp)
                        .alpha(0.72f)
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

                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                query = it.uppercase()
                                if (!it.equals(activeAirport.icao, true)) showResults = false
                            },
                            modifier = Modifier.fillMaxWidth(),
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

                        AnimatedVisibility(matches.isNotEmpty() && !showResults) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .background(Color(0xF20B1016), RoundedCornerShape(7.dp))
                                    .border(1.dp, YcHairline, RoundedCornerShape(7.dp))
                            ) {
                                matches.forEachIndexed { index, airport ->
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
                                            airport.city?.let {
                                                Text(it, color = YcMuted, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                    if (index != matches.lastIndex) HorizontalDivider(color = YcHairline)
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

                    NotamSection(notams, prefs)
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
    var briefingCharts by remember { mutableStateOf(emptyGeoJson()) }
    var briefingMapError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { apiOk = YcApi.catalogOk() }

    LaunchedEffect(requestToken) {
        if (requestToken == 0) return@LaunchedEffect
        loading = true
        error = null
        briefingMapError = null

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

            val mapResult = runCatching {
                YcApi.chartViewport(
                    routeViewport(data.route),
                    setOf("airport", "airway")
                )
            }
            briefingCharts = mapResult.getOrDefault(emptyGeoJson())
            briefingMapError = mapResult.exceptionOrNull()?.message
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
                    BriefingMap(
                        points = data.route,
                        chartsGeoJson = briefingCharts,
                        error = briefingMapError
                    )
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
private fun BriefingMap(
    points: List<RoutePoint>,
    chartsGeoJson: String,
    error: String?
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(Color(0xFF071017), RoundedCornerShape(6.dp))
            .border(1.dp, YcHairline, RoundedCornerShape(6.dp))
    ) {
        NativeAviationMap(
            center = null,
            zoom = 4.0,
            interactive = true,
            chartsGeoJson = chartsGeoJson,
            notamGeoJson = emptyGeoJson(),
            flightsGeoJson = emptyGeoJson(),
            routeGeoJson = routeGeoJson(points),
            routePoints = points,
            wafsFrame = null,
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
                "LIVE ROUTE MAP",
                color = YcCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }

        if (error != null) {
            Surface(
                color = YcRed.copy(alpha = 0.88f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Text(
                    "CHARTS · " + error,
                    color = YcText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavMapScreen() {
    var apiOk by remember { mutableStateOf(false) }
    var centerAirport by remember { mutableStateOf(ltfmFallback) }
    var viewport by remember { mutableStateOf(airportViewport(ltfmFallback, 7)) }
    var layers by remember { mutableStateOf(MapLayers()) }
    var showLayers by remember { mutableStateOf(false) }
    var chartGeo by remember { mutableStateOf(emptyGeoJson()) }
    var notamGeo by remember { mutableStateOf(emptyGeoJson()) }
    var flightsGeo by remember { mutableStateOf(emptyGeoJson()) }
    var wafsFrame by remember { mutableStateOf<WafsFrame?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var timeOffsetHours by remember { mutableFloatStateOf(0f) }
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
        loading = true
        error = null
        val validTime = Instant.now().plus(timeOffsetHours.toLong(), ChronoUnit.HOURS)
        val result = runCatching {
            coroutineScope {
                val chartTask = async {
                    YcApi.chartViewport(viewport, layers.chartSet())
                }
                val notamTask = async {
                    if (layers.notam && viewport.zoom >= 5) {
                        YcApi.notamViewport(viewport, validTime)
                    } else {
                        emptyGeoJson()
                    }
                }
                val flightTask = async {
                    if (layers.adsb) {
                        val lat = (viewport.north + viewport.south) / 2.0
                        val lon = midpointLongitude(viewport.west, viewport.east)
                        YcApi.flightsGeoJson(
                            YcApi.flights(lat, lon, viewportRadiusNm(viewport))
                        )
                    } else {
                        emptyGeoJson()
                    }
                }
                val wafsTask = async {
                    if (layers.wafs) {
                        YcApi.wafsFrame(wafsProduct, wafsFl, validTime)
                    } else null
                }
                listOf(
                    chartTask.await(),
                    notamTask.await(),
                    flightTask.await()
                ) to wafsTask.await()
            }
        }

        result.onSuccess { payload ->
            chartGeo = payload.first[0]
            notamGeo = payload.first[1]
            flightsGeo = payload.first[2]
            wafsFrame = payload.second
            apiOk = true
        }.onFailure {
            error = it.message ?: "NavMap API request failed."
        }
        loading = false
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
                .statusBarsPadding()
                .padding(top = 58.dp, start = 14.dp, end = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = { showLayers = true },
                color = YcSurface.copy(alpha = 0.94f),
                border = androidx.compose.foundation.BorderStroke(1.dp, YcHairline),
                shape = RoundedCornerShape(7.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Layers, null, tint = YcCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("LAYERS", color = YcText, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                onClick = { refreshToken++ },
                color = YcSurface.copy(alpha = 0.94f),
                border = androidx.compose.foundation.BorderStroke(1.dp, YcHairline),
                shape = RoundedCornerShape(7.dp)
            ) {
                Icon(
                    Icons.Outlined.Refresh,
                    null,
                    tint = YcCyan,
                    modifier = Modifier.padding(10.dp).size(18.dp)
                )
            }
        }

        Row(
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 108.dp, start = 14.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MapChip("CHARTS", layers.chartSet().isNotEmpty())
            MapChip("NOTAM", layers.notam)
            MapChip("WAFS", layers.wafs)
            MapChip("ADS-B", layers.adsb)
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .background(Color(0xF20B1016), RoundedCornerShape(7.dp))
                .border(1.dp, YcHairline, RoundedCornerShape(7.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp)
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
                if (loading) {
                    Text("LOADING", color = YcMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                }
            }
            Slider(
                value = timeOffsetHours,
                onValueChange = { timeOffsetHours = it },
                valueRange = -24f..24f,
                steps = 47
            )
            if (error != null) ErrorBox(error!!)
        }
    }

    if (showLayers) {
        ModalBottomSheet(
            onDismissRequest = { showLayers = false },
            containerColor = YcSurface,
            contentColor = YcText
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                item {
                    Text("MAP LAYERS", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "One native MapLibre map using the current API v1 endpoints.",
                        color = YcMuted,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(18.dp))

                    Kicker("CHARTS")
                    LayerToggle("Airports", layers.airports) { layers = layers.copy(airports = it) }
                    LayerToggle("Navaids", layers.navaids) { layers = layers.copy(navaids = it) }
                    LayerToggle("Waypoints", layers.waypoints) { layers = layers.copy(waypoints = it) }
                    LayerToggle("Airways", layers.airways) { layers = layers.copy(airways = it) }
                    LayerToggle("SID", layers.sid) { layers = layers.copy(sid = it) }
                    LayerToggle("STAR", layers.star) { layers = layers.copy(star = it) }
                    LayerToggle("Airspace", layers.airspace) { layers = layers.copy(airspace = it) }

                    Spacer(Modifier.height(14.dp))
                    Kicker("OPERATIONAL")
                    LayerToggle("NOTAM", layers.notam) { layers = layers.copy(notam = it) }
                    LayerToggle("WAFS", layers.wafs) { layers = layers.copy(wafs = it) }
                    LayerToggle("ADS-B traffic", layers.adsb) { layers = layers.copy(adsb = it) }

                    if (layers.wafs) {
                        Spacer(Modifier.height(14.dp))
                        Kicker("WAFS")
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
                                Surface(
                                    onClick = {
                                        wafsProduct = option.first
                                        wafsFl = nearestSupportedWafsLevel(option.first, wafsFl)
                                    },
                                    color = if (wafsProduct == option.first) {
                                        YcCyan.copy(alpha = 0.14f)
                                    } else {
                                        YcSurfaceHigh
                                    },
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (wafsProduct == option.first) YcCyan else YcHairline
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        option.second,
                                        color = if (wafsProduct == option.first) YcCyanSoft else YcMuted,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                        if (wafsProduct != "cbextent" && wafsProduct != "cbtop") {
                            Text(
                                "FL" + wafsFl,
                                color = YcText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                            Slider(
                                value = wafsFl.toFloat(),
                                onValueChange = {
                                    wafsFl = nearestSupportedWafsLevel(wafsProduct, it.toInt())
                                },
                                valueRange = 60f..450f
                            )
                        }
                    }

                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
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
