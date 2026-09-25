package com.yulcaribe.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class AirportResult(val icao: String, val name: String?, val lat: Double, val lon: Double)
data class WeatherPayload(val icao: String, val source: String, val fetchedAt: String?, val metarRaw: String?, val tafRaw: String?)
data class NotamItem(
    val id: String, val ident: String, val text: String?,
    val effectiveStart: String?, val effectiveEnd: String?, val effectiveEndRaw: String?,
    val lowerLimit: String?, val upperLimit: String?, val selectionCode: String?,
    val classification: String?, val status: String?
)
data class GeoPoint(val lon: Double, val lat: Double)
data class GeoShape(val layer: String, val ident: String?, val geometryType: String, val lines: List<List<GeoPoint>>, val point: GeoPoint?)
data class NavViewport(val shapes: List<GeoShape>, val counts: Map<String, Int>, val truncated: Boolean)
data class AircraftPoint(val hex: String?, val flight: String?, val lat: Double, val lon: Double, val track: Double?)
data class BriefStation(val icao: String, val role: String, val name: String?, val metarRaw: String?, val tafRaw: String?, val flightCategory: String?)
data class BriefHazard(val hazard: String, val distanceNm: Double?, val proximity: String?, val timeRelation: String?, val cruiseRelation: String?, val raw: String?)
data class BriefingPayload(
    val routeMode: String, val distanceNm: Double, val etdUtc: String?, val cruiseFL: Int,
    val estimatedEetMinutes: Int, val route: List<GeoPoint>, val stations: List<BriefStation>,
    val hazards: List<BriefHazard>, val source: String
)

object YulCaribeApi {
    private const val BASE = "https://yulcaribe.com/main/api"

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun getText(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "GET"
        c.connectTimeout = 15000
        c.readTimeout = 25000
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "YulCaribe-Android/0.2")
        c.useCaches = false

        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val body = stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()
        c.disconnect()

