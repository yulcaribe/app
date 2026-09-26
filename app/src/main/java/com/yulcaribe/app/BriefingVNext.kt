package com.yulcaribe.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit

@Composable
internal fun BriefingScreenV(store: YcLocalStore) {
    val scope = rememberCoroutineScope()
    var from by rememberSaveable { mutableStateOf("LTAI") }
    var to by rememberSaveable { mutableStateOf("EDDB") }
    var etd by rememberSaveable {
        mutableStateOf(Instant.now().truncatedTo(ChronoUnit.MINUTES).toString())
    }
    var fl by rememberSaveable { mutableStateOf("360") }
    var route by rememberSaveable { mutableStateOf("") }
    var requestToken by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var briefing by remember { mutableStateOf<Briefing?>(null) }
    var modelWinds by remember { mutableStateOf<List<ModelWindPoint>>(emptyList()) }
    var modelWindLoading by remember { mutableStateOf(false) }
    var modelWindError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedRoutes by remember { mutableStateOf<List<YcLocalStore.RoutePreset>>(emptyList()) }
    var recentRoutes by remember { mutableStateOf<List<YcLocalStore.RoutePreset>>(emptyList()) }
    var routeListToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(routeListToken) {
        savedRoutes = store.savedRoutes(20)
        recentRoutes = store.recentRoutes(10)
    }

    LaunchedEffect(requestToken) {
        if (requestToken == 0) return@LaunchedEffect
        loading = true
        error = null
        val currentFl = fl.toIntOrNull() ?: 360
        val result = runCatching {
            YcApi.briefing(
                from.trim().uppercase(),
                to.trim().uppercase(),
                etd.trim(),
                currentFl,
                route.trim().uppercase()
            )
        }

        result.fold(
            onSuccess = { data ->
                briefing = data
                modelWinds = emptyList()
                modelWindError = null
                store.recordRecentRoute(from, to, currentFl, route)
                routeListToken++
            },
            onFailure = { error = it.message ?: "Pilot Briefing request failed." }
        )
        loading = false
    }

    LaunchedEffect(briefing) {
        val data = briefing ?: return@LaunchedEffect
        modelWindLoading = true
        modelWindError = null
        runCatching { YcApi.modelWind(data) }
            .onSuccess { modelWinds = it }
            .onFailure { modelWindError = it.message ?: "Model wind unavailable." }
        modelWindLoading = false
    }

    fun applyPreset(preset: YcLocalStore.RoutePreset) {
        from = preset.from
        to = preset.to
        fl = preset.fl.toString()
        route = preset.route
        etd = Instant.now().truncatedTo(ChronoUnit.MINUTES).toString()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 38.dp)
    ) {
        item {
            BrandBarV("PILOT BRIEFING")
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "PILOT BRIEFING",
                    color = YcText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp
                )
                Spacer(Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BriefFieldV("FROM", from, { from = it.uppercase() }, Modifier.weight(1f))
                    BriefFieldV("TO", to, { to = it.uppercase() }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BriefFieldV("ETD UTC", etd, { etd = it }, Modifier.weight(1.45f))
                    BriefFieldV("FL", fl, { fl = it }, Modifier.weight(0.55f))
                }
                Spacer(Modifier.height(10.dp))
                KickerV("OFP / ROUTE · OPTIONAL")
                OutlinedTextField(
                    value = route,
                    onValueChange = { route = it.uppercase() },
                    modifier = Modifier.fillMaxWidth().height(92.dp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = YcText
                    ),
                    shape = RoundedCornerShape(6.dp)
                )

                Spacer(Modifier.height(12.dp))
                KickerV("SAVED / RECENT")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    savedRoutes.forEach { preset ->
                        RouteChipV(
                            label = "★ " + (preset.name ?: "${preset.from}-${preset.to}"),
                            selected = from == preset.from && to == preset.to && fl == preset.fl.toString()
                        ) { applyPreset(preset) }
                    }
                    recentRoutes.take(5).forEach { preset ->
                        RouteChipV(
                            label = "${preset.from}→${preset.to} · FL${preset.fl}",
                            selected = false
                        ) { applyPreset(preset) }
                    }
                    if (savedRoutes.isEmpty() && recentRoutes.isEmpty()) {
                        Text(
                            "Routes used here will stay on this device.",
                            color = YcMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                store.saveRoute(
                                    name = "${from.trim().uppercase()}-${to.trim().uppercase()}",
                                    from = from,
                                    to = to,
                                    fl = fl.toIntOrNull() ?: 360,
                                    route = route
                                )
                                routeListToken++
                            }
                        }
                    ) {
                        Text("SAVE ROUTE", color = YcCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                    }
                }

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
                    ErrorBoxV(error!!)
                }
                Spacer(Modifier.height(22.dp))
            }
        }

        briefing?.let { data ->
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    BriefingMapV(data, modelWinds, modelWindLoading, modelWindError)
                    Spacer(Modifier.height(18.dp))

                    BriefingTableV(
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

                    when {
                        modelWindLoading -> BriefingTableV(
                            "MODEL WIND",
                            "GFS 0.25° · LOADING",
                            listOf("STATUS" to "Route wind guidance is loading…")
                        )
                        modelWinds.isNotEmpty() -> {
                            val picks = listOf(
                                0,
                                modelWinds.lastIndex / 4,
                                modelWinds.lastIndex / 2,
                                modelWinds.lastIndex * 3 / 4,
                                modelWinds.lastIndex
                            ).distinct().map { modelWinds[it] }
                            BriefingTableV(
                                "MODEL WIND",
                                "GFS 0.25°",
                                picks.map { point ->
                                    val pct = (point.progress * 100).toInt()
                                    val dir = point.directionDeg.toInt().toString().padStart(3, '0')
                                    val tail = point.tailwindKt?.let {
                                        (if (it >= 0) "TW " else "HW ") + kotlin.math.abs(it).toInt() + "kt"
                                    }.orEmpty()
                                    "$pct% ROUTE" to "$dir° / ${point.speedKt.toInt()}kt" +
                                        if (tail.isNotBlank()) " · $tail" else ""
                                }
                            )
                        }
                        modelWindError != null -> BriefingTableV(
                            "MODEL WIND",
                            "UNAVAILABLE",
                            listOf("STATUS" to modelWindError!!)
                        )
                    }

                    if (data.routeWarnings.isNotEmpty()) {
                        BriefingTableV(
                            "ROUTE WARNINGS",
                            "PARSER",
                            data.routeWarnings.mapIndexed { i, value -> "WARNING ${i + 1}" to value }
                        )
                    }

                    BriefingTableV(
                        "SIGMET",
                        "ROUTE RELEVANCE",
                        listOf("HAZARDS" to data.hazards.size.toString(), "SOURCE" to data.source)
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

                    BriefingTableV(
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
                                        (station.flightCategory?.let { " · $it" } ?: ""),
                                    color = YcMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp
                                )
                            }
                            station.metarRaw?.let {
                                Text(
                                    "METAR  $it",
                                    color = YcText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            station.tafRaw?.let {
                                Text(
                                    "TAF    $it",
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
private fun BriefFieldV(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier
) {
    Column(modifier) {
        KickerV(label)
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
private fun RouteChipV(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) YcCyan.copy(alpha = 0.14f) else YcSurfaceHigh,
        border = BorderStroke(1.dp, if (selected) YcCyan else YcHairline),
        shape = RoundedCornerShape(5.dp)
    ) {
        Text(
            label,
            color = if (selected) YcCyanSoft else YcText,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun BriefingMapV(
    data: Briefing,
    modelWinds: List<ModelWindPoint>,
    modelWindLoading: Boolean,
    modelWindError: String?
) {
    var fullScreen by remember { mutableStateOf(false) }
    var layersOpen by remember { mutableStateOf(false) }
    var showRoute by remember { mutableStateOf(true) }
    var showStations by remember { mutableStateOf(true) }
    var showSigmet by remember { mutableStateOf(true) }
    var showModelWind by remember { mutableStateOf(true) }
    var showWafs by remember { mutableStateOf(false) }
    var showNotam by remember { mutableStateOf(false) }
    var showCharts by remember { mutableStateOf(false) }
    var showAirports by remember { mutableStateOf(true) }
    var showNavaids by remember { mutableStateOf(true) }
    var showAirways by remember { mutableStateOf(true) }
    var showSid by remember { mutableStateOf(true) }
    var showStar by remember { mutableStateOf(true) }
    var showAirspace by remember { mutableStateOf(true) }
    var chartGeo by remember { mutableStateOf(emptyGeoJsonV()) }
    var notamGeo by remember { mutableStateOf(emptyGeoJsonV()) }
    var wafsFrame by remember { mutableStateOf<WafsFrame?>(null) }
    var wafsProduct by remember { mutableStateOf("edr") }
    var wafsOpacity by remember { mutableFloatStateOf(0.48f) }

    val chartSet = buildSet {
        if (showCharts) {
            if (showAirports) add("airport")
            if (showNavaids) add("navaid")
            if (showAirways) add("airway")
            if (showSid) add("sid")
            if (showStar) add("star")
            if (showAirspace) add("airspace")
        }
    }

    LaunchedEffect(fullScreen, chartSet, data.route) {
        chartGeo = if (fullScreen && chartSet.isNotEmpty()) {
            runCatching { YcApi.chartViewport(routeViewportV(data.route), chartSet) }
                .getOrDefault(emptyGeoJsonV())
        } else emptyGeoJsonV()
    }

    LaunchedEffect(fullScreen, showNotam, data.route, data.etdUtc) {
        notamGeo = if (fullScreen && showNotam) {
            val at = data.etdUtc?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
            runCatching { YcApi.notamViewport(routeViewportV(data.route), at) }
                .getOrDefault(emptyGeoJsonV())
        } else emptyGeoJsonV()
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
                nearestSupportedWafsLevelV(wafsProduct, data.cruiseFl),
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
            routeGeoJson = routeGeoJsonV(data.route),
            briefingGeoJson = briefingOverlayGeoJsonV(
                data,
                includeStations = true,
                includeHazards = true,
                winds = modelWinds
            ),
            routePoints = data.route,
            fitRoute = true,
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            color = YcSurface.copy(alpha = 0.86f),
            border = BorderStroke(1.dp, YcHairline),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
        ) {
            Text(
                when {
                    modelWindLoading -> "ROUTE · MODEL WIND LOADING…"
                    modelWindError != null && modelWinds.isEmpty() -> "ROUTE · MODEL WIND UNAVAILABLE"
                    else -> "ROUTE · SIGMET · STATIONS · MODEL WIND"
                },
                color = YcCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }

        Surface(
            onClick = { fullScreen = true },
            color = YcSurface.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, YcHairline),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.OpenInFull, null, tint = YcCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("EXPAND", color = YcText, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
            }
        }
    }

    if (fullScreen) {
        Dialog(
            onDismissRequest = { fullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(Modifier.fillMaxSize().background(YcVoid)) {
                NativeAviationMapV2(
                    center = null,
                    zoom = 4.0,
                    interactive = true,
                    chartsGeoJson = chartGeo,
                    notamGeoJson = notamGeo,
                    routeGeoJson = if (showRoute) routeGeoJsonV(data.route) else emptyGeoJsonV(),
                    briefingGeoJson = briefingOverlayGeoJsonV(
                        data,
                        includeStations = showStations,
                        includeHazards = showSigmet,
                        winds = if (showModelWind) modelWinds else emptyList()
                    ),
                    routePoints = data.route,
                    fitRoute = true,
                    wafsFrame = wafsFrame,
                    wafsOpacity = wafsOpacity,
                    modifier = Modifier.fillMaxSize()
                )

                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { fullScreen = false },
                        color = YcSurface.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, YcHairline),
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
                        color = YcSurface.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, YcHairline),
                        shape = RoundedCornerShape(7.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Layers, null, tint = YcCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("LAYERS", color = YcText, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                        }
                    }
                }

                if (layersOpen) {
                    LazyColumn(
                        Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 68.dp, end = 12.dp)
                            .width(286.dp)
                            .background(YcSurface.copy(alpha = 0.97f), RoundedCornerShape(9.dp))
                            .border(1.dp, YcHairline, RoundedCornerShape(9.dp)),
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        item {
                            Text("BRIEFING LAYERS", color = YcText, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            LayerToggleV("Route", showRoute) { showRoute = it }
                            LayerToggleV("Stations", showStations) { showStations = it }
                            LayerToggleV("SIGMET", showSigmet) { showSigmet = it }
                            LayerToggleV("Model Wind", showModelWind) { showModelWind = it }
                            LayerToggleV("WAFS", showWafs) { showWafs = it }
                            LayerToggleV("NOTAM", showNotam) { showNotam = it }
                            LayerToggleV("Charts", showCharts) { showCharts = it }
                            if (showCharts) {
                                LayerToggleV("Airports", showAirports) { showAirports = it }
                                LayerToggleV("Navaids", showNavaids) { showNavaids = it }
                                LayerToggleV("Airways", showAirways) { showAirways = it }
                                LayerToggleV("SID", showSid) { showSid = it }
                                LayerToggleV("STAR", showStar) { showStar = it }
                                LayerToggleV("Airspace", showAirspace) { showAirspace = it }
                            }

                            if (showWafs) {
                                Spacer(Modifier.height(7.dp))
                                KickerV("WAFS PRODUCT")
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    listOf(
                                        "edr" to "EDR",
                                        "icing" to "ICE",
                                        "cbextent" to "CB EXT",
                                        "cbtop" to "CB TOP",
                                        "wind" to "WIND"
                                    ).forEach { item ->
                                        OptionChipV(item.second, wafsProduct == item.first) {
                                            wafsProduct = item.first
                                        }
                                    }
                                }
                                Spacer(Modifier.height(7.dp))
                                KickerV("WAFS OPACITY · ${(wafsOpacity * 100).toInt()}%")
                                Slider(
                                    value = wafsOpacity,
                                    onValueChange = { wafsOpacity = it },
                                    valueRange = 0.10f..0.85f
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun briefingOverlayGeoJsonV(
    data: Briefing,
    includeStations: Boolean,
    includeHazards: Boolean,
    winds: List<ModelWindPoint> = emptyList()
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
                        JSONObject().put("type", "Point").put("coordinates", JSONArray().put(lon).put(lat))
                    )
                    .put(
                        "properties",
                        JSONObject().put("kind", "station").put("label", station.icao).put("role", station.role)
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

    winds.forEachIndexed { index, wind ->
        val prominent = index == 0 || index == winds.lastIndex ||
            index % kotlin.math.max(1, winds.size / 8) == 0
        features.put(
            JSONObject()
                .put("type", "Feature")
                .put(
                    "geometry",
                    JSONObject().put("type", "Point").put("coordinates", JSONArray().put(wind.lon).put(wind.lat))
                )
                .put(
                    "properties",
                    JSONObject()
                        .put("kind", "wind")
                        .put("rotation", (wind.directionDeg + 180.0) % 360.0)
                        .put("speedLabel", if (prominent) wind.speedKt.toInt().toString() + "kt" else "")
                        .put("direction", wind.directionDeg)
                        .put("speedKt", wind.speedKt)
                        .put("tailwindKt", wind.tailwindKt)
                        .put("crosswindKt", wind.crosswindKt)
                        .put("progress", wind.progress)
                )
        )
    }

    return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
}

@Composable
private fun BriefingTableV(title: String, subtitle: String, rows: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        SectionHeaderV(title, subtitle, YcCyan)
        rows.forEach { DataRowV(it.first, it.second) }
    }
}
