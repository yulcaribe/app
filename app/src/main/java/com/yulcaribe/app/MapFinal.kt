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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.temporal.ChronoUnit

private enum class FinalMapPanel { Charts, Wafs }

private data class FinalWafsProduct(
    val id: String,
    val label: String,
    val levels: List<Int>?
)

private val FINAL_WAFS_PRODUCTS = listOf(
    FinalWafsProduct("edr", "EDR", listOf(140, 180, 240, 270, 300, 340, 390, 450)),
    FinalWafsProduct("icing", "ICING", listOf(60, 100, 140, 180, 240, 300)),
    FinalWafsProduct("cbextent", "CB EXTENT", null),
    FinalWafsProduct("cbtop", "CB TOPS", null),
    FinalWafsProduct("wind", "WIND", listOf(100, 140, 180, 240, 270, 300, 340, 390, 450))
)

private fun finalWafsConfig(id: String): FinalWafsProduct =
    FINAL_WAFS_PRODUCTS.firstOrNull { it.id == id } ?: FINAL_WAFS_PRODUCTS.first()

private fun finalNearestWafsLevel(product: FinalWafsProduct, requested: Int): Int =
    product.levels?.minByOrNull { kotlin.math.abs(it - requested) } ?: requested

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapScreenFinalV(
    prefs: VPreferences,
    onPrefsChange: (VPreferences) -> Unit
) {
    var centerAirport by remember { mutableStateOf(ltaiFallbackV) }
    var viewport by remember { mutableStateOf(airportViewportV(ltaiFallbackV, 7)) }
    var layers by remember {
        mutableStateOf(
            VMapLayers(
                charts = prefs.mapCharts,
                notam = prefs.mapNotam,
                wafs = prefs.mapWafs,
                adsb = prefs.mapAdsb
            )
        )
    }
    var panel by remember { mutableStateOf<FinalMapPanel?>(null) }

    var chartGeo by remember { mutableStateOf(emptyGeoJsonV()) }
    var notamGeo by remember { mutableStateOf(emptyGeoJsonV()) }
    var flightsGeo by remember { mutableStateOf(emptyGeoJsonV()) }
    var wafsFrames by remember { mutableStateOf<Map<String, WafsFrame>>(emptyMap()) }

    var chartsError by remember { mutableStateOf<String?>(null) }
    var notamError by remember { mutableStateOf<String?>(null) }
    var wafsErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var adsbError by remember { mutableStateOf<String?>(null) }

    var chartsLoading by remember { mutableStateOf(false) }
    var notamLoading by remember { mutableStateOf(false) }
    var wafsLoading by remember { mutableStateOf(false) }
    var adsbLoading by remember { mutableStateOf(false) }

    var refreshToken by remember { mutableIntStateOf(0) }
    var loadGeneration by remember { mutableIntStateOf(0) }
    var wafsGeneration by remember { mutableIntStateOf(0) }
    var timeOffsetHours by remember { mutableFloatStateOf(0f) }
    var timelineExpanded by rememberSaveable { mutableStateOf(false) }

    val allWafsIds = remember { FINAL_WAFS_PRODUCTS.map { it.id }.toSet() }
    var wafsSelectedProduct by remember { mutableStateOf("edr") }
    var wafsActiveProducts by remember {
        mutableStateOf(if (prefs.mapWafs) allWafsIds else emptySet())
    }
    var wafsLevels by remember {
        mutableStateOf(
            mapOf(
                "edr" to 340,
                "icing" to 240,
                "wind" to 340
            )
        )
    }
    var wafsOpacities by remember {
        mutableStateOf(FINAL_WAFS_PRODUCTS.associate { it.id to 0.48f })
    }

    val wafsTopError = wafsErrors.entries.firstOrNull()?.let { (id, message) ->
        finalWafsConfig(id).label + " · " + message
    }

    val adsbBuffer = remember { AdsbTrackBuffer() }

    fun persistTopLevel(next: VMapLayers) {
        layers = next
        onPrefsChange(
            prefs.copy(
                mapCharts = next.charts,
                mapNotam = next.notam,
                mapWafs = next.wafs,
                mapAdsb = next.adsb
            )
        )
    }

    fun setWafsProductEnabled(product: String, enabled: Boolean) {
        val next = if (enabled) wafsActiveProducts + product else wafsActiveProducts - product
        wafsActiveProducts = next
        if (next.isEmpty()) {
            persistTopLevel(layers.copy(wafs = false))
            wafsFrames = emptyMap()
        } else if (!layers.wafs) {
            persistTopLevel(layers.copy(wafs = true))
        }
    }

    fun setWafsLevel(product: String, level: Int) {
        val cfg = finalWafsConfig(product)
        val normalized = finalNearestWafsLevel(cfg, level)
        wafsLevels = wafsLevels + (product to normalized)
    }

    LaunchedEffect(prefs.mainAirportIcao) {
        runCatching { YcApi.airportDetail(prefs.mainAirportIcao) }
            .getOrNull()
            ?.let {
                centerAirport = it
                viewport = airportViewportV(it, 7)
            }
    }

    LaunchedEffect(viewport, layers, refreshToken, timeOffsetHours) {
        val generation = ++loadGeneration
        val validTime = Instant.now().plus(timeOffsetHours.toLong(), ChronoUnit.HOURS)

        chartsLoading = layers.charts && layers.chartSet().isNotEmpty()
        notamLoading = layers.notam && viewport.zoom >= 5

        coroutineScope {
            val chartTask = async {
                if (layers.charts && layers.chartSet().isNotEmpty()) {
                    runCatching { YcApi.chartViewport(viewport, layers.chartSet()) }
                } else Result.success(emptyGeoJsonV())
            }
            val notamTask = async {
                if (layers.notam && viewport.zoom >= 5) {
                    runCatching { YcApi.notamViewport(viewport, validTime) }
                } else Result.success(emptyGeoJsonV())
            }

            val chartResult = chartTask.await()
            val notamResult = notamTask.await()
            if (generation != loadGeneration) return@coroutineScope

            chartResult.fold(
                onSuccess = { chartGeo = it; chartsError = null },
                onFailure = { chartsError = it.message ?: "CHARTS request failed." }
            )
            chartsLoading = false

            notamResult.fold(
                onSuccess = { notamGeo = it; notamError = null },
                onFailure = { notamError = it.message ?: "NOTAM request failed." }
            )
            notamLoading = false
        }
    }

    LaunchedEffect(layers.wafs, wafsActiveProducts, wafsLevels, refreshToken, timeOffsetHours) {
        val generation = ++wafsGeneration
        if (!layers.wafs || wafsActiveProducts.isEmpty()) {
            wafsFrames = emptyMap()
            wafsErrors = emptyMap()
            wafsLoading = false
            return@LaunchedEffect
        }

        val validTime = Instant.now().plus(timeOffsetHours.toLong(), ChronoUnit.HOURS)
        wafsLoading = true
        coroutineScope {
            val tasks = wafsActiveProducts.associateWith { productId ->
                async {
                    val cfg = finalWafsConfig(productId)
                    val requested = wafsLevels[productId] ?: cfg.levels?.firstOrNull() ?: 340
                    val level = finalNearestWafsLevel(cfg, requested)
                    runCatching { YcApi.wafsFrame(productId, level, validTime) }
                }
            }

            val nextFrames = wafsFrames.filterKeys { it in wafsActiveProducts }.toMutableMap()
            val nextErrors = mutableMapOf<String, String>()

            tasks.forEach { (productId, task) ->
                task.await().fold(
                    onSuccess = { frame -> nextFrames[productId] = frame },
                    onFailure = { failure ->
                        nextFrames.remove(productId)
                        nextErrors[productId] = failure.message ?: "WAFS request failed."
                    }
                )
            }

            if (generation == wafsGeneration) {
                wafsFrames = nextFrames
                wafsErrors = nextErrors
                wafsLoading = false
            }
        }
    }

    LaunchedEffect(viewport, layers.adsb, refreshToken) {
        if (!layers.adsb) {
            adsbBuffer.clear()
            flightsGeo = emptyGeoJsonV()
            adsbError = null
            adsbLoading = false
            return@LaunchedEffect
        }

        while (true) {
            if (viewport.zoom >= 4) {
                adsbLoading = flightsGeo == emptyGeoJsonV() && adsbError == null
                runCatching { YcApi.adsb(viewport) }
                    .onSuccess {
                        adsbBuffer.ingest(it)
                        adsbError = null
                    }
                    .onFailure {
                        adsbError = it.message ?: "ADS-B request failed."
                    }
                adsbLoading = false
            }
            delay(2000)
        }
    }

    LaunchedEffect(layers.adsb) {
        if (!layers.adsb) return@LaunchedEffect
        while (true) {
            val rendered = adsbBuffer.render()
            if (rendered.isNotEmpty()) flightsGeo = YcApi.flightsGeoJson(rendered)
            delay(32)
        }
    }

    Box(Modifier.fillMaxSize().background(YcVoid)) {
        NativeAviationMapV2(
            center = centerAirport,
            zoom = 7.0,
            interactive = true,
            chartsGeoJson = chartGeo,
            notamGeoJson = notamGeo,
            aircraftGeoJson = flightsGeo,
            wafsFrames = wafsFrames.values.toList(),
            wafsOpacities = wafsOpacities,
            onViewportChanged = { viewport = it },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(YcVoid.copy(alpha = 0.72f), Color.Transparent)
                    )
                )
        ) {
            BrandBarV("MAP")
        }

        Row(
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 56.dp, start = 12.dp, end = 12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FinalMapControlChip(
                label = "CHARTS",
                active = layers.charts,
                error = chartsError,
                loading = chartsLoading,
                hasOptions = true,
                onClick = { panel = FinalMapPanel.Charts }
            )
            FinalMapControlChip(
                label = "NOTAM",
                active = layers.notam,
                error = notamError,
                loading = notamLoading,
                onClick = { persistTopLevel(layers.copy(notam = !layers.notam)) }
            )
            FinalMapControlChip(
                label = "WAFS",
                active = layers.wafs,
                error = wafsTopError,
                loading = wafsLoading,
                hasOptions = true,
                onClick = { panel = FinalMapPanel.Wafs }
            )
            FinalMapControlChip(
                label = "ADS-B",
                active = layers.adsb,
                error = adsbError,
                loading = adsbLoading,
                onClick = { persistTopLevel(layers.copy(adsb = !layers.adsb)) }
            )
            Surface(
                onClick = { refreshToken++ },
                color = YcSurface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, YcHairline),
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (timelineExpanded) 168.dp else 66.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (layers.notam && viewport.zoom < 5) {
                FinalMapNotice("Zoom in to show NOTAM · z5+")
            }
            if (layers.charts && viewport.zoom < 5) {
                FinalMapNotice("Zoom in to show chart data · z5+")
            }
            if (layers.adsb && viewport.zoom < 4) {
                FinalMapNotice("Zoom in to load ADS-B · z4+")
            }
            if (layers.adsb && adsbError != null) {
                FinalMapNotice("ADS-B · ${adsbError!!}", error = true)
            }
        }

        if (!timelineExpanded) {
            Surface(
                onClick = { timelineExpanded = true },
                color = YcSurface.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, YcHairline),
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
                        finalTimelineLabel(timeOffsetHours),
                        color = YcCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("TIMELINE", color = YcMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
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
                    .background(YcSurface.copy(alpha = 0.96f), RoundedCornerShape(9.dp))
                    .border(1.dp, YcHairline, RoundedCornerShape(9.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        finalTimelineLabel(timeOffsetHours),
                        color = YcCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text("UTC TIMELINE", color = YcMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                    Surface(onClick = { timelineExpanded = false }, color = Color.Transparent) {
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
                    FinalTimeButton("-1h", Icons.Outlined.Remove) {
                        timeOffsetHours = (timeOffsetHours - 1f).coerceAtLeast(-24f)
                    }
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        onClick = { timeOffsetHours = 0f },
                        color = YcSurfaceHigh,
                        border = BorderStroke(1.dp, YcHairline),
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
                    FinalTimeButton("+1h", Icons.Outlined.Add) {
                        timeOffsetHours = (timeOffsetHours + 1f).coerceAtMost(24f)
                    }
                }

                Slider(
                    value = timeOffsetHours,
                    onValueChange = { timeOffsetHours = it },
                    valueRange = -24f..24f,
                    steps = 47
                )
            }
        }
    }

    if (panel == FinalMapPanel.Charts) {
        ModalBottomSheet(
            onDismissRequest = { panel = null },
            containerColor = YcSurface,
            contentColor = YcText
        ) {
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                item {
                    Text("CHARTS", color = YcText, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    FinalZoomToggle("Charts", "master", layers.charts) {
                        persistTopLevel(layers.copy(charts = it))
                    }
                    FinalZoomToggle("Airports", "z5+", layers.airports) { layers = layers.copy(airports = it) }
                    FinalZoomToggle("Navaids", "z6+", layers.navaids) { layers = layers.copy(navaids = it) }
                    FinalZoomToggle("Waypoints", "z8+", layers.waypoints) { layers = layers.copy(waypoints = it) }
                    FinalZoomToggle("Airways", "z5+", layers.airways) { layers = layers.copy(airways = it) }
                    FinalZoomToggle("SID", "z8+", layers.sid) { layers = layers.copy(sid = it) }
                    FinalZoomToggle("STAR", "z8+", layers.star) { layers = layers.copy(star = it) }
                    FinalZoomToggle("Airspace", "z5+", layers.airspace) { layers = layers.copy(airspace = it) }
                    chartsError?.let {
                        Spacer(Modifier.padding(top = 8.dp))
                        ErrorBoxV("CHARTS · $it")
                    }
                    Spacer(Modifier.padding(bottom = 24.dp))
                }
            }
        }
    }

    if (panel == FinalMapPanel.Wafs) {
        val selectedConfig = finalWafsConfig(wafsSelectedProduct)
        val selectedActive = wafsSelectedProduct in wafsActiveProducts
        val selectedLevels = selectedConfig.levels
        val selectedFl = selectedLevels?.let { levels ->
            finalNearestWafsLevel(selectedConfig, wafsLevels[wafsSelectedProduct] ?: levels.first())
        }
        val selectedOpacity = wafsOpacities[wafsSelectedProduct] ?: 0.48f

        ModalBottomSheet(
            onDismissRequest = { panel = null },
            containerColor = YcSurface,
            contentColor = YcText
        ) {
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                item {
                    Text("WAFS", color = YcText, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    LayerToggleV("WAFS", layers.wafs) { enabled ->
                        persistTopLevel(layers.copy(wafs = enabled))
                        wafsActiveProducts = if (enabled) allWafsIds else emptySet()
                        if (!enabled) {
                            wafsFrames = emptyMap()
                            wafsErrors = emptyMap()
                        }
                    }

                    KickerV("PRODUCT")
                    Text(
                        "Tap a product to edit it. Use the layer switch below to show or hide it.",
                        color = YcMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FINAL_WAFS_PRODUCTS.forEach { product ->
                            FinalWafsProductChip(
                                label = product.label,
                                active = product.id in wafsActiveProducts,
                                selected = product.id == wafsSelectedProduct,
                                onClick = { wafsSelectedProduct = product.id }
                            )
                        }
                    }

                    FinalZoomToggle(
                        selectedConfig.label,
                        if (selectedActive) "layer on" else "layer off",
                        selectedActive
                    ) { enabled ->
                        setWafsProductEnabled(wafsSelectedProduct, enabled)
                    }

                    if (selectedLevels != null && selectedFl != null) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                KickerV("FLIGHT LEVEL")
                                Text(
                                    "FL$selectedFl",
                                    color = YcText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                )
                            }
                            FinalWafsStepButton(Icons.Outlined.Remove, "Previous level") {
                                val index = selectedLevels.indexOf(selectedFl)
                                if (index > 0) setWafsLevel(wafsSelectedProduct, selectedLevels[index - 1])
                            }
                            Spacer(Modifier.width(6.dp))
                            FinalWafsStepButton(Icons.Outlined.Add, "Next level") {
                                val index = selectedLevels.indexOf(selectedFl)
                                if (index >= 0 && index < selectedLevels.lastIndex) {
                                    setWafsLevel(wafsSelectedProduct, selectedLevels[index + 1])
                                }
                            }
                        }

                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            selectedLevels.forEach { level ->
                                OptionChipV("FL$level", selectedFl == level) {
                                    setWafsLevel(wafsSelectedProduct, level)
                                }
                            }
                        }
                    } else {
                        KickerV("FLIGHT LEVEL")
                        Text(
                            "No flight-level selection for ${selectedConfig.label}.",
                            color = YcMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    KickerV("OPACITY")
                    Text(
                        "${(selectedOpacity * 100).toInt()}%",
                        color = YcText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Slider(
                        value = selectedOpacity,
                        onValueChange = { value ->
                            wafsOpacities = wafsOpacities +
                                (wafsSelectedProduct to value.coerceIn(0.1f, 0.9f))
                        },
                        valueRange = 0.1f..0.9f
                    )

                    wafsErrors[wafsSelectedProduct]?.let {
                        ErrorBoxV("${selectedConfig.label} · $it")
                    }
                    Spacer(Modifier.padding(bottom = 24.dp))
                }
            }
        }
    }
}

