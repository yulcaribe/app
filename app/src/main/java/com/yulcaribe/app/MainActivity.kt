package com.yulcaribe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Void = Color(0xFF05070A)
private val SurfaceBase = Color(0xFF0B1016)
private val SurfaceHigh = Color(0xFF111821)
private val Hairline = Color(0xFF1A2630)
private val Cyan = Color(0xFF00D9FF)
private val CyanSoft = Color(0xFF7AEAFF)
private val TextPrimary = Color(0xFFF5F7FA)
private val TextSecondary = Color(0xFF8E9AA7)
private val Success = Color(0xFF4BE28C)
private val Warning = Color(0xFFFFB84D)
private val Danger = Color(0xFFFF5F6D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YulCaribeTheme {
                LiveYulCaribeApp()
            }
        }
    }
}

private enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Briefing("Pilot Briefing", Icons.Outlined.FlightTakeoff),
    NavMap("NavMap", Icons.Outlined.Map),
    Settings("Settings", Icons.Outlined.Settings)
}

private data class Airport(
    val icao: String,
    val iata: String,
    val name: String,
    val city: String
)

private data class AppPrefs(
    val showRaw: Boolean = true,
    val showDecoded: Boolean = true,
    val showExplanation: Boolean = true,
    val metarAlerts: Boolean = true,
    val tafAlerts: Boolean = false,
    val notamAlerts: Boolean = true,
    val rememberMapLayers: Boolean = true,
    val denseMode: Boolean = false
)

private data class LayerState(
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
)

private val airports = listOf(
    Airport("LTFM", "IST", "Istanbul Airport", "Istanbul"),
    Airport("LTAI", "AYT", "Antalya Airport", "Antalya"),
    Airport("LTAC", "ESB", "Esenboga Airport", "Ankara"),
    Airport("LTBJ", "ADB", "Adnan Menderes Airport", "Izmir"),
    Airport("EDDF", "FRA", "Frankfurt Airport", "Frankfurt"),
    Airport("EGLL", "LHR", "Heathrow Airport", "London")
)

@Composable
private fun YulCaribeTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Cyan,
        onPrimary = Void,
        secondary = CyanSoft,
        background = Void,
        onBackground = TextPrimary,
        surface = SurfaceBase,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceHigh,
        onSurfaceVariant = TextSecondary,
        error = Danger
    )

    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography.copy(
            headlineLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                letterSpacing = (-0.5).sp
            ),
            headlineMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp
            ),
            titleMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                letterSpacing = 0.4.sp
            ),
            bodyMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        ),
        content = content
    )
}

@Composable
private fun YulCaribeApp() {
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var prefs by remember { mutableStateOf(AppPrefs()) }

    Scaffold(
        containerColor = Void,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xF20A0E13),
                tonalElevation = 0.dp,
                modifier = Modifier.border(1.dp, Hairline)
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
                            selectedIconColor = Cyan,
                            selectedTextColor = TextPrimary,
                            indicatorColor = Cyan.copy(alpha = 0.12f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
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
                Destination.Briefing -> PilotBriefingScreen()
                Destination.NavMap -> NavMapScreen()
                Destination.Settings -> SettingsScreen(
                    prefs = prefs,
                    onPrefsChange = { prefs = it }
                )
            }
        }
    }
}

