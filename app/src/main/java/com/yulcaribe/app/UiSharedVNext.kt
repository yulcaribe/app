package com.yulcaribe.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
internal fun BrandBarV(screen: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "YULCARIBE",
            color = YcText,
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
            letterSpacing = 2.1.sp
        )
        if (!screen.isNullOrBlank()) {
            Spacer(Modifier.width(10.dp))
            Text(
                screen.uppercase(),
                color = YcMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                letterSpacing = 0.9.sp
            )
        }
    }
}

@Composable
internal fun KickerV(text: String) {
    Text(
        text,
        color = YcCyan,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        letterSpacing = 1.1.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
internal fun SectionHeaderV(title: String, status: String, statusColor: Color) {
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
internal fun DataRowV(label: String, value: String) {
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
internal fun ErrorBoxV(message: String) {
    Surface(
        color = YcRed.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, YcRed.copy(alpha = 0.35f)),
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
internal fun LayerToggleV(
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
internal fun OptionChipV(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) YcCyan.copy(alpha = 0.14f) else YcSurfaceHigh,
        border = BorderStroke(1.dp, if (selected) YcCyan else YcHairline),
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
internal fun WeatherSectionV(
    title: String,
    product: WeatherProduct,
    decoded: List<DecodedSection>,
    explanation: String,
    prefs: VPreferences,
    cachedAtMs: Long,
    live: Boolean
) {
    val status = when {
        live -> "LIVE"
        cachedAtMs > 0L -> "CACHED · ${ageLabelV(cachedAtMs)}"
        product.available -> "CACHED"
        else -> "NO DATA"
    }
    val statusColor = when {
        live -> YcGreen
        product.available -> YcAmber
        else -> YcMuted
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 30.dp)) {
        SectionHeaderV(title, status, statusColor)

        if (prefs.showRaw) {
            KickerV("RAW")
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
            KickerV("DECODED")
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
                    section.rows.forEach { row -> DataRowV(row.first, row.second) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (prefs.showExplanation) {
            KickerV("EXPLANATION")
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
internal fun NotamListV(page: NotamPage?, prefs: VPreferences, loading: Boolean) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        SectionHeaderV(
            "NOTAM",
            when {
                loading && page == null -> "LOADING"
                page == null -> "LIVE"
                else -> "${page.total} VALID"
            },
            if (page == null) YcMuted else YcGreen
        )

        if (loading && page == null) {
            Text("Loading current NOTAM…", color = YcMuted, fontSize = 12.sp)
            return@Column
        }

        if (page == null) return@Column
        if (page.items.isEmpty()) {
            Text("No valid NOTAM returned for this airport.", color = YcMuted, fontSize = 12.sp)
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
                    KickerV("RAW")
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
                    KickerV("DECODED")
                    notamDecodedRowsV(notam).forEach { DataRowV(it.first, it.second) }
                    Spacer(Modifier.height(12.dp))
                }

                if (prefs.showExplanation) {
                    KickerV("EXPLANATION")
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

internal fun notamDecodedRowsV(n: Notam): List<Pair<String, String>> = buildList {
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

internal fun ageLabelV(cachedAtMs: Long): String {
    val age = (System.currentTimeMillis() - cachedAtMs).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(age)
    return when {
        minutes < 1 -> "NOW"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> "${minutes / (24 * 60)}d"
    }
}

internal fun airportViewportV(airport: Airport, zoom: Int = 7): Viewport = Viewport(
    west = airport.lon - 1.35,
    south = airport.lat - 1.0,
    east = airport.lon + 1.35,
    north = airport.lat + 1.0,
    zoom = zoom
)

internal fun routeViewportV(points: List<RoutePoint>): Viewport {
    if (points.isEmpty()) return airportViewportV(ltaiFallbackV, 7)
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

internal fun routeGeoJsonV(points: List<RoutePoint>): String {
    if (points.size < 2) return emptyGeoJsonV()
    val coordinates = JSONArray()
    points.forEach { p -> coordinates.put(JSONArray().put(p.lon).put(p.lat)) }
    val geometry = JSONObject().put("type", "LineString").put("coordinates", coordinates)
    val feature = JSONObject()
        .put("type", "Feature")
        .put("geometry", geometry)
        .put("properties", JSONObject().put("layer", "route"))
    return JSONObject()
        .put("type", "FeatureCollection")
        .put("features", JSONArray().put(feature))
        .toString()
}

internal fun nearestSupportedWafsLevelV(product: String, requested: Int): Int {
    val levels = when (product) {
        "edr" -> listOf(140, 180, 240, 270, 300, 340, 390, 450)
        "icing" -> listOf(60, 100, 140, 180, 240, 300)
        "wind" -> listOf(100, 140, 180, 240, 270, 300, 340, 390, 450)
        else -> return requested.coerceIn(50, 600)
    }
    return levels.minByOrNull { kotlin.math.abs(it - requested) } ?: levels.first()
}

internal fun emptyGeoJsonV(): String = """{"type":"FeatureCollection","features":[]}"""

internal fun haversineNmV(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earth = 3440.065
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dp = Math.toRadians(lat2 - lat1)
    val dl = Math.toRadians(lon2 - lon1)
    val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return 2 * earth * asin(min(1.0, sqrt(a)))
}
