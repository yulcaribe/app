package com.yulcaribe.app

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlin.math.atan
import kotlin.math.sinh

object YcApi {
    const val BASE = "https://yulcaribe.com/main/api/v1"

    private val localAirportFallbacks = listOf(
        Airport(0, "LTAI", "AYT", "Antalya Airport", "Antalya", 36.8987, 30.8005, 177),
        Airport(0, "LTFM", "IST", "Istanbul Airport", "Istanbul", 41.2753, 28.7519, 325),
        Airport(0, "LTFJ", "SAW", "Istanbul Sabiha Gokcen Airport", "Istanbul", 40.8986, 29.3092, 312),
        Airport(0, "LTAC", "ESB", "Ankara Esenboga Airport", "Ankara", 40.1281, 32.9951, 3125),
        Airport(0, "LTBJ", "ADB", "Izmir Adnan Menderes Airport", "Izmir", 38.2924, 27.1569, 412)
    )

    private val phonetic = mapOf(
        "ALFA" to "A", "ALPHA" to "A", "BRAVO" to "B", "CHARLIE" to "C",
        "DELTA" to "D", "ECHO" to "E", "FOXTROT" to "F", "GOLF" to "G",
        "HOTEL" to "H", "INDIA" to "I", "JULIETT" to "J", "JULIET" to "J",
        "KILO" to "K", "LIMA" to "L", "MIKE" to "M", "NOVEMBER" to "N",
        "OSCAR" to "O", "PAPA" to "P", "QUEBEC" to "Q", "ROMEO" to "R",
        "SIERRA" to "S", "TANGO" to "T", "UNIFORM" to "U", "VICTOR" to "V",
        "WHISKEY" to "W", "XRAY" to "X", "X-RAY" to "X", "YANKEE" to "Y",
        "ZULU" to "Z"
    )

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private data class HttpBytes(
        val code: Int,
        val body: ByteArray,
        val headers: Map<String, List<String>>
    )