@Composable
private fun BrandHeader(kicker: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "YULCARIBE",
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                letterSpacing = 2.2.sp
            )
            if (kicker != null) {
                Text(
                    kicker.uppercase(),
                    color = TextSecondary,
                    fontSize = 9.sp,
                    letterSpacing = 1.1.sp
                )
            }
        }
        Text(
            "● LIVE",
            color = Success,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun HomeScreen(prefs: AppPrefs) {
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    var query by rememberSaveable { mutableStateOf("") }
    var activeAirport by remember { mutableStateOf(airports.first()) }
    var showResults by rememberSaveable { mutableStateOf(false) }

    val heroHeight by animateDpAsState(
        targetValue = if (showResults) 310.dp else 500.dp,
        label = "homeHero"
    )

    val matches = remember(query) {
        if (query.isBlank()) emptyList()
        else {
            val needle = query.trim().uppercase()
            airports.filter {
                it.icao.contains(needle) ||
                    it.iata.contains(needle) ||
                    it.name.uppercase().contains(needle) ||
                    it.city.uppercase().contains(needle)
            }.take(4)
        }
    }

    androidx.compose.runtime.LaunchedEffect(showResults, activeAirport.icao) {
        if (showResults) {
            delay(380)
            listState.animateScrollToItem(1)
        }
    }

    fun selectAirport(airport: Airport) {
        activeAirport = airport
        query = airport.icao
        showResults = true
        focusManager.clearFocus()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .background(Void)
                    .animateContentSize()
            ) {
                AviationBackdrop(
                    airport = activeAirport,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(5.dp)
                        .alpha(0.58f)
                )

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Void.copy(alpha = 0.12f),
                                0.56f to Void.copy(alpha = 0.38f),
                                1f to Void
                            )
                        )
                )

                Column(Modifier.fillMaxSize()) {
                    BrandHeader("Airport intelligence")

                    Spacer(Modifier.weight(1f))

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = if (showResults) 18.dp else 36.dp)
                    ) {
                        Text(
                            if (showResults) "ACTIVE AIRPORT" else "LIVE CONTEXT",
                            color = Cyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            letterSpacing = 1.3.sp
                        )
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                activeAirport.icao,
                                style = MaterialTheme.typography.headlineLarge,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                activeAirport.iata + " · " + activeAirport.city.uppercase(),
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 5.dp)
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                query = it
                                if (it != activeAirport.icao) showResults = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    "Airport, ICAO or IATA",
                                    color = TextSecondary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = Cyan
                                )
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    matches.firstOrNull()?.let { selectAirport(it) }
                                }
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        AnimatedVisibility(
                            visible = matches.isNotEmpty() &&
                                !showResults &&
                                query.uppercase() != activeAirport.icao
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .background(Color(0xF20B1016), RoundedCornerShape(8.dp))
                                    .border(1.dp, Hairline, RoundedCornerShape(8.dp))
                            ) {
                                matches.forEachIndexed { index, airport ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectAirport(airport) }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            airport.icao,
                                            color = Cyan,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(55.dp)
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                airport.name,
                                                color = TextPrimary,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                airport.iata + " · " + airport.city,
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    if (index != matches.lastIndex) {
                                        HorizontalDivider(color = Hairline)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showResults) {
            item {
                AirportResultFlow(
                    airport = activeAirport,
                    prefs = prefs
                )
            }
        } else {
            item {
                HomeHint()
            }
        }
    }
}

