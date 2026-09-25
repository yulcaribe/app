package com.yulcaribe.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
import kotlin.math.cos

private val LiveVoid = Color(0xFF05070A)
private val LiveSurface = Color(0xFF0B1016)
private val LiveHigh = Color(0xFF111821)
private val LiveLine = Color(0xFF1A2630)
private val LiveCyan = Color(0xFF00D9FF)
private val LiveCyanSoft = Color(0xFF7AEAFF)
private val LiveText = Color(0xFFF5F7FA)
private val LiveMuted = Color(0xFF8E9AA7)
private val LiveGreen = Color(0xFF4BE28C)
private val LiveAmber = Color(0xFFFFB84D)
private val LiveRed = Color(0xFFFF5F6D)

private enum class LiveDestination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Briefing("Pilot Briefing", Icons.Outlined.FlightTakeoff),
    NavMap("NavMap", Icons.Outlined.Map),
    Settings("Settings", Icons.Outlined.Settings)
}

private data class LivePrefs(
    val raw: Boolean = true,
    val decoded: Boolean = true,
    val explanation: Boolean = true
)

private data class LiveLayers(
    val airport: Boolean = true,
    val navaid: Boolean = true,
    val waypoint: Boolean = false,
    val airway: Boolean = true,
    val sid: Boolean = false,
    val star: Boolean = false,
    val airspace: Boolean = true,
    val notam: Boolean = false,
    val adsb: Boolean = false
) {
    fun serverLayers(): Set<String> = buildSet {
        if (airport) add("airport")
        if (navaid) add("navaid")
        if (waypoint) add("waypoint")
        if (airway) add("airway")
        if (sid) add("sid")
        if (star) add("star")
        if (airspace) add("airspace")
        if (notam) add("notam")
    }
}

private val ltfmFallback = AirportResult(
    icao = "LTFM",
    name = "Istanbul Airport",
    lat = 41.2753,
    lon = 28.7519
)

@Composable
fun LiveYulCaribeApp() {
    var destination by rememberSaveable { mutableStateOf(LiveDestination.Home) }
    var prefs by remember { mutableStateOf(LivePrefs()) }

    Scaffold(
        containerColor = LiveVoid,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xF20A0E13),
                tonalElevation = 0.dp,
                modifier = Modifier.border(1.dp, LiveLine)
            ) {
                LiveDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = {
                            Text(
                                item.label,
                                fontSize = if (item == LiveDestination.Briefing) 9.sp else 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = LiveCyan,
                            selectedTextColor = LiveText,
                            indicatorColor = LiveCyan.copy(alpha = 0.12f),
                            unselectedIconColor = LiveMuted,
                            unselectedTextColor = LiveMuted
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
                LiveDestination.Home -> LiveHomeScreen(prefs)
                LiveDestination.Briefing -> LiveBriefingScreen()
                LiveDestination.NavMap -> LiveNavMapScreen()
                LiveDestination.Settings -> LiveSettingsScreen(prefs) { prefs = it }
            }
        }
    }
}

