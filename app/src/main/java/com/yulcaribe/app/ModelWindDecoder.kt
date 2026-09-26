package com.yulcaribe.app

import com.ph.grib2tools.grib2file.GribFile
import com.ph.grib2tools.grib2file.RandomAccessGribFile
import com.ph.grib2tools.grib2file.datarepresentation.DataRepresentationTemplate5x
import com.ph.grib2tools.grib2file.griddefinition.GridDefinitionTemplate30
import com.ph.grib2tools.grib2file.productdefinition.ProductDefinitionTemplate40
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object ModelWindDecoder {
    data class Bbox(
        val left: Double,
        val right: Double,
        val bottom: Double,
        val top: Double
    )

    private data class RouteSample(
        val lat: Double,
        val lon: Double,
        val progress: Double,
        val track: Double
    )

    private data class Field(
        val file: RandomAccessGribFile,
        val name: String
    )

    fun routeBbox(route: List<RoutePoint>): Bbox {
        require(route.size >= 2) { "Model için geçerli rota yok." }
        val lats = route.map { it.lat }
        val lons = route.map { normalizeLon(it.lon) }
        var left = lons.minOrNull()!! - 3.0
        var right = lons.maxOrNull()!! + 3.0
        if (right - left > 170.0) {
            left = -180.0
            right = 180.0
        }
        return Bbox(
            left = left.coerceAtLeast(-180.0),
            right = right.coerceAtMost(180.0),
            bottom = (lats.minOrNull()!! - 3.0).coerceAtLeast(-90.0),
            top = (lats.maxOrNull()!! + 3.0).coerceAtMost(90.0)
        )
    }

    fun decode(
        bytes: ByteArray,
        briefing: Briefing
    ): List<ModelWindPoint> {
        val messages = splitMessages(bytes)
        if (messages.isEmpty()) error("GFS GRIB2 içinde kayıt bulunamadı.")

        val fields = mutableMapOf<String, Field>()
        messages.forEach { message ->
            val file = RandomAccessGribFile("gfs025", "YulCaribe modelwx")
            file.importFromStream(ByteArrayInputStream(message), 0)

            val section0 = file.section0
            if ((section0.discipline.toInt() and 0xff) != 0) return@forEach
            val product = file.productDefinitionTemplate as? ProductDefinitionTemplate40
                ?: return@forEach
            val category = product.parameterCategory.toInt() and 0xff
            val number = product.parameterNumber.toInt() and 0xff
            val name = when ("$category:$number") {
                "0:0" -> "TMP"
                "1:1" -> "RH"
                "2:2" -> "UGRD"
                "2:3" -> "VGRD"
                "3:5" -> "HGT"
                else -> null
            } ?: return@forEach

            if (!fields.containsKey(name)) fields[name] = Field(file, name)
        }

        val uField = fields["UGRD"] ?: error("GFS UGRD kaydı eksik.")
        val vField = fields["VGRD"] ?: error("GFS VGRD kaydı eksik.")
        val tempField = fields["TMP"]
        val rhField = fields["RH"]
        val hgtField = fields["HGT"]

        val route = routeSamples(briefing.route)
        val start = briefing.etdUtc?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val end = briefing.estimatedArrivalUtc?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val durationMs = if (start != null && end != null) {
            max(0L, end.toEpochMilli() - start.toEpochMilli())
        } else 0L

        val result = route.mapNotNull { point ->
            val u = sample(uField.file, point.lat, point.lon)
            val v = sample(vField.file, point.lat, point.lon)
            if (!u.isFinite() || !v.isFinite()) return@mapNotNull null

            val speed = hypot(u, v) * 1.943844
            val direction = (Math.toDegrees(atan2(-u, -v)) + 360.0) % 360.0
            val trackRad = Math.toRadians(point.track)
            val tail = (u * sin(trackRad) + v * cos(trackRad)) * 1.943844
            val cross = abs(u * cos(trackRad) - v * sin(trackRad)) * 1.943844

            val tmpK = tempField?.let { sample(it.file, point.lat, point.lon) }
            val rh = rhField?.let { sample(it.file, point.lat, point.lon) }
            val hgt = hgtField?.let { sample(it.file, point.lat, point.lon) }
            val eta = start?.plusMillis((durationMs * point.progress).toLong())?.toString()

            ModelWindPoint(
                lat = point.lat,
                lon = point.lon,
                directionDeg = direction,
                speedKt = speed,
                progress = point.progress,
                etaUtc = eta,
                tailwindKt = tail,
                crosswindKt = cross,
                temperatureC = tmpK?.takeIf { it.isFinite() }?.minus(273.15),
                relativeHumidity = rh?.takeIf { it.isFinite() && it in 0.0..100.0 },
                geopotentialHeightM = hgt?.takeIf { it.isFinite() }
            )
        }

        if (result.isEmpty()) error("GFS rota gridinde kullanılabilir rüzgâr verisi bulunamadı.")
        return result
    }

    private fun sample(file: RandomAccessGribFile, lat: Double, lon: Double): Double {
        val grid = file.gridDefinitionTemplate as? GridDefinitionTemplate30
            ?: return Double.NaN
        val section5 = file.getSection5(0) ?: return Double.NaN
        val template = section5.dataRepresentationTemplate ?: return Double.NaN

        return when (section5.dataRepresentationTemplateNumber.toInt()) {
            0 -> runCatching { file.getValueAtLocation(0, lat, lon).toDouble() }
                .getOrDefault(Double.NaN)
            2, 3 -> {
                val i = GribFile.getLonIndex(grid, lon)
                val j = GribFile.getLatIndex(grid, lat)
                if (i !in 0 until grid.numberPointsLon || j !in 0 until grid.numberPointsLat) {
                    Double.NaN
                } else {
                    val index = j * grid.numberPointsLon + i
                    val raw = file.getSection7(0)?.data?.variablePart?.getOrNull(index)
                        ?: return Double.NaN
                    unpack(template, raw)
                }
            }
            else -> Double.NaN
        }
    }

    private fun unpack(template: DataRepresentationTemplate5x, raw: Int): Double {
        return (
            template.referenceValueR.toDouble() +
                raw.toDouble() * 2.0.pow(template.binaryScaleFactorE.toInt())
            ) / 10.0.pow(template.decimalScaleFactorD.toInt())
    }

    private fun splitMessages(bytes: ByteArray): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        var cursor = 0
        while (cursor + 16 <= bytes.size) {
            var start = -1
            var i = cursor
            while (i + 16 <= bytes.size) {
                if (
                    bytes[i] == 'G'.code.toByte() &&
                    bytes[i + 1] == 'R'.code.toByte() &&
                    bytes[i + 2] == 'I'.code.toByte() &&
                    bytes[i + 3] == 'B'.code.toByte() &&
                    (bytes[i + 7].toInt() and 0xff) == 2
                ) {
                    start = i
                    break
                }
                i++
            }
            if (start < 0) break

            val length = ByteBuffer.wrap(bytes, start + 8, 8)
                .order(ByteOrder.BIG_ENDIAN)
                .long
            if (length < 20L || length > Int.MAX_VALUE || start + length > bytes.size) break
            val end = start + length.toInt()
            out += bytes.copyOfRange(start, end)
            cursor = end
        }
        return out
    }

    private fun routeSamples(route: List<RoutePoint>, spacingNm: Double = 25.0): List<RouteSample> {
        require(route.size >= 2) { "Model için geçerli rota yok." }
        val lengths = route.zipWithNext { a, b -> distanceNm(a, b) }
        val total = lengths.sum()
        require(total > 0.0) { "Rota uzunluğu sıfır." }

        val segments = ceil(total / spacingNm).toInt().coerceIn(4, 240)
        val out = ArrayList<RouteSample>(segments + 1)
        var leg = 0
        var passed = 0.0

        for (index in 0..segments) {
            val target = total * index / segments.toDouble()
            while (leg < lengths.lastIndex && passed + lengths[leg] < target) {
                passed += lengths[leg]
                leg++
            }
            val fraction = if (lengths[leg] > 0.0) {
                ((target - passed) / lengths[leg]).coerceIn(0.0, 1.0)
            } else 0.0
            val a = route[leg]
            val b = route[leg + 1]
            val p = interpolate(a, b, fraction)
            val track = bearing(if (fraction < 0.999999) p else a, b)
            out += RouteSample(
                lat = p.lat,
                lon = p.lon,
                progress = index / segments.toDouble(),
                track = track
            )
        }
        return out
    }

    private fun distanceNm(a: RoutePoint, b: RoutePoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(normalizeLon(b.lon - a.lon))
        val h = sin(dLat / 2).pow(2.0) +
            cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2.0)
        return 3440.065 * 2.0 * atan2(sqrt(h), sqrt(max(0.0, 1.0 - h)))
    }

    private fun bearing(a: RoutePoint, b: RoutePoint): Double {
        val x = Math.toRadians(a.lat)
        val y = Math.toRadians(b.lat)
        val dl = Math.toRadians(normalizeLon(b.lon - a.lon))
        return (
            Math.toDegrees(
                atan2(
                    sin(dl) * cos(y),
                    cos(x) * sin(y) - sin(x) * cos(y) * cos(dl)
                )
            ) + 360.0
            ) % 360.0
    }

    private fun interpolate(a: RoutePoint, b: RoutePoint, f: Double): RoutePoint {
        val angle = distanceNm(a, b) / 3440.065
        if (angle < 1e-10) return a
        val sinAngle = sin(angle)
        val aa = sin((1.0 - f) * angle) / sinAngle
        val bb = sin(f * angle) / sinAngle
        val latA = Math.toRadians(a.lat)
        val lonA = Math.toRadians(a.lon)
        val latB = Math.toRadians(b.lat)
        val lonB = Math.toRadians(b.lon)

        val x = aa * cos(latA) * cos(lonA) + bb * cos(latB) * cos(lonB)
        val y = aa * cos(latA) * sin(lonA) + bb * cos(latB) * sin(lonB)
        val z = aa * sin(latA) + bb * sin(latB)

        return RoutePoint(
            lat = Math.toDegrees(atan2(z, hypot(x, y))),
            lon = normalizeLon(Math.toDegrees(atan2(y, x)))
        )
    }

    private fun normalizeLon(value: Double): Double =
        ((value + 540.0) % 360.0) - 180.0
}