@Composable
private fun HomeHint() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            "SEARCH FIRST",
            color = Cyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Search an airport to bring its chart context forward, then continue into METAR, TAF and NOTAM on the same screen.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Hairline)
        Spacer(Modifier.height(16.dp))
        Text(
            "Background preview uses the YulCaribe NavMap visual language: airways, airspace and navigation geometry. Live API data is intentionally not wired in this first design build.",
            color = TextSecondary.copy(alpha = 0.78f),
            fontSize = 11.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun AirportResultFlow(
    airport: Airport,
    prefs: AppPrefs
) {
    val isAntalya = airport.icao == "LTAI"
    val metar = if (isAntalya) {
        "LTAI 251750Z 21007KT 9999 FEW025 27/18 Q1012 NOSIG"
    } else {
        airport.icao + " 251750Z 03009KT 9999 SCT030 21/14 Q1015 NOSIG"
    }
    val taf = if (isAntalya) {
        "TAF LTAI 251700Z 2518/2618 21008KT 9999 FEW025 TEMPO 2602/2607 5000 BR BECMG 2608/2610 18012KT"
    } else {
        "TAF " + airport.icao + " 251700Z 2518/2618 03010KT 9999 SCT030 TEMPO 2603/2607 6000 BKN020"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Void)
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 36.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    airport.icao + " / " + airport.iata,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Text(
                    airport.name,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
            Surface(
                color = Warning.copy(alpha = 0.10f),
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Warning.copy(alpha = 0.45f)
                )
            ) {
                Text(
                    "DESIGN DATA",
                    color = Warning,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        TechnicalDataSection(
            title = "METAR",
            status = "OBSERVATION",
            raw = metar,
            decoded = listOf(
                "WIND" to if (isAntalya) "210° / 07 KT" else "030° / 09 KT",
                "VISIBILITY" to "10 KM+",
                "CLOUD" to if (isAntalya) "FEW 2,500 FT" else "SCT 3,000 FT",
                "TEMP / DEW" to if (isAntalya) "27°C / 18°C" else "21°C / 14°C",
                "QNH" to if (isAntalya) "1012 HPA" else "1015 HPA"
            ),
            explanation = if (isAntalya) {
                "Visibility is unrestricted. Light south-westerly wind, few clouds at 2,500 ft and no significant short-term change reported."
            } else {
                "Good visibility with light north-easterly wind. Scattered cloud layer is reported and no significant short-term change is included."
            },
            prefs = prefs
        )

        TechnicalDataSection(
            title = "TAF",
            status = "FORECAST",
            raw = taf,
            decoded = listOf(
                "BASE" to "9999 · light wind · limited cloud",
                "TEMPO" to "Reduced visibility window overnight",
                "BECMG" to if (isAntalya) "Wind shifts southerly in the morning" else "No BECMG group in sample"
            ),
            explanation = if (isAntalya) {
                "Forecast remains generally favourable. A temporary visibility reduction is possible overnight, followed by a southerly wind increase during the morning period."
            } else {
                "Forecast remains broadly stable, with a temporary overnight reduction in visibility and lower cloud possible."
            },
            prefs = prefs
        )

        TechnicalDataSection(
            title = "NOTAM",
            status = "AIRPORT LIST",
            raw = "Airport-specific NOTAM listing endpoint is not connected in this Android prototype yet.",
            decoded = listOf(
                "STATUS" to "Backend connection pending",
                "PLANNED" to "Validity · category · Q-code · geometry",
                "SOURCE" to "YulCaribe Main NOTAM data"
            ),
            explanation = "When the airport NOTAM endpoint is connected, active notices will appear here in sequence with raw text, decoded fields and the parsed human-readable explanation.",
            prefs = prefs,
            pending = true
        )
    }
}

@Composable
private fun TechnicalDataSection(
    title: String,
    status: String,
    raw: String,
    decoded: List<Pair<String, String>>,
    explanation: String,
    prefs: AppPrefs,
    pending: Boolean = false
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 30.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.width(10.dp))
            Text(
                status,
                color = if (pending) Warning else Cyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                "UTC",
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Hairline)
        Spacer(Modifier.height(14.dp))

        if (prefs.showRaw) {
            TechnicalLabel("RAW")
            Text(
                raw,
                color = TextPrimary.copy(alpha = if (pending) 0.65f else 0.92f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(18.dp))
        }

        if (prefs.showDecoded) {
            TechnicalLabel("DECODED")
            decoded.forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                ) {
                    Text(
                        row.first,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.weight(0.38f)
                    )
                    Text(
                        row.second,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(0.62f)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (prefs.showExplanation) {
            TechnicalLabel("EXPLANATION")
            Text(
                explanation,
                color = TextPrimary.copy(alpha = 0.88f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun TechnicalLabel(text: String) {
    Text(
        text,
        color = Cyan,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 7.dp)
    )
}

@Composable
private fun AviationBackdrop(
    airport: Airport,
    modifier: Modifier = Modifier
) {
    Box(modifier.background(Color(0xFF071017))) {
        Canvas(Modifier.fillMaxSize()) {
            val center = androidx.compose.ui.geometry.Offset(
                size.width * 0.54f,
                size.height * 0.47f
            )

            drawCircle(
                color = Color(0xFF0E2029),
                radius = size.minDimension * 0.48f,
                center = center
            )

            val routeColor = Cyan.copy(alpha = 0.72f)
            for (i in 0 until 9) {
                val angle = (-2.55 + i * 0.55) + ((airport.icao.hashCode() % 17) / 50.0)
                val length = size.maxDimension * (0.55f + (i % 3) * 0.13f)
                val end = androidx.compose.ui.geometry.Offset(
                    center.x + kotlin.math.cos(angle).toFloat() * length,
                    center.y + kotlin.math.sin(angle).toFloat() * length
                )
                drawLine(
                    color = routeColor,
                    start = center,
                    end = end,
                    strokeWidth = if (i % 2 == 0) 2.2f else 1.3f,
                    cap = StrokeCap.Round
                )
            }

            val airspace = Color(0xFFFF6F91).copy(alpha = 0.42f)
            drawCircle(
                color = airspace,
                radius = size.minDimension * 0.18f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
            drawCircle(
                color = airspace.copy(alpha = 0.28f),
                radius = size.minDimension * 0.29f,
                center = androidx.compose.ui.geometry.Offset(center.x * 0.73f, center.y * 1.18f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
            )

            for (i in 0 until 22) {
                val x = ((i * 83 + airport.icao.hashCode()) and 0x7fffffff) % 1000 / 1000f
                val y = ((i * 47 + airport.iata.hashCode()) and 0x7fffffff) % 1000 / 1000f
                drawCircle(
                    color = TextSecondary.copy(alpha = 0.25f),
                    radius = if (i % 5 == 0) 3.3f else 1.9f,
                    center = androidx.compose.ui.geometry.Offset(size.width * x, size.height * y)
                )
            }

            drawCircle(
                color = CyanSoft,
                radius = 8f,
                center = center
            )
            drawCircle(
                color = Void,
                radius = 4.5f,
                center = center
            )
        }

        Surface(
            color = Color(0xCC071017),
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Cyan.copy(alpha = 0.35f)),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 12.dp)
        ) {
            Text(
                airport.icao,
                color = CyanSoft,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun PilotBriefingScreen() {
    var from by rememberSaveable { mutableStateOf("LTAI") }
    var to by rememberSaveable { mutableStateOf("EDDB") }
    var etd by rememberSaveable { mutableStateOf("18:30Z") }
    var flightLevel by rememberSaveable { mutableStateOf("360") }
    var route by rememberSaveable {
        mutableStateOf("KUMRU N131 TUMBO UL620 NISVA DCT BUDOP")
    }
    var generated by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        item {
            BrandHeader("Pilot Briefing")
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "PILOT BRIEFING",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Route, time and level aware aviation weather workspace.",
                    color = TextSecondary
                )
                Spacer(Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BriefField("FROM", from, { from = it }, Modifier.weight(1f))
                    BriefField("TO", to, { to = it }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BriefField("ETD UTC", etd, { etd = it }, Modifier.weight(1f))
                    BriefField("CRUISE FL", flightLevel, { flightLevel = it }, Modifier.weight(1f))
                }

                Spacer(Modifier.height(12.dp))
                Text("OFP / ROUTE", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = route,
                    onValueChange = { route = it.uppercase() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TextPrimary
                    ),
                    shape = RoundedCornerShape(6.dp)
                )

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { generated = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Cyan,
                        contentColor = Void
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "GENERATE PILOT BRIEF",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(22.dp))
            }
        }

        if (generated) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    BriefMapPreview(from, to)
                    Spacer(Modifier.height(18.dp))

                    BriefingBlock(
                        "ROUTE",
                        from + "  →  " + to,
                        listOf(
                            "ETD" to etd,
                            "CRUISE" to ("FL" + flightLevel),
                            "ROUTE INPUT" to "USER / OFP"
                        )
                    )

                    BriefingBlock(
                        "MODEL WX",
                        "GFS + WAFS",
                        listOf(
                            "GFS WIND" to "Route sampling",
                            "WAFS" to "Visual forecast",
                            "FL" to flightLevel
                        )
                    )

                    BriefingBlock(
                        "WAFS PRODUCTS",
                        "ROUTE FORECAST",
                        listOf(
                            "TURBULENCE / EDR" to "Layer",
                            "ICING" to "Layer",
                            "CB EXTENT / TOPS" to "Layer",
                            "WIND" to "Layer"
                        )
                    )

                    BriefingBlock(
                        "SIGMET",
                        "ROUTE RELEVANCE",
                        listOf(
                            "TIME WINDOW" to "ETD aware",
                            "GEOMETRY" to "Route intersection",
                            "STATUS" to "API connection next"
                        )
                    )

                    BriefingBlock(
                        "METAR / TAF",
                        "REPRESENTATIVE POINTS",
                        listOf(
                            from to "Departure",
                            to to "Arrival",
                            "ENROUTE" to "Representative stations"
                        )
                    )
                }
            }
        } else {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    HorizontalDivider(color = Hairline)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "The Android screen keeps Pilot Briefing separate from Home airport search. Results will combine route-aware METAR/TAF, SIGMET, GFS and WAFS when the Main APIs are wired in.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BriefField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            label,
            color = Cyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp
        )
        Spacer(Modifier.height(5.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.uppercase()) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = TextPrimary
            ),
            shape = RoundedCornerShape(6.dp)
        )
    }
}

@Composable
private fun BriefMapPreview(from: String, to: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .background(Color(0xFF071017), RoundedCornerShape(6.dp))
            .border(1.dp, Hairline, RoundedCornerShape(6.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until 6) {
                val y = size.height * (0.12f + i * 0.15f)
                drawLine(
                    color = Cyan.copy(alpha = 0.15f),
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width, y + (i - 3) * 10f),
                    strokeWidth = 1.2f
                )
            }
            val start = androidx.compose.ui.geometry.Offset(size.width * 0.16f, size.height * 0.72f)
            val end = androidx.compose.ui.geometry.Offset(size.width * 0.84f, size.height * 0.28f)
            drawLine(
                color = Cyan,
                start = start,
                end = end,
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
            drawCircle(CyanSoft, 7f, start)
            drawCircle(CyanSoft, 7f, end)
        }

        Text(
            from,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp)
        )
        Text(
            to,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(18.dp)
        )
        Text(
            "ROUTE PREVIEW",
            color = Cyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
        )
    }
}

@Composable
private fun BriefingBlock(
    title: String,
    subtitle: String,
    rows: List<Pair<String, String>>
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                subtitle,
                color = Cyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Hairline)
        Spacer(Modifier.height(8.dp))
        rows.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
            ) {
                Text(
                    row.first,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(0.46f)
                )
                Text(
                    row.second,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(0.54f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavMapScreen() {
    var showLayers by remember { mutableStateOf(false) }
    var layers by remember { mutableStateOf(LayerState()) }
    var mapSearch by rememberSaveable { mutableStateOf("") }
    var timeOffset by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Void)
    ) {
        NavMapCanvas(
            layers = layers,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Void.copy(alpha = 0.70f),
                            Color.Transparent,
                            Color.Transparent,
                            Void.copy(alpha = 0.45f)
                        )
                    )
                )
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            BrandHeader("Aeronautical NavMap")
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = mapSearch,
                    onValueChange = { mapSearch = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = {
                        Text(
                            "ICAO / waypoint / airway / SID / STAR",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, null, tint = Cyan)
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = { showLayers = true },
                    color = SurfaceBase.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Hairline)
                ) {
                    Icon(
                        Icons.Outlined.Layers,
                        contentDescription = "Layers",
                        tint = Cyan,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                MapStatusChip("CHARTS", true)
                MapStatusChip("NOTAM", layers.notam)
                MapStatusChip("WAFS", layers.wafs)
                MapStatusChip("ADS-B", layers.adsb)
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .background(Color(0xF20B1016), RoundedCornerShape(8.dp))
                .border(1.dp, Hairline, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (timeOffset == 0f) "NOW" else ((if (timeOffset > 0) "+" else "") + timeOffset.toInt() + "h"),
                    color = Cyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "-24h     UTC TIMELINE     +24h",
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
            }
            Slider(
                value = timeOffset,
                onValueChange = { timeOffset = it },
                valueRange = -24f..24f,
                steps = 47
            )
        }
    }

    if (showLayers) {
        ModalBottomSheet(
            onDismissRequest = { showLayers = false },
            containerColor = SurfaceBase,
            contentColor = TextPrimary
        ) {
            LayerSheet(
                state = layers,
                onChange = { layers = it }
            )
        }
    }
}

@Composable
private fun MapStatusChip(label: String, active: Boolean) {
    Surface(
        color = if (active) Cyan.copy(alpha = 0.12f) else SurfaceBase.copy(alpha = 0.88f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (active) Cyan.copy(alpha = 0.65f) else Hairline
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            label,
            color = if (active) CyanSoft else TextSecondary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun LayerSheet(
    state: LayerState,
    onChange: (LayerState) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
    ) {
        item {
            Text(
                "MAP LAYERS",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                "One map. Navigation, NOTAM, WAFS and traffic are independent layers.",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(20.dp))

            LayerGroupTitle("CHARTS")
            LayerToggle("Airports", state.airports) { onChange(state.copy(airports = it)) }
            LayerToggle("Navaids", state.navaids) { onChange(state.copy(navaids = it)) }
            LayerToggle("Waypoints", state.waypoints) { onChange(state.copy(waypoints = it)) }
            LayerToggle("Airways", state.airways) { onChange(state.copy(airways = it)) }
            LayerToggle("SID", state.sid) { onChange(state.copy(sid = it)) }
            LayerToggle("STAR", state.star) { onChange(state.copy(star = it)) }
            LayerToggle("Airspace", state.airspace) { onChange(state.copy(airspace = it)) }

            Spacer(Modifier.height(14.dp))
            LayerGroupTitle("OPERATIONAL")
            LayerToggle("NOTAM geometry", state.notam) { onChange(state.copy(notam = it)) }
            LayerToggle("WAFS visual forecast", state.wafs) { onChange(state.copy(wafs = it)) }
            LayerToggle("ADS-B traffic", state.adsb) { onChange(state.copy(adsb = it)) }

            Spacer(Modifier.height(20.dp))
            Text(
                "WAFS flight level and product controls will sit inside this sheet when live data is connected.",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(34.dp))
        }
    }
}

@Composable
private fun LayerGroupTitle(text: String) {
    Text(
        text,
        color = Cyan,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.1.sp,
        modifier = Modifier.padding(bottom = 7.dp)
    )
}

@Composable
private fun LayerToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = TextPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Void,
                checkedTrackColor = Cyan,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceHigh
            )
        )
    }
}