@Composable
private fun LiveBrandHeader(
    title: String,
    connected: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "YULCARIBE",
                color = LiveText,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                letterSpacing = 2.1.sp
            )
            Text(
                title.uppercase(),
                color = LiveMuted,
                fontSize = 9.sp,
                letterSpacing = 1.0.sp
            )
        }
        Text(
            if (connected) "● LIVE API" else "○ CONNECTING",
            color = if (connected) LiveGreen else LiveMuted,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun LiveHomeScreen(prefs: LivePrefs) {
    val focus = LocalFocusManager.current
    var activeAirport by remember { mutableStateOf(ltfmFallback) }
    var query by rememberSaveable { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<AirportResult>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    var showResults by rememberSaveable { mutableStateOf(false) }

    var weather by remember { mutableStateOf<WeatherPayload?>(null) }
    var notams by remember { mutableStateOf<List<NotamItem>>(emptyList()) }
    var nav by remember { mutableStateOf<NavViewport?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        runCatching { YulCaribeApi.airportExact("LTFM") }
            .getOrNull()
            ?.let { activeAirport = it }
    }

    LaunchedEffect(activeAirport.icao, refresh) {
        loading = true
        error = null
        val result = runCatching {
            coroutineScope {
                val navTask = async {
                    YulCaribeApi.navViewport(
                        activeAirport.lat,
                        activeAirport.lon,
                        zoom = 7,
                        layers = setOf("airport", "navaid", "airway", "airspace")
                    )
                }
                if (showResults) {
                    val wxTask = async { YulCaribeApi.weather(activeAirport.icao) }
                    val notamTask = async { YulCaribeApi.airportNotams(activeAirport.icao) }
                    Triple(navTask.await(), wxTask.await(), notamTask.await())
                } else {
                    Triple(navTask.await(), null, emptyList())
                }
            }
        }
        result.onSuccess {
            nav = it.first
            if (showResults) {
                weather = it.second
                notams = it.third
            }
        }.onFailure {
            error = it.message ?: "API request failed."
        }
        loading = false
    }

    LaunchedEffect(query) {
        if (query.trim().length < 2 || query.equals(activeAirport.icao, true)) {
            matches = emptyList()
            searchLoading = false
            return@LaunchedEffect
        }
        searchLoading = true
        delay(260)
        matches = runCatching { YulCaribeApi.searchAirports(query.trim()) }
            .getOrDefault(emptyList())
            .take(6)
        searchLoading = false
    }

    fun selectAirport(airport: AirportResult) {
        activeAirport = airport
        query = airport.icao
        showResults = true
        weather = null
        notams = emptyList()
        matches = emptyList()
        focus.clearFocus()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (showResults) 330.dp else 520.dp)
                    .animateContentSize()
                    .background(LiveVoid)
            ) {
                LiveNavCanvas(
                    nav = nav,
                    center = activeAirport,
                    aircraft = emptyList(),
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(5.dp)
                        .alpha(0.54f)
                )

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    LiveVoid.copy(alpha = 0.18f),
                                    LiveVoid.copy(alpha = 0.36f),
                                    LiveVoid
                                )
                            )
                        )
                )

                Column(Modifier.fillMaxSize()) {
                    LiveBrandHeader(
                        title = "Airport Intelligence",
                        connected = nav != null && error == null
                    )

                    Spacer(Modifier.weight(1f))

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Text(
                            if (showResults) "ACTIVE AIRPORT" else "LIVE CONTEXT",
                            color = LiveCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            letterSpacing = 1.3.sp
                        )
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                activeAirport.icao,
                                color = LiveText,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 31.sp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                activeAirport.name ?: "",
                                color = LiveMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
                            placeholder = { Text("Airport ICAO or name", color = LiveMuted) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Search, null, tint = LiveCyan)
                            },
                            trailingIcon = {
                                if (searchLoading) {
                                    Text("…", color = LiveCyan, fontSize = 18.sp)
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { matches.firstOrNull()?.let(::selectAirport) }
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        AnimatedVisibility(visible = matches.isNotEmpty() && !showResults) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .background(Color(0xF20B1016), RoundedCornerShape(7.dp))
                                    .border(1.dp, LiveLine, RoundedCornerShape(7.dp))
                            ) {
                                matches.forEachIndexed { index, airport ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { selectAirport(airport) }
                                            .padding(horizontal = 14.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            airport.icao,
                                            color = LiveCyan,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(62.dp)
                                        )
                                        Text(
                                            airport.name ?: "Airport",
                                            color = LiveText,
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (index != matches.lastIndex) HorizontalDivider(color = LiveLine)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!showResults) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                    TechnicalKicker("SEARCH AIRPORT")
                    Text(
                        "Search an airport. Its real YulCaribe NavMap geometry becomes the background; METAR, TAF and NOTAM continue below on the same screen.",
                        color = LiveMuted,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                    if (error != null) {
                        Spacer(Modifier.height(14.dp))
                        ErrorLine(error!!)
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
                                activeAirport.icao,
                                color = LiveText,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                            Text(
                                activeAirport.name ?: "Airport",
                                color = LiveMuted,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = { refresh++ }) {
                            Icon(Icons.Outlined.Refresh, null, tint = LiveCyan)
                            Spacer(Modifier.width(5.dp))
                            Text("REFRESH", color = LiveCyan, fontSize = 10.sp)
                        }
                    }

                    if (loading && weather == null) {
                        Spacer(Modifier.height(18.dp))
                        Text("Loading live YulCaribe data…", color = LiveMuted)
                    }

                    if (error != null) {
                        Spacer(Modifier.height(16.dp))
                        ErrorLine(error!!)
                    }

                    weather?.let { wx ->
                        Spacer(Modifier.height(20.dp))
                        LiveWeatherSection(
                            title = "METAR",
                            raw = wx.metarRaw,
                            decoded = decodeMetar(wx.metarRaw),
                            explanation = explainMetar(wx.metarRaw),
                            prefs = prefs,
                            source = wx.source
                        )

                        LiveWeatherSection(
                            title = "TAF",
                            raw = wx.tafRaw,
                            decoded = decodeTaf(wx.tafRaw),
                            explanation = explainTaf(wx.tafRaw),
                            prefs = prefs,
                            source = wx.source
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    NotamSection(notams, prefs)
                }
            }
        }
    }
}

@Composable
private fun LiveWeatherSection(
    title: String,
    raw: String?,
    decoded: List<Pair<String, String>>,
    explanation: String,
    prefs: LivePrefs,
    source: String
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 30.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = LiveText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "LIVE",
                color = LiveGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                source,
                color = LiveMuted,
                fontSize = 8.sp,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = LiveLine)
        Spacer(Modifier.height(13.dp))

        if (prefs.raw) {
            TechnicalKicker("RAW")
            Text(
                raw ?: "No current report available.",
                color = LiveText.copy(alpha = if (raw == null) 0.55f else 0.92f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(17.dp))
        }

        if (prefs.decoded) {
            TechnicalKicker("DECODED")
            if (decoded.isEmpty()) {
                Text("No decodable fields.", color = LiveMuted, fontSize = 12.sp)
            } else {
                decoded.forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            row.first,
                            color = LiveMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.weight(0.40f)
                        )
                        Text(
                            row.second,
                            color = LiveText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(0.60f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (prefs.explanation) {
            TechnicalKicker("EXPLANATION")
            Text(
                explanation,
                color = LiveText.copy(alpha = 0.88f),
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun NotamSection(notams: List<NotamItem>, prefs: LivePrefs) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "NOTAM",
                color = LiveText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(Modifier.width(10.dp))
            Text(
                notams.size.toString() + " ACTIVE",
                color = LiveGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = LiveLine)
        Spacer(Modifier.height(12.dp))

        if (notams.isEmpty()) {
            Text(
                "No active airport NOTAM returned for this ICAO at the current UTC time.",
                color = LiveMuted,
                fontSize = 12.sp
            )
        }

        notams.forEachIndexed { index, n ->
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(
                    n.ident.ifBlank { n.id },
                    color = LiveCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))

                if (prefs.raw) {
                    TechnicalKicker("RAW")
                    Text(
                        n.text ?: "Raw NOTAM text unavailable.",
                        color = LiveText.copy(alpha = 0.90f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(13.dp))
                }

                if (prefs.decoded) {
                    TechnicalKicker("DECODED")
                    notamDecode(n).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(
                                row.first,
                                color = LiveMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                modifier = Modifier.weight(0.38f)
                            )
                            Text(
                                row.second,
                                color = LiveText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                modifier = Modifier.weight(0.62f)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (prefs.explanation) {
                    TechnicalKicker("EXPLANATION")
                    Text(
                        explainNotam(n),
                        color = LiveText.copy(alpha = 0.86f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }

                if (index != notams.lastIndex) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = LiveLine)
                }
            }
        }
    }
}

private fun decodeMetar(raw: String?): List<Pair<String, String>> {
    if (raw.isNullOrBlank()) return emptyList()
    val tokens = raw.uppercase().split(Regex("\\s+"))
    val out = mutableListOf<Pair<String, String>>()

    val wind = tokens.firstOrNull { it.matches(Regex("(\\d{3}|VRB)\\d{2,3}(G\\d{2,3})?KT")) }
    if (wind != null) out += "WIND" to wind

    val vis = tokens.firstOrNull { it == "CAVOK" || it.matches(Regex("\\d{4}")) }
    if (vis != null) out += "VISIBILITY" to if (vis == "9999") "10 KM OR MORE" else vis

    val clouds = tokens.filter { it.matches(Regex("(FEW|SCT|BKN|OVC)\\d{3}.*")) }
    if (clouds.isNotEmpty()) out += "CLOUD" to clouds.joinToString(" · ")

    val temp = tokens.firstOrNull { it.matches(Regex("M?\\d{2}/M?\\d{2}")) }
    if (temp != null) out += "TEMP / DEW" to temp

    val qnh = tokens.firstOrNull { it.matches(Regex("Q\\d{4}")) }
    if (qnh != null) out += "QNH" to qnh.removePrefix("Q") + " HPA"

    val wx = tokens.filter {
        it.contains("RA") || it.contains("SN") || it.contains("TS") ||
            it.contains("FG") || it.contains("BR") || it.contains("DZ")
    }
    if (wx.isNotEmpty()) out += "WEATHER" to wx.joinToString(" ")

    return out
}

private fun explainMetar(raw: String?): String {
    if (raw.isNullOrBlank()) return "Current METAR is not available."
    val decoded = decodeMetar(raw).toMap()
    val bits = mutableListOf<String>()
    decoded["WIND"]?.let { bits += "Reported wind: " + it + "." }
    decoded["VISIBILITY"]?.let { bits += "Visibility: " + it + "." }
    decoded["CLOUD"]?.let { bits += "Cloud: " + it + "." }
    decoded["WEATHER"]?.let { bits += "Reported weather: " + it + "." }
    decoded["QNH"]?.let { bits += "QNH: " + it + "." }
    if (raw.contains("NOSIG")) bits += "No significant short-term change is reported."
    return bits.joinToString(" ").ifBlank { "Live METAR loaded; refer to the raw report above." }
}

private fun decodeTaf(raw: String?): List<Pair<String, String>> {
    if (raw.isNullOrBlank()) return emptyList()
    val upper = raw.uppercase()
    val out = mutableListOf<Pair<String, String>>()
    val validity = Regex("\\b\\d{4}/\\d{4}\\b").find(upper)?.value
    if (validity != null) out += "VALIDITY" to validity
    val changes = Regex("\\b(BECMG|TEMPO|PROB30|PROB40)\\b").findAll(upper).map { it.value }.toList()
    if (changes.isNotEmpty()) out += "CHANGE GROUPS" to changes.joinToString(" · ")
    val winds = Regex("\\b(\\d{3}|VRB)\\d{2,3}(G\\d{2,3})?KT\\b").findAll(upper).map { it.value }.take(5).toList()
    if (winds.isNotEmpty()) out += "WINDS" to winds.joinToString(" → ")
    val vis = Regex("\\b(9999|\\d{4})\\b").findAll(upper).map { it.value }.take(5).toList()
    if (vis.isNotEmpty()) out += "VISIBILITY" to vis.joinToString(" → ")
    return out
}

private fun explainTaf(raw: String?): String {
    if (raw.isNullOrBlank()) return "Current TAF is not available."
    val upper = raw.uppercase()
    val bits = mutableListOf<String>()
    if (upper.contains("BECMG")) bits += "The forecast contains a BECMG transition."
    if (upper.contains("TEMPO")) bits += "Temporary conditions are forecast during part of the validity period."
    if (upper.contains("PROB30") || upper.contains("PROB40")) bits += "A probability group is present."
    if (bits.isEmpty()) bits += "No BECMG, TEMPO or probability change group was detected in the current TAF."
    bits += "Use the raw TAF for the exact validity windows and operational wording."
    return bits.joinToString(" ")
}

private fun notamDecode(n: NotamItem): List<Pair<String, String>> = buildList {
    n.status?.let { add("STATUS" to it.uppercase()) }
    n.selectionCode?.let { add("Q / CODE" to it) }
    n.effectiveStart?.let { add("FROM" to it) }
    add("TO" to (n.effectiveEndRaw ?: n.effectiveEnd ?: "OPEN"))
    if (n.lowerLimit != null || n.upperLimit != null) {
        add("LIMITS" to ((n.lowerLimit ?: "SFC") + " / " + (n.upperLimit ?: "UNL")))
    }
    n.classification?.let { add("CLASS" to it) }
}

private fun explainNotam(n: NotamItem): String {
    val end = n.effectiveEndRaw ?: n.effectiveEnd ?: "an unspecified end time"
    val limits = if (n.lowerLimit != null || n.upperLimit != null) {
        " Vertical limits: " + (n.lowerLimit ?: "SFC") + " to " + (n.upperLimit ?: "UNL") + "."
    } else ""
    return "This notice is applicable to the selected airport context and is valid until " + end + "." + limits
}

@Composable
private fun TechnicalKicker(text: String) {
    Text(
        text,
        color = LiveCyan,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun ErrorLine(message: String) {
    Surface(
        color = LiveRed.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, LiveRed.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(5.dp)
    ) {
        Text(
            message,
            color = LiveRed,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.padding(10.dp)
        )
    }
}

@Composable
private fun LiveBriefingScreen() {
    var from by rememberSaveable { mutableStateOf("LTAI") }
    var to by rememberSaveable { mutableStateOf("EDDB") }
    var etd by rememberSaveable { mutableStateOf(YulCaribeApi.defaultEtdUtc()) }
    var fl by rememberSaveable { mutableStateOf("360") }
    var route by rememberSaveable { mutableStateOf("") }
    var requestId by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf<BriefingPayload?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(requestId) {
        if (requestId == 0) return@LaunchedEffect
        loading = true
        error = null
        val result = runCatching {
            YulCaribeApi.briefing(
                from.trim().uppercase(),
                to.trim().uppercase(),
                etd.trim(),
                fl.toIntOrNull() ?: 360,
                route.trim().uppercase()
            )
        }
        result.onSuccess { data = it }.onFailure { error = it.message ?: "Briefing request failed." }
        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        item {
            LiveBrandHeader("Pilot Briefing", connected = data != null && error == null)
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(12.dp))
                Text("PILOT BRIEFING", color = LiveText, fontSize = 29.sp, fontWeight = FontWeight.SemiBold)
                Text("Live route / time / level briefing from YulCaribe Main.", color = LiveMuted, fontSize = 12.sp)
                Spacer(Modifier.height(22.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiveBriefField("FROM", from, { from = it }, Modifier.weight(1f))
                    LiveBriefField("TO", to, { to = it }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiveBriefField("ETD UTC", etd, { etd = it }, Modifier.weight(1.4f))
                    LiveBriefField("FL", fl, { fl = it }, Modifier.weight(0.6f))
                }
                Spacer(Modifier.height(10.dp))
                TechnicalKicker("OFP / ROUTE · OPTIONAL")
                OutlinedTextField(
                    value = route,
                    onValueChange = { route = it.uppercase() },
                    modifier = Modifier.fillMaxWidth().height(92.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = LiveText),
                    shape = RoundedCornerShape(6.dp)
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { requestId++ },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCyan, contentColor = LiveVoid),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("GET LIVE PILOT BRIEF", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                if (loading) {
                    Spacer(Modifier.height(14.dp))
                    Text("Building live briefing…", color = LiveMuted)
                }
                if (error != null) {
                    Spacer(Modifier.height(14.dp))
                    ErrorLine(error!!)
                }
                Spacer(Modifier.height(22.dp))
            }
        }

        data?.let { brief ->
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    BriefRouteCanvas(brief)
                    Spacer(Modifier.height(18.dp))

                    LiveBriefBlock(
                        "ROUTE",
                        brief.routeMode.uppercase(),
                        listOf(
                            "DISTANCE" to brief.distanceNm.toInt().toString() + " NM",
                            "ETD" to (brief.etdUtc ?: etd),
                            "CRUISE" to "FL" + brief.cruiseFL,
                            "EET" to brief.estimatedEetMinutes.toString() + " MIN"
                        )
                    )

                    LiveBriefBlock(
                        "SIGMET",
                        "ROUTE RELEVANCE",
                        listOf(
                            "HAZARDS" to brief.hazards.size.toString(),
                            "SOURCE" to brief.source
                        )
                    )

                    brief.hazards.take(10).forEach { hazard ->
                        Column(Modifier.padding(bottom = 14.dp)) {
                            Text(
                                hazard.hazard + " · " + (hazard.proximity ?: "ROUTE"),
                                color = LiveAmber,
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
                                color = LiveMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                            if (!hazard.raw.isNullOrBlank()) {
                                Text(
                                    hazard.raw,
                                    color = LiveText.copy(alpha = 0.86f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(top = 5.dp)
                                )
                            }
                        }
                    }

                    LiveBriefBlock(
                        "METAR / TAF",
                        "REPRESENTATIVE STATIONS",
                        listOf("STATIONS" to brief.stations.size.toString())
                    )

                    brief.stations.forEach { station ->
                        Column(Modifier.padding(bottom = 18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    station.icao,
                                    color = LiveCyan,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    station.role.uppercase() + (station.flightCategory?.let { " · " + it } ?: ""),
                                    color = LiveMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp
                                )
                            }
                            station.metarRaw?.let {
                                Text("METAR  " + it, color = LiveText, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 15.sp)
                            }
                            station.tafRaw?.let {
                                Text("TAF    " + it, color = LiveText.copy(alpha = 0.82f), fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveBriefField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        TechnicalKicker(label)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = LiveText),
            shape = RoundedCornerShape(6.dp)
        )
    }
}

@Composable
private fun BriefRouteCanvas(brief: BriefingPayload) {
    val points = brief.route
    Box(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .background(Color(0xFF071017), RoundedCornerShape(6.dp))
            .border(1.dp, LiveLine, RoundedCornerShape(6.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (points.size < 2) return@Canvas
            val minLon = points.minOf { it.lon }
            val maxLon = points.maxOf { it.lon }
            val minLat = points.minOf { it.lat }
            val maxLat = points.maxOf { it.lat }
            fun x(lon: Double) = ((lon - minLon) / (maxLon - minLon).coerceAtLeast(0.001) * size.width * 0.82 + size.width * 0.09).toFloat()
            fun y(lat: Double) = (size.height * 0.91 - (lat - minLat) / (maxLat - minLat).coerceAtLeast(0.001) * size.height * 0.82).toFloat()
            val path = Path()
            points.forEachIndexed { index, p ->
                if (index == 0) path.moveTo(x(p.lon), y(p.lat)) else path.lineTo(x(p.lon), y(p.lat))
            }
            drawPath(path, LiveCyan, style = Stroke(width = 3f))
            drawCircle(LiveCyanSoft, 7f, androidx.compose.ui.geometry.Offset(x(points.first().lon), y(points.first().lat)))
            drawCircle(LiveCyanSoft, 7f, androidx.compose.ui.geometry.Offset(x(points.last().lon), y(points.last().lat)))
        }
        Text(
            "LIVE ROUTE · " + brief.distanceNm.toInt() + " NM",
            color = LiveCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun LiveBriefBlock(title: String, subtitle: String, rows: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = LiveText, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(subtitle, color = LiveCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        }
        Spacer(Modifier.height(7.dp))
        HorizontalDivider(color = LiveLine)
        Spacer(Modifier.height(7.dp))
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(row.first, color = LiveMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp, modifier = Modifier.weight(0.4f))
                Text(row.second, color = LiveText, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.weight(0.6f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveNavMapScreen() {
    var center by remember { mutableStateOf(ltfmFallback) }
    var nav by remember { mutableStateOf<NavViewport?>(null) }
    var aircraft by remember { mutableStateOf<List<AircraftPoint>>(emptyList()) }
    var layers by remember { mutableStateOf(LiveLayers()) }
    var showLayers by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        runCatching { YulCaribeApi.airportExact("LTFM") }.getOrNull()?.let { center = it }
    }

    LaunchedEffect(center.icao, layers, refresh) {
        loading = true
        error = null
        val result = runCatching {
            coroutineScope {
                val mapTask = async {
                    YulCaribeApi.navViewport(center.lat, center.lon, 7, layers.serverLayers())
                }
                val adsbTask = async {
                    if (layers.adsb) YulCaribeApi.flights(center.lat, center.lon, 100) else emptyList()
                }
                mapTask.await() to adsbTask.await()
            }
        }
        result.onSuccess {
            nav = it.first
            aircraft = it.second
        }.onFailure { error = it.message ?: "NavMap request failed." }
        loading = false
    }

    Box(Modifier.fillMaxSize().background(LiveVoid)) {
        LiveNavCanvas(nav, center, aircraft, Modifier.fillMaxSize())

        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(LiveVoid.copy(alpha = 0.78f), Color.Transparent)
                    )
                )
        ) {
            LiveBrandHeader("Aeronautical NavMap", connected = nav != null && error == null)
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
                color = LiveSurface.copy(alpha = 0.94f),
                border = androidx.compose.foundation.BorderStroke(1.dp, LiveLine),
                shape = RoundedCornerShape(7.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Layers, null, tint = LiveCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("LAYERS", color = LiveText, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                onClick = { refresh++ },
                color = LiveSurface.copy(alpha = 0.94f),
                border = androidx.compose.foundation.BorderStroke(1.dp, LiveLine),
                shape = RoundedCornerShape(7.dp)
            ) {
                Icon(Icons.Outlined.Refresh, null, tint = LiveCyan, modifier = Modifier.padding(10.dp).size(18.dp))
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
            LiveMapChip("CHARTS", true)
            LiveMapChip("NOTAM " + (nav?.counts?.get("notam") ?: 0), layers.notam)
            LiveMapChip("ADS-B " + aircraft.size, layers.adsb)
        }

        if (loading) {
            Text(
                "LOADING LIVE MAP…",
                color = LiveCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            )
        }

        if (error != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(14.dp)) {
                ErrorLine(error!!)
            }
        }
    }

    if (showLayers) {
        ModalBottomSheet(
            onDismissRequest = { showLayers = false },
            containerColor = LiveSurface,
            contentColor = LiveText
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("LIVE MAP LAYERS", color = LiveText, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text("All layers use the existing YulCaribe Main APIs.", color = LiveMuted, fontSize = 11.sp)
                Spacer(Modifier.height(18.dp))

                LiveLayerToggle("Airports", layers.airport) { layers = layers.copy(airport = it) }
                LiveLayerToggle("Navaids", layers.navaid) { layers = layers.copy(navaid = it) }
                LiveLayerToggle("Waypoints", layers.waypoint) { layers = layers.copy(waypoint = it) }
                LiveLayerToggle("Airways", layers.airway) { layers = layers.copy(airway = it) }
                LiveLayerToggle("SID", layers.sid) { layers = layers.copy(sid = it) }
                LiveLayerToggle("STAR", layers.star) { layers = layers.copy(star = it) }
                LiveLayerToggle("Airspace", layers.airspace) { layers = layers.copy(airspace = it) }
                LiveLayerToggle("NOTAM geometry", layers.notam) { layers = layers.copy(notam = it) }
                LiveLayerToggle("ADS-B traffic", layers.adsb) { layers = layers.copy(adsb = it) }

                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun LiveMapChip(label: String, active: Boolean) {
    Surface(
        color = if (active) LiveCyan.copy(alpha = 0.12f) else LiveSurface.copy(alpha = 0.92f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) LiveCyan.copy(alpha = 0.6f) else LiveLine),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            label,
            color = if (active) LiveCyanSoft else LiveMuted,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun LiveLayerToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = LiveText, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = LiveVoid,
                checkedTrackColor = LiveCyan,
                uncheckedThumbColor = LiveMuted,
                uncheckedTrackColor = LiveHigh
            )
        )
    }
}

@Composable
private fun LiveNavCanvas(
    nav: NavViewport?,
    center: AirportResult,
    aircraft: List<AircraftPoint>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.background(Color(0xFF081116))) {
        val latSpan = 1.0
        val lonSpan = 1.35
        val west = center.lon - lonSpan
        val east = center.lon + lonSpan
        val south = center.lat - latSpan
        val north = center.lat + latSpan

        fun project(p: GeoPoint): androidx.compose.ui.geometry.Offset {
            val px = ((p.lon - west) / (east - west) * size.width).toFloat()
            val py = (size.height - (p.lat - south) / (north - south) * size.height).toFloat()
            return androidx.compose.ui.geometry.Offset(px, py)
        }

        nav?.shapes?.forEach { shape ->
            val color = when (shape.layer) {
                "airway" -> LiveCyan.copy(alpha = 0.72f)
                "sid" -> Color(0xFF70E8A7).copy(alpha = 0.68f)
                "star" -> Color(0xFFBC9CFF).copy(alpha = 0.68f)
                "airspace" -> Color(0xFFFF7F94).copy(alpha = 0.52f)
                "notam" -> LiveAmber.copy(alpha = 0.65f)
                "navaid" -> LiveAmber.copy(alpha = 0.85f)
                "airport" -> LiveCyanSoft.copy(alpha = 0.90f)
                else -> LiveMuted.copy(alpha = 0.48f)
            }

            shape.lines.forEach { line ->
                if (line.size < 2) return@forEach
                val path = Path()
                line.forEachIndexed { index, p ->
                    val pt = project(p)
                    if (index == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                }
                drawPath(path, color, style = Stroke(width = if (shape.layer == "airway") 1.8f else 1.35f))
            }

            shape.point?.let { p ->
                val pt = project(p)
                drawCircle(color, if (shape.layer == "airport") 5.5f else 3.4f, pt)
            }
        }

        aircraft.forEach { ac ->
            val pt = project(GeoPoint(ac.lon, ac.lat))
            drawCircle(LiveText.copy(alpha = 0.90f), 4.5f, pt)
            val radians = Math.toRadians(ac.track ?: 0.0)
            val end = androidx.compose.ui.geometry.Offset(
                pt.x + kotlin.math.sin(radians).toFloat() * 10f,
                pt.y - kotlin.math.cos(radians).toFloat() * 10f
            )
            drawLine(LiveCyan, pt, end, strokeWidth = 1.5f, cap = StrokeCap.Round)
        }

        val cp = project(GeoPoint(center.lon, center.lat))
        drawCircle(LiveCyanSoft, 8f, cp)
        drawCircle(LiveVoid, 4f, cp)
    }
}

@Composable
private fun LiveSettingsScreen(prefs: LivePrefs, onPrefs: (LivePrefs) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 36.dp)) {
        item {
            LiveBrandHeader("Settings", connected = true)
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(16.dp))
                Text("SETTINGS", color = LiveText, fontSize = 29.sp, fontWeight = FontWeight.SemiBold)
                Text("Airport result visibility preferences.", color = LiveMuted, fontSize = 12.sp)
                Spacer(Modifier.height(24.dp))

                LiveSettingToggle("Show RAW", "Original METAR, TAF and NOTAM source text.", prefs.raw) {
                    onPrefs(prefs.copy(raw = it))
                }
                LiveSettingToggle("Show decoded", "Structured values parsed on-device from live reports.", prefs.decoded) {
                    onPrefs(prefs.copy(decoded = it))
                }
                LiveSettingToggle("Show explanation", "Human-readable parsed explanation.", prefs.explanation) {
                    onPrefs(prefs.copy(explanation = it))
                }

                Spacer(Modifier.height(26.dp))
                HorizontalDivider(color = LiveLine)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Data endpoints: YulCaribe Main · AviationWeather.gov · FAA NMS · ADSB.lol proxy",
                    color = LiveMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun LiveSettingToggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = LiveText, fontSize = 13.sp)
            Text(detail, color = LiveMuted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 2.dp, end = 16.dp))
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = LiveVoid,
                checkedTrackColor = LiveCyan,
                uncheckedThumbColor = LiveMuted,
                uncheckedTrackColor = LiveHigh
            )
        )
    }
    HorizontalDivider(color = LiveLine.copy(alpha = 0.7f))
}