    private fun request(url: String, accept: String = "application/json"): HttpBytes {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 12_000
        connection.readTimeout = 28_000
        connection.useCaches = false
        connection.setRequestProperty("Accept", accept)
        connection.setRequestProperty("User-Agent", "YulCaribe-Android/0.2")

        val code = connection.responseCode
        val input = if (code in 200..299) connection.inputStream else connection.errorStream
        val out = ByteArrayOutputStream()
        if (input != null) {
            BufferedInputStream(input).use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                }
            }
        }
        val result = HttpBytes(code, out.toByteArray(), connection.headerFields.filterKeys { it != null })
        connection.disconnect()
        return result
    }

    private fun httpJson(url: String): JSONObject {
        val response = request(url)
        val text = response.body.toString(Charsets.UTF_8)
        val json = if (text.isBlank()) JSONObject() else JSONObject(text)
        if (response.code !in 200..299 || !json.optBoolean("ok", response.code in 200..299)) {
            val endpoint = runCatching { URL(url).path.substringAfterLast("/") }.getOrDefault("api")
            val detail = json.optString("error").ifBlank { "Request failed" }
            error(endpoint + " · HTTP " + response.code + " · " + detail)
        }
        return json
    }

    suspend fun catalogOk(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val json = httpJson(BASE + "/index.php")
            json.optString("version") == "v1"
        }.getOrDefault(false)
    }

    fun normalizeAirportQuery(input: String): String {
        val raw = input.trim()
        if (raw.isBlank()) return ""
        val words = raw.uppercase().split(Regex("[\\s-]+")).filter { it.isNotBlank() }
        if (words.size in 3..8 && words.all { phonetic.containsKey(it) }) {
            return words.joinToString("") { phonetic[it].orEmpty() }
        }
        return raw
    }

    suspend fun airportSearch(query: String, limit: Int = 12): List<Airport> =
        withContext(Dispatchers.IO) {
            val normalized = normalizeAirportQuery(query).trim()
            if (normalized.length < 2) return@withContext emptyList()

            val needle = normalized.uppercase()
            val localMatches = localAirportFallbacks.filter { airport ->
                airport.icao == needle ||
                    airport.iata?.uppercase() == needle ||
                    airport.name.uppercase().contains(needle) ||
                    airport.city?.uppercase()?.contains(needle) == true
            }

            val remoteMatches = runCatching {
                val json = httpJson(
                    BASE + "/navdata.php?action=airport-search&q=" + enc(normalized) +
                        "&limit=" + limit.coerceIn(1, 50)
                )
                parseAirports(json.optJSONArray("items"))
            }.getOrDefault(emptyList())

            (localMatches + remoteMatches)
                .distinctBy { it.icao.uppercase() }
                .sortedWith(
                    compareByDescending<Airport> { it.icao.equals(needle, true) }
                        .thenByDescending { it.iata?.equals(needle, true) == true }
                        .thenByDescending { it.city?.equals(normalized, true) == true }
                        .thenBy { it.icao }
                )
                .take(limit.coerceIn(1, 50))
        }

    suspend fun airportDetail(ident: String): Airport = withContext(Dispatchers.IO) {
        val normalized = normalizeAirportQuery(ident).trim().uppercase()
        localAirportFallbacks.firstOrNull {
            it.icao == normalized || it.iata?.uppercase() == normalized
        }?.let { return@withContext it }

        val json = httpJson(
            BASE + "/navdata.php?action=airport-detail&ident=" + enc(normalized)
        )
        parseAirport(json.getJSONObject("airport"))
    }

    private fun parseAirports(array: JSONArray?): List<Airport> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                add(parseAirport(row))
            }
        }
    }

    private fun parseAirport(row: JSONObject): Airport =
        Airport(
            id = row.optInt("id", 0),
            icao = row.optString("icao"),
            iata = row.optNullableString("iata"),
            name = row.optString("name").ifBlank { row.optString("icao") },
            city = row.optNullableString("city"),
            lat = row.optDouble("lat"),
            lon = row.optDouble("lon"),
            elevationFt = if (row.isNull("elevationFt")) null else row.optInt("elevationFt")
        )

    suspend fun weather(icao: String): WeatherBundle = withContext(Dispatchers.IO) {
        val json = httpJson(BASE + "/weather.php?icao=" + enc(icao.uppercase()))
        WeatherBundle(
            icao = json.optString("icao", icao.uppercase()),
            source = json.optString("source", "AviationWeather.gov"),
            fetchedAt = json.optNullableString("fetchedAt"),
            metar = parseWeatherProduct(json.optJSONObject("metar")),
            taf = parseWeatherProduct(json.optJSONObject("taf"))
        )
    }

    private fun parseWeatherProduct(row: JSONObject?): WeatherProduct {
        if (row == null) return WeatherProduct(false, null, null, null)
        return WeatherProduct(
            available = row.optBoolean("available", false),
            raw = row.optNullableString("raw"),
            source = row.optNullableString("source"),
            transport = row.optNullableString("transport")
        )
    }

    suspend fun notamsForAirport(
        icao: String,
        limit: Int = 200
    ): NotamPage = withContext(Dispatchers.IO) {
        val url = BASE + "/notam.php?action=list&state=valid&icao=" +
            enc(icao.uppercase()) +
            "&limit=" + limit.coerceIn(1, 200) +
            "&include_text=1&include_geometry=0&sort=updated_desc"
        val json = httpJson(url)
        val paging = json.optJSONObject("paging") ?: JSONObject()
        val array = json.optJSONArray("items") ?: JSONArray()
        NotamPage(
            total = paging.optInt("total", array.length()),
            returned = paging.optInt("returned", array.length()),
            items = buildList {
                for (i in 0 until array.length()) {
                    val row = array.optJSONObject(i) ?: continue
                    add(parseNotam(row))
                }
            }
        )
    }

    private fun parseNotam(row: JSONObject): Notam =
        Notam(
            id = row.optString("id"),
            ident = row.optString("ident").ifBlank { row.optString("id") },
            type = row.optNullableString("type"),
            classification = row.optNullableString("classification"),
            fir = row.optNullableString("fir"),
            location = row.optNullableString("location"),
            icaoLocation = row.optNullableString("icaoLocation"),
            selectionCode = row.optNullableString("selectionCode"),
            traffic = row.optNullableString("traffic"),
            purpose = row.optNullableString("purpose"),
            scope = row.optNullableString("scope"),
            minimumFl = row.optNullableInt("minimumFl"),
            maximumFl = row.optNullableInt("maximumFl"),
            effectiveStart = row.optNullableString("effectiveStart"),
            effectiveEnd = row.optNullableString("effectiveEnd"),
            effectiveEndRaw = row.optNullableString("effectiveEndRaw"),
            schedule = row.optNullableString("schedule"),
            lowerLimit = row.optNullableString("lowerLimit"),
            upperLimit = row.optNullableString("upperLimit"),
            radiusNm = row.optNullableDouble("radiusNm"),
            status = row.optNullableString("status"),
            temporalState = row.optNullableString("temporalState"),
            text = row.optNullableString("text")
        )

    suspend fun chartViewport(
        viewport: Viewport,
        layers: Set<String>
    ): String = withContext(Dispatchers.IO) {
        if (layers.isEmpty()) return@withContext emptyFeatureCollection()
        val url = BASE + "/navdata.php?action=viewport" +
            "&z=" + viewport.zoom.coerceIn(0, 18) +
            "&west=" + viewport.west +
            "&south=" + viewport.south +
            "&east=" + viewport.east +
            "&north=" + viewport.north +
            "&layers=" + enc(layers.joinToString(","))
        val json = httpJson(url)
        json.optJSONObject("data")?.toString() ?: emptyFeatureCollection()
    }

    suspend fun notamViewport(
        viewport: Viewport,
        at: Instant
    ): String = withContext(Dispatchers.IO) {
        val url = BASE + "/notam.php?action=map" +
            "&z=" + viewport.zoom.coerceIn(0, 18) +
            "&west=" + viewport.west +
            "&south=" + viewport.south +
            "&east=" + viewport.east +
            "&north=" + viewport.north +
            "&at=" + enc(at.toString())
        val json = httpJson(url)
        json.optJSONObject("data")?.toString() ?: emptyFeatureCollection()
    }

    suspend fun flights(lat: Double, lon: Double, radiusNm: Int): List<Aircraft> =
        withContext(Dispatchers.IO) {
            val url = BASE + "/flights.php?lat=" + lat +
                "&lon=" + lon +
                "&radius=" + radiusNm.coerceIn(1, 235)
            val json = httpJson(url)
            val array = json.optJSONArray("ac") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val row = array.optJSONObject(i) ?: continue
                    if (!row.has("lat") || !row.has("lon")) continue
                    add(
                        Aircraft(
                            hex = row.optNullableString("hex"),
                            flight = row.optNullableString("flight")?.trim(),
                            registration = row.optNullableString("r"),
                            type = row.optNullableString("t") ?: row.optNullableString("desc"),
                            lat = row.optDouble("lat"),
                            lon = row.optDouble("lon"),
                            track = row.optNullableDouble("track")
                                ?: row.optNullableDouble("true_heading")
                                ?: row.optNullableDouble("mag_heading"),
                            altitude = if (row.has("alt_baro")) row.opt("alt_baro")?.toString() else null,
                            groundSpeed = row.optNullableDouble("gs"),
                            squawk = row.optNullableString("squawk")
                        )
                    )
                }
            }
        }

    fun flightsGeoJson(items: List<Aircraft>): String {
        val features = JSONArray()
        items.forEach { ac ->
            val props = JSONObject()
                .put("layer", "flight")
                .put("hex", ac.hex)
                .put("flight", ac.flight ?: ac.registration ?: ac.hex ?: "AIRCRAFT")
                .put("registration", ac.registration)
                .put("aircraft_type", ac.type)
                .put("track", ac.track ?: 0.0)
                .put("altitude", ac.altitude)
                .put("groundspeed", ac.groundSpeed ?: 0.0)
                .put("squawk", ac.squawk)
            val geometry = JSONObject()
                .put("type", "Point")
                .put("coordinates", JSONArray().put(ac.lon).put(ac.lat))
            features.put(
                JSONObject()
                    .put("type", "Feature")
                    .put("geometry", geometry)
                    .put("properties", props)
            )
        }
        return JSONObject()
            .put("type", "FeatureCollection")
            .put("features", features)
            .toString()
    }

    suspend fun briefing(
        from: String,
        to: String,
        etdUtc: String,
        cruiseFl: Int,
        route: String
    ): Briefing = withContext(Dispatchers.IO) {
        var url = BASE + "/briefing.php?from=" + enc(from.uppercase()) +
            "&to=" + enc(to.uppercase()) +
            "&etd=" + enc(etdUtc) +
            "&fl=" + cruiseFl.coerceIn(50, 600)
        if (route.isNotBlank()) url += "&route=" + enc(route.uppercase())

        val json = httpJson(url)
        val routeArray = json.optJSONArray("route") ?: JSONArray()
        val points = buildList {
            for (i in 0 until routeArray.length()) {
                val p = routeArray.optJSONArray(i) ?: continue
                if (p.length() >= 2) add(RoutePoint(p.optDouble(0), p.optDouble(1)))
            }
        }

        val stationsArray = json.optJSONArray("stations") ?: JSONArray()
        val stations = buildList {
            for (i in 0 until stationsArray.length()) {
                val row = stationsArray.optJSONObject(i) ?: continue
                val metar = row.optJSONObject("metar")
                val taf = row.optJSONObject("taf")
                add(
                    BriefStation(
                        icao = row.optString("icao"),
                        name = row.optNullableString("name"),
                        role = row.optString("role", "enroute"),
                        metarRaw = metar?.optNullableString("raw"),
                        tafRaw = taf?.optNullableString("raw"),
                        flightCategory = metar?.optNullableString("flightCategory")
                    )
                )
            }
        }

        val hazardsArray = json.optJSONArray("hazards") ?: JSONArray()
        val hazards = buildList {
            for (i in 0 until hazardsArray.length()) {
                val row = hazardsArray.optJSONObject(i) ?: continue
                add(
                    BriefHazard(
                        hazard = row.optString("hazard", "SIGMET"),
                        distanceNm = row.optNullableDouble("distanceNm"),
                        proximity = row.optNullableString("proximity"),
                        timeRelation = row.optNullableString("timeRelation"),
                        cruiseRelation = row.optNullableString("cruiseRelation"),
                        raw = row.optNullableString("raw")
                    )
                )
            }
        }

        val warnings = buildList {
            val engine = json.optJSONObject("routeEngine")
            val arr = engine?.optJSONArray("warnings")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val value = arr.optString(i)
                    if (value.isNotBlank()) add(value)
                }
            }
        }

        val flight = json.optJSONObject("flight") ?: JSONObject()
        Briefing(
            source = json.optString("source", "NOAA/NWS Aviation Weather Center"),
            routeMode = json.optString("routeMode", "great_circle"),
            distanceNm = json.optDouble("distanceNm", 0.0),
            route = points,
            etdUtc = flight.optNullableString("etdUtc"),
            cruiseFl = flight.optInt("cruiseFL", cruiseFl),
            estimatedEetMinutes = flight.optInt("estimatedEetMinutes", 0),
            estimatedArrivalUtc = flight.optNullableString("estimatedArrivalUtc"),
            stations = stations,
            hazards = hazards,
            routeWarnings = warnings
        )
    }

    suspend fun wafsFrame(
        product: String,
        fl: Int,
        validUtc: Instant
    ): WafsFrame = withContext(Dispatchers.IO) {
        val url = BASE + "/wafs.php?action=image&product=" + enc(product) +
            "&fl=" + fl +
            "&valid=" + enc(validUtc.toString())
        val response = request(url, "image/png,image/*;q=0.8,*/*;q=0.1")
        if (response.code !in 200..299) {
            val message = runCatching {
                JSONObject(response.body.toString(Charsets.UTF_8)).optString("error")
            }.getOrDefault("")
            error("wafs.php · HTTP " + response.code + " · " + message.ifBlank { "WAFS image request failed" })
        }
        val bitmap = BitmapFactory.decodeByteArray(response.body, 0, response.body.size)
            ?: error("WAFS PNG çözülemedi.")
        val ratio = bitmap.height.toDouble() / bitmap.width.toDouble()
        val yMax = Math.PI * ratio
        val maxLat = Math.toDegrees(atan(sinh(yMax)))
        bitmap.recycle()

        fun header(name: String): String? =
            response.headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()

        WafsFrame(
            product = product,
            png = response.body,
            maxLatitude = maxLat,
            validUtc = header("X-YC-WAFS-Valid-UTC"),
            layerFl = header("X-YC-WAFS-Layer-FL"),
            forecastHour = header("X-YC-WAFS-Forecast-Hour")
        )
    }

    private fun emptyFeatureCollection(): String =
        """{"type":"FeatureCollection","features":[]}"""
}

private fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() && it != "null" }
}

private fun JSONObject.optNullableInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return optInt(key)
}

private fun JSONObject.optNullableDouble(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    val value = optDouble(key, Double.NaN)
    return value.takeIf { !it.isNaN() }
}
