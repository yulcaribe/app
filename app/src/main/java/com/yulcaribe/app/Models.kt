package com.yulcaribe.app

data class Airport(
    val id: Int,
    val icao: String,
    val iata: String?,
    val name: String,
    val city: String?,
    val lat: Double,
    val lon: Double,
    val elevationFt: Int?
)

data class WeatherProduct(
    val available: Boolean,
    val raw: String?,
    val source: String?,
    val transport: String?
)

data class WeatherBundle(
    val icao: String,
    val source: String,
    val fetchedAt: String?,
    val metar: WeatherProduct,
    val taf: WeatherProduct
)

data class Notam(
    val id: String,
    val ident: String,
    val type: String?,
    val classification: String?,
    val fir: String?,
    val location: String?,
    val icaoLocation: String?,
    val selectionCode: String?,
    val traffic: String?,
    val purpose: String?,
    val scope: String?,
    val minimumFl: Int?,
    val maximumFl: Int?,
    val effectiveStart: String?,
    val effectiveEnd: String?,
    val effectiveEndRaw: String?,
    val schedule: String?,
    val lowerLimit: String?,
    val upperLimit: String?,
    val radiusNm: Double?,
    val status: String?,
    val temporalState: String?,
    val text: String?
)

data class NotamPage(
    val total: Int,
    val returned: Int,
    val items: List<Notam>
)

data class BriefStation(
    val icao: String,
    val name: String?,
    val role: String,
    val metarRaw: String?,
    val tafRaw: String?,
    val flightCategory: String?,
    val lat: Double? = null,
    val lon: Double? = null
)

data class BriefHazard(
    val hazard: String,
    val distanceNm: Double?,
    val proximity: String?,
    val timeRelation: String?,
    val cruiseRelation: String?,
    val raw: String?,
    val featureJson: String? = null
)

data class RoutePoint(
    val lat: Double,
    val lon: Double
)

data class Briefing(
    val source: String,
    val routeMode: String,
    val distanceNm: Double,
    val route: List<RoutePoint>,
    val etdUtc: String?,
    val cruiseFl: Int,
    val estimatedEetMinutes: Int,
    val estimatedArrivalUtc: String?,
    val stations: List<BriefStation>,
    val hazards: List<BriefHazard>,
    val routeWarnings: List<String>
)

data class Aircraft(
    val hex: String?,
    val flight: String?,
    val registration: String?,
    val type: String?,
    val lat: Double,
    val lon: Double,
    val track: Double?,
    val altitude: String?,
    val groundSpeed: Double?,
    val squawk: String?,
    val seenPosSeconds: Double? = null
)

data class AdsbSnapshot(
    val sourceNowMs: Long,
    val aircraft: List<Aircraft>
)

data class ModelWindPoint(
    val lat: Double,
    val lon: Double,
    val directionDeg: Double,
    val speedKt: Double,
    val progress: Double,
    val etaUtc: String? = null
)

data class Viewport(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
    val zoom: Int
)

data class WafsFrame(
    val product: String,
    val png: ByteArray,
    val maxLatitude: Double,
    val validUtc: String?,
    val layerFl: String?,
    val forecastHour: String?
)