@Composable
private fun FinalMapControlChip(
    label: String,
    active: Boolean,
    error: String?,
    loading: Boolean,
    hasOptions: Boolean = false,
    onClick: () -> Unit
) {
    val borderColor = when {
        error != null -> YcRed
        active -> YcCyan
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
        border = BorderStroke(1.dp, borderColor),
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
            when {
                error != null -> {
                    Spacer(Modifier.width(5.dp))
                    Text("!", color = YcRed, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
                loading -> {
                    Spacer(Modifier.width(5.dp))
                    Text("…", color = YcCyan, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun FinalZoomToggle(
    label: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = YcText, fontSize = 13.sp)
            Text(
                detail,
                color = YcMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp
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
}

@Composable
private fun FinalWafsProductChip(
    label: String,
    active: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (active) YcCyan.copy(alpha = 0.16f) else YcSurfaceHigh,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected || active) YcCyan else YcHairline),
        shape = RoundedCornerShape(5.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (active) "● " else "○ ",
                color = if (active) YcCyan else YcMuted,
                fontSize = 8.sp
            )
            Text(
                label,
                color = if (active) YcCyanSoft else YcMuted,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun FinalWafsStepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = YcSurfaceHigh,
        border = BorderStroke(1.dp, YcHairline),
        shape = RoundedCornerShape(5.dp)
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = YcCyan,
            modifier = Modifier.padding(9.dp).size(17.dp)
        )
    }
}

@Composable
private fun FinalMapNotice(text: String, error: Boolean = false) {
    Surface(
        color = YcSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, if (error) YcRed.copy(alpha = 0.8f) else YcHairline),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text,
            color = if (error) YcRed else YcMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun FinalTimeButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = YcSurfaceHigh,
        border = BorderStroke(1.dp, YcHairline),
        shape = RoundedCornerShape(5.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = YcCyan, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = YcText, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        }
    }
}

private fun finalTimelineLabel(value: Float): String =
    if (value == 0f) "NOW" else (if (value > 0) "+" else "") + value.toInt() + "h"