@Composable
private fun NavMapCanvas(
    layers: LayerState,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.background(Color(0xFF091117))) {
        val center = androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.49f)

        if (layers.wafs) {
            drawCircle(
                color = Warning.copy(alpha = 0.08f),
                radius = size.minDimension * 0.34f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.36f)
            )
            drawCircle(
                color = Cyan.copy(alpha = 0.07f),
                radius = size.minDimension * 0.28f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.27f, size.height * 0.62f)
            )
        }

        if (layers.airspace) {
            val pink = Color(0xFFFF7F94).copy(alpha = 0.48f)
            drawCircle(
                color = pink,
                radius = size.minDimension * 0.18f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
            drawCircle(
                color = pink.copy(alpha = 0.32f),
                radius = size.minDimension * 0.29f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.66f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6f)
            )
        }

        if (layers.airways) {
            for (i in 0 until 13) {
                val angle = -2.9 + i * 0.47
                val length = size.maxDimension * (0.6f + (i % 4) * 0.08f)
                val end = androidx.compose.ui.geometry.Offset(
                    center.x + kotlin.math.cos(angle).toFloat() * length,
                    center.y + kotlin.math.sin(angle).toFloat() * length
                )
                drawLine(
                    color = Cyan.copy(alpha = 0.75f),
                    start = center,
                    end = end,
                    strokeWidth = if (i % 3 == 0) 2.6f else 1.5f
                )
            }
        }

        if (layers.navaids || layers.airports || layers.waypoints) {
            for (i in 0 until 27) {
                val x = ((i * 79 + 137) % 1000) / 1000f
                val y = ((i * 43 + 299) % 1000) / 1000f
                val color = when {
                    i % 7 == 0 && layers.airports -> CyanSoft
                    i % 3 == 0 && layers.navaids -> Warning
                    layers.waypoints -> TextSecondary
                    else -> Color.Transparent
                }
                if (color != Color.Transparent) {
                    drawCircle(
                        color = color.copy(alpha = 0.86f),
                        radius = if (i % 7 == 0) 5.5f else 3.2f,
                        center = androidx.compose.ui.geometry.Offset(size.width * x, size.height * y)
                    )
                }
            }
        }

        if (layers.notam) {
            drawCircle(
                color = Danger.copy(alpha = 0.14f),
                radius = size.minDimension * 0.13f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.74f, size.height * 0.60f)
            )
            drawCircle(
                color = Danger.copy(alpha = 0.62f),
                radius = size.minDimension * 0.13f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.74f, size.height * 0.60f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f)
            )
        }

        if (layers.adsb) {
            val positions = listOf(
                0.18f to 0.30f,
                0.37f to 0.57f,
                0.62f to 0.26f,
                0.79f to 0.47f,
                0.54f to 0.72f,
                0.27f to 0.79f
            )
            positions.forEachIndexed { index, p ->
                val x = size.width * p.first
                val y = size.height * p.second
                val path = Path().apply {
                    moveTo(x, y - 9f)
                    lineTo(x - 5.5f, y + 7f)
                    lineTo(x, y + 4f)
                    lineTo(x + 5.5f, y + 7f)
                    close()
                }
                drawPath(
                    path = path,
                    color = if (index == 2) Cyan else TextPrimary.copy(alpha = 0.82f)
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    prefs: AppPrefs,
    onPrefsChange: (AppPrefs) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        item {
            BrandHeader("Preferences")
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "SETTINGS",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "Control how much information YulCaribe shows by default.",
                    color = TextSecondary
                )

                Spacer(Modifier.height(28.dp))
                SettingsHeading("DEFAULT CONTEXT")
                SettingValueRow("Default airport", "LTFM / Istanbul Airport")
                SettingValueRow("Weather language", "English / Turkish later")
                SettingValueRow("WAFS default level", "FL360")

                Spacer(Modifier.height(24.dp))
                SettingsHeading("AIRPORT RESULTS")
                SettingToggle(
                    "Show raw data",
                    "Keep original METAR, TAF and NOTAM text visible.",
                    prefs.showRaw
                ) { onPrefsChange(prefs.copy(showRaw = it)) }
                SettingToggle(
                    "Show decoded fields",
                    "Display structured values below the raw report.",
                    prefs.showDecoded
                ) { onPrefsChange(prefs.copy(showDecoded = it)) }
                SettingToggle(
                    "Show explanation",
                    "Display the parsed human-readable explanation.",
                    prefs.showExplanation
                ) { onPrefsChange(prefs.copy(showExplanation = it)) }

                Spacer(Modifier.height(24.dp))
                SettingsHeading("NOTIFICATIONS")
                SettingToggle(
                    "METAR changes",
                    "Notify for followed airport observation updates.",
                    prefs.metarAlerts
                ) { onPrefsChange(prefs.copy(metarAlerts = it)) }
                SettingToggle(
                    "TAF updates",
                    "Notify when a followed airport forecast changes.",
                    prefs.tafAlerts
                ) { onPrefsChange(prefs.copy(tafAlerts = it)) }
                SettingToggle(
                    "NOTAM updates",
                    "Notify for new or changed airport notices.",
                    prefs.notamAlerts
                ) { onPrefsChange(prefs.copy(notamAlerts = it)) }

                Spacer(Modifier.height(24.dp))
                SettingsHeading("NAVMAP")
                SettingToggle(
                    "Remember map layers",
                    "Restore the previous NavMap layer configuration.",
                    prefs.rememberMapLayers
                ) { onPrefsChange(prefs.copy(rememberMapLayers = it)) }

                Spacer(Modifier.height(24.dp))
                SettingsHeading("DISPLAY")
                SettingToggle(
                    "Dense data mode",
                    "Reduce spacing for higher telemetry density.",
                    prefs.denseMode
                ) { onPrefsChange(prefs.copy(denseMode = it)) }

                Spacer(Modifier.height(30.dp))
                HorizontalDivider(color = Hairline)
                Spacer(Modifier.height(16.dp))
                Text(
                    "YulCaribe Android · native Compose design prototype",
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsHeading(text: String) {
    Text(
        text,
        color = Cyan,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun SettingValueRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = TextPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            color = TextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp
        )
    }
    HorizontalDivider(color = Hairline.copy(alpha = 0.72f))
}

@Composable
private fun SettingToggle(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp)
            Text(
                detail,
                color = TextSecondary,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 2.dp, end = 16.dp)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Void,
                checkedTrackColor = Cyan,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceHigh
            )
        )
    }
    HorizontalDivider(color = Hairline.copy(alpha = 0.72f))
}
