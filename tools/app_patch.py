from pathlib import Path

p = Path('app/src/main/java/com/yulcaribe/app/YulCaribeApp.kt')
s = p.read_text()

def must_replace(old, new, count=-1):
    global s
    if old not in s:
        raise SystemExit('Expected source block not found:\n' + old[:220])
    s = s.replace(old, new, count)

if 'import android.content.Context\n' not in s:
    s = s.replace('package com.yulcaribe.app\n\n', 'package com.yulcaribe.app\n\nimport android.content.Context\n')
if 'import androidx.compose.ui.platform.LocalContext\n' not in s:
    s = s.replace('import androidx.compose.ui.platform.LocalFocusManager\n', 'import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.LocalFocusManager\n')

must_replace(
'''data class AppPreferences(
    val showRaw: Boolean = true,
    val showDecoded: Boolean = true,
    val showExplanation: Boolean = true
)''',
'''data class AppPreferences(
    val showRaw: Boolean = true,
    val showDecoded: Boolean = true,
    val showExplanation: Boolean = true,
    val mainAirportIcao: String = "LTFM"
)''')

must_replace(
'''fun YulCaribeApp() {
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var prefs by remember { mutableStateOf(AppPreferences()) }''',
'''fun YulCaribeApp() {
    val context = LocalContext.current
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var prefs by remember { mutableStateOf(loadAppPreferences(context)) }

    fun updatePreferences(value: AppPreferences) {
        prefs = value
        saveAppPreferences(context, value)
    }''')

s = s.replace('containerColor = Color(0xF20A0E13),', 'containerColor = YcSurface.copy(alpha = 0.96f),')
must_replace('Destination.NavMap -> NavMapScreen()', 'Destination.NavMap -> NavMapScreen(prefs)')
must_replace('Destination.Settings -> SettingsScreen(prefs) { prefs = it }', 'Destination.Settings -> SettingsScreen(prefs, ::updatePreferences)')

must_replace(
'''    LaunchedEffect(Unit) {
        apiOk = YcApi.catalogOk()
        runCatching { YcApi.airportDetail("LTFM") }
            .getOrNull()
            ?.let { activeAirport = it }
    }''',
'''    LaunchedEffect(prefs.mainAirportIcao) {
        apiOk = YcApi.catalogOk()
        runCatching { YcApi.airportDetail(prefs.mainAirportIcao) }
            .getOrNull()
            ?.let { activeAirport = it }
    }''', 1)

must_replace('private fun NavMapScreen() {', 'private fun NavMapScreen(prefs: AppPreferences) {')
must_replace(
'''    LaunchedEffect(Unit) {
        apiOk = YcApi.catalogOk()
        runCatching { YcApi.airportDetail("LTFM") }.getOrNull()?.let {
            centerAirport = it
            viewport = airportViewport(it, 7)
        }
    }''',
'''    LaunchedEffect(prefs.mainAirportIcao) {
        apiOk = YcApi.catalogOk()
        runCatching { YcApi.airportDetail(prefs.mainAirportIcao) }.getOrNull()?.let {
            centerAirport = it
            viewport = airportViewport(it, 7)
        }
    }''')

# Main web uses min zoom 4.2 and 32 ms render frames. Android viewport zoom is integer.
s = s.replace('if (viewport.zoom >= 4) {', 'if (viewport.zoom >= 5) {')
s = s.replace('delay(80)', 'delay(32)')

# Light appearance for app chrome/panels. Keep the aviation map itself visually dark/technical.
s = s.replace('Color(0xFF0B1016)', 'YcSurface')
s = s.replace('Color(0xF20B1016)', 'YcSurface.copy(alpha = 0.95f)')
s = s.replace('Color(0xE60B1016)', 'YcSurface.copy(alpha = 0.90f)')
s = s.replace('Color(0xEC0B1016)', 'YcSurface.copy(alpha = 0.93f)')
s = s.replace('Color(0xF50B1016)', 'YcSurface.copy(alpha = 0.96f)')
s = s.replace('Color(0xD90B1016)', 'YcSurface.copy(alpha = 0.85f)')