        if (code !in 200..299) {
            val message = runCatching {
                JSONObject(body).optString("error").ifBlank { "HTTP " + code }
            }.getOrDefault("HTTP " + code)
            error(message)
        }
        return body
    }

    suspend fun searchAirports(query: String): List<AirportResult> = withContext(Dispatchers.IO) {
        val json = JSONObject(getText(BASE + "/navmap.php?action=search&q=" + enc(query)))
        val arr = json.optJSONArray("results") ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                if (row.optString("kind") != "airport") continue
                if (!row.has("lat") || !row.has("lon")) continue
                add(
                    AirportResult(
                        icao = row.optString("ident"),
                        name = row.optString("name").takeIf { it.isNotBlank() && it != "null" },
                        lat = row.optDouble("lat"),
                        lon = row.optDouble("lon")
                    )
                )
            }
        }
    }

    suspend fun airportExact(icao: String): AirportResult? =
        searchAirports(icao).firstOrNull { it.icao.equals(icao, true) }

    suspend fun weather(icao: String): WeatherPayload = withContext(Dispatchers.IO) {
        val json = JSONObject(getText(BASE + "/weather.php?icao=" + enc(icao)))
        val metar = json.optJSONObject("metar")
        val taf = json.optJSONObject("taf")
        WeatherPayload(
            icao = json.optString("icao", icao),
            source = json.optString("source", "AviationWeather.gov"),
            fetchedAt = json.optString("fetchedAt").takeIf { it.isNotBlank() },
            metarRaw = metar?.optString("raw")?.takeIf { it.isNotBlank() && it != "null" },
            tafRaw = taf?.optString("raw")?.takeIf { it.isNotBlank() && it != "null" }
        )
    }

    suspend fun airportNotams(icao: String): List<NotamItem> = withContext(Dispatchers.IO) {
        val json = JSONObject(getText(BASE + "/airport-notams.php?icao=" + enc(icao)))
        val arr = json.optJSONArray("items") ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                fun value(name: String) = row.optString(name).takeIf { it.isNotBlank() && it != "null" }
                add(
                    NotamItem(
                        id = row.optString("id"),
                        ident = row.optString("ident"),
                        text = value("text"),
                        effectiveStart = value("effectiveStart"),
                        effectiveEnd = value("effectiveEnd"),
                        effectiveEndRaw = value("effectiveEndRaw"),
                        lowerLimit = value("lowerLimit"),
                        upperLimit = value("upperLimit"),
                        selectionCode = value("selectionCode"),
                        classification = value("classification"),
                        status = value("status")
                    )
                )
            }
        }
    }

    suspend fun navViewport(
        centerLat: Double,
        centerLon: Double,
        zoom: Int = 7,
        layers: Set<String>
    ): NavViewport = withContext(Dispatchers.IO) {
        val latSpan = when {
            zoom >= 9 -> 0.35
            zoom >= 8 -> 0.6
            zoom >= 7 -> 1.0
            else -> 1.7
        }
        val lonSpan = latSpan * 1.35
        val west = centerLon - lonSpan
        val east = centerLon + lonSpan
        val south = centerLat - latSpan
        val north = centerLat + latSpan
        val layerText = layers.joinToString(",")

        val url = BASE + "/navmap.php?action=viewport&z=" + zoom +
            "&west=" + west + "&south=" + south + "&east=" + east + "&north=" + north +
            "&layers=" + enc(layerText)

        val json = JSONObject(getText(url))
        val collection = json.optJSONObject("data") ?: JSONObject()
        val features = collection.optJSONArray("features") ?: JSONArray()
        val shapes = buildList {
            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                val props = feature.optJSONObject("properties") ?: JSONObject()
                val geometry = feature.optJSONObject("geometry") ?: continue
                parseGeometry(
                    layer = props.optString("layer", "unknown"),
                    ident = props.optString("ident").takeIf { it.isNotBlank() && it != "null" },
                    geometry = geometry
                )?.let(::add)
            }
        }

        val countObj = json.optJSONObject("counts") ?: JSONObject()
        val counts = buildMap {
            val keys = countObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, countObj.optInt(key, 0))
            }
        }
        NavViewport(shapes, counts, json.optBoolean("truncated", false))
    }

    private fun parseGeometry(layer: String, ident: String?, geometry: JSONObject): GeoShape? {
        val type = geometry.optString("type")
        val coords = geometry.optJSONArray("coordinates") ?: return null
        return when (type) {
            "Point" -> if (coords.length() < 2) null else
                GeoShape(layer, ident, type, emptyList(), GeoPoint(coords.optDouble(0), coords.optDouble(1)))
            "LineString" -> GeoShape(layer, ident, type, listOf(parseLine(coords)), null)
            "MultiLineString", "Polygon" -> {
                val lines = buildList {
                    for (i in 0 until coords.length()) coords.optJSONArray(i)?.let { add(parseLine(it)) }
                }
                GeoShape(layer, ident, type, lines, null)
            }
            "MultiPolygon" -> {
                val lines = buildList {
                    for (i in 0 until coords.length()) {
                        val polygon = coords.optJSONArray(i) ?: continue
                        for (j in 0 until polygon.length()) polygon.optJSONArray(j)?.let { add(parseLine(it)) }
                    }
                }
                GeoShape(layer, ident, type, lines, null)
            }
            else -> null
        }
    }

    private fun parseLine(array: JSONArray): List<GeoPoint> = buildList {
        for (i in 0 until array.length()) {
            val p = array.optJSONArray(i) ?: continue
            if (p.length() >= 2) add(GeoPoint(p.optDouble(0), p.optDouble(1)))
        }
    }

    suspend fun flights(lat: Double, lon: Double, radiusNm: Int = 90): List<AircraftPoint> =
        withContext(Dispatchers.IO) {
            val json = JSONObject(getText(BASE + "/flights.php?lat=" + lat + "&lon=" + lon + "&radius=" + radiusNm))
            val arr = json.optJSONArray("ac") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val ac = arr.optJSONObject(i) ?: continue
                    if (!ac.has("lat") || !ac.has("lon")) continue
                    add(
                        AircraftPoint(
                            hex = ac.optString("hex").takeIf { it.isNotBlank() },
                            flight = ac.optString("flight").trim().takeIf { it.isNotBlank() },
                            lat = ac.optDouble("lat"),
                            lon = ac.optDouble("lon"),
                            track = if (ac.has("track")) ac.optDouble("track") else null
                        )
                    )
                }
            }
        }

    suspend fun briefing(
        from: String,
        to: String,
        etdUtc: String,
        cruiseFl: Int,
        route: String
    ): BriefingPayload = withContext(Dispatchers.IO) {
        var url = BASE + "/briefing.php?from=" + enc(from) + "&to=" + enc(to) +
            "&etd=" + enc(etdUtc) + "&fl=" + cruiseFl
        if (route.isNotBlank()) url += "&route=" + enc(route)

        val json = JSONObject(getText(url))
        val routeArray = json.optJSONArray("route") ?: JSONArray()
        val routePoints = buildList {
            for (i in 0 until routeArray.length()) {
                val p = routeArray.optJSONArray(i) ?: continue
                if (p.length() >= 2) add(GeoPoint(lon = p.optDouble(1), lat = p.optDouble(0)))
            }
        }

        val stationArray = json.optJSONArray("stations") ?: JSONArray()
        val stations = buildList {
            for (i in 0 until stationArray.length()) {
                val row = stationArray.optJSONObject(i) ?: continue
                val metar = row.optJSONObject("metar")
                val taf = row.optJSONObject("taf")
                add(
                    BriefStation(
                        icao = row.optString("icao"),
                        role = row.optString("role"),
                        name = row.optString("name").takeIf { it.isNotBlank() },
                        metarRaw = metar?.optString("raw")?.takeIf { it.isNotBlank() && it != "null" },
                        tafRaw = taf?.optString("raw")?.takeIf { it.isNotBlank() && it != "null" },
                        flightCategory = metar?.optString("flightCategory")?.takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }

        val hazardArray = json.optJSONArray("hazards") ?: JSONArray()
        val hazards = buildList {
            for (i in 0 until hazardArray.length()) {
                val row = hazardArray.optJSONObject(i) ?: continue
                add(
                    BriefHazard(
                        hazard = row.optString("hazard", "SIGMET"),
                        distanceNm = if (row.has("distanceNm")) row.optDouble("distanceNm") else null,
                        proximity = row.optString("proximity").takeIf { it.isNotBlank() },
                        timeRelation = row.optString("timeRelation").takeIf { it.isNotBlank() },
                        cruiseRelation = row.optString("cruiseRelation").takeIf { it.isNotBlank() },
                        raw = row.optString("raw").takeIf { it.isNotBlank() }
                    )
                )
            }
        }

        val flight = json.optJSONObject("flight") ?: JSONObject()
        BriefingPayload(
            routeMode = json.optString("routeMode", "great_circle"),
            distanceNm = json.optDouble("distanceNm", 0.0),
            etdUtc = flight.optString("etdUtc").takeIf { it.isNotBlank() },
            cruiseFL = flight.optInt("cruiseFL", cruiseFl),
            estimatedEetMinutes = flight.optInt("estimatedEetMinutes", 0),
            route = routePoints,
            stations = stations,
            hazards = hazards,
            source = json.optString("source", "NOAA/NWS Aviation Weather Center")
        )
    }

    fun defaultEtdUtc(): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
}