settings_marker = '''private fun SettingsScreen(
    prefs: AppPreferences,
    onChange: (AppPreferences) -> Unit
) {
    LazyColumn('''
settings_replacement = '''private fun SettingsScreen(
    prefs: AppPreferences,
    onChange: (AppPreferences) -> Unit
) {
    val focus = LocalFocusManager.current
    var airportQuery by rememberSaveable(prefs.mainAirportIcao) { mutableStateOf(prefs.mainAirportIcao) }
    var airportMatches by remember { mutableStateOf<List<Airport>>(emptyList()) }
    var airportFocused by remember { mutableStateOf(false) }
    var airportBusy by remember { mutableStateOf(false) }
    var airportGeneration by remember { mutableIntStateOf(0) }

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
        delay(140)
        val rows = runCatching { YcApi.airportSearch(normalized, 20) }.getOrDefault(emptyList())
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
    }

    LazyColumn('''
must_replace(settings_marker, settings_replacement)

must_replace(
'''                Kicker("AIRPORT RESULTS")''',
'''                Kicker("MAIN AIRPORT")
                Text(
                    "Home background and Map startup center use this airport.",
                    color = YcMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(9.dp))

                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = airportQuery,
                        onValueChange = { airportQuery = it.uppercase() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { airportFocused = it.isFocused },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = YcCyan) },
                        trailingIcon = {
                            if (airportBusy) Text("…", color = YcCyan, fontSize = 18.sp)
                        },
                        placeholder = { Text("ICAO / IATA / city", color = YcMuted) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { airportMatches.firstOrNull()?.let(::chooseMainAirport) }
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    DropdownMenu(
                        expanded = airportFocused && airportQuery.trim().length >= 2,
                        onDismissRequest = { airportFocused = false },
                        modifier = Modifier
                            .fillMaxWidth(0.94f)
                            .heightIn(max = 300.dp)
                            .background(YcSurface),
                        properties = PopupProperties(focusable = false)
                    ) {
                        if (airportBusy && airportMatches.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Searching…", color = YcMuted) },
                                onClick = {}
                            )
                        } else if (airportMatches.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No airport match.", color = YcMuted) },
                                onClick = {}
                            )
                        } else {
                            airportMatches.forEach { airport ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                airport.icao + (airport.iata?.let { " · " + it } ?: ""),
                                                color = YcCyan,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                listOfNotNull(airport.name, airport.city).joinToString(" · "),
                                                color = YcMuted,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    },
                                    onClick = { chooseMainAirport(airport) }
                                )
                            }
                        }
                    }
                }

                Text(
                    "Current: " + prefs.mainAirportIcao,
                    color = YcCyanSoft,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 7.dp)
                )

                Spacer(Modifier.height(26.dp))
                Kicker("AIRPORT RESULTS")''')

if 'private fun loadAppPreferences(context: Context)' not in s:
    s = s.rstrip() + '''

private fun loadAppPreferences(context: Context): AppPreferences {
    val store = context.getSharedPreferences("yulcaribe_preferences", Context.MODE_PRIVATE)
    return AppPreferences(
        showRaw = store.getBoolean("show_raw", true),
        showDecoded = store.getBoolean("show_decoded", true),
        showExplanation = store.getBoolean("show_explanation", true),
        mainAirportIcao = store.getString("main_airport", "LTFM")
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.matches(Regex("^[A-Z0-9]{3,4}$")) }
            ?: "LTFM"
    )
}

private fun saveAppPreferences(context: Context, prefs: AppPreferences) {
    context.getSharedPreferences("yulcaribe_preferences", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("show_raw", prefs.showRaw)
        .putBoolean("show_decoded", prefs.showDecoded)
        .putBoolean("show_explanation", prefs.showExplanation)
        .putString("main_airport", prefs.mainAirportIcao.uppercase())
        .apply()
}
''' + '\n'

p.write_text(s)
