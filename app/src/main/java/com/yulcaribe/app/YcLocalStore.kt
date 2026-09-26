package com.yulcaribe.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Small app-private persistence layer for data that must survive process restarts.
 *
 * The cache policy is intentionally airport-centric:
 * - the selected main airport is never evicted,
 * - favorites are never evicted,
 * - up to five additional recently used airports are retained,
 * - NOTAM is deliberately not persisted here.
 */
class YcLocalStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    data class AirportCache(
        val airport: Airport,
        val chartGeoJson: String?,
        val weather: WeatherBundle?,
        val weatherCachedAtMs: Long,
        val favorite: Boolean,
        val lastAccessMs: Long
    )

    data class RoutePreset(
        val id: Long,
        val name: String?,
        val from: String,
        val to: String,
        val fl: Int,
        val route: String,
        val saved: Boolean,
        val lastUsedMs: Long
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE airport_cache (
                icao TEXT PRIMARY KEY NOT NULL,
                airport_id INTEGER NOT NULL DEFAULT 0,
                iata TEXT,
                name TEXT NOT NULL,
                city TEXT,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                elevation_ft INTEGER,
                chart_geo TEXT,
                weather_json TEXT,
                weather_cached_at INTEGER NOT NULL DEFAULT 0,
                favorite INTEGER NOT NULL DEFAULT 0,
                last_access INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE briefing_routes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                from_icao TEXT NOT NULL,
                to_icao TEXT NOT NULL,
                cruise_fl INTEGER NOT NULL,
                route_text TEXT NOT NULL DEFAULT '',
                saved INTEGER NOT NULL DEFAULT 0,
                last_used INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_airport_cache_recent ON airport_cache(last_access DESC)")
        db.execSQL("CREATE INDEX idx_briefing_routes_recent ON briefing_routes(last_used DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    suspend fun loadAirport(icao: String): AirportCache? = withContext(Dispatchers.IO) {
        readableDatabase.query(
            "airport_cache",
            null,
            "icao=?",
            arrayOf(icao.trim().uppercase()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toAirportCache() else null
        }
    }

    suspend fun touchAirport(airport: Airport, mainIcao: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val db = writableDatabase
        db.beginTransaction()
        try {
            upsertAirportBase(db, airport, now)
            trimRecentLocked(db, mainIcao.trim().uppercase())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun saveChart(airport: Airport, geoJson: String, mainIcao: String) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val db = writableDatabase
            db.beginTransaction()
            try {
                upsertAirportBase(db, airport, now)
                ContentValues().apply {
                    put("chart_geo", geoJson)
                    put("last_access", now)
                }.also { values ->
                    db.update("airport_cache", values, "icao=?", arrayOf(airport.icao.uppercase()))
                }
                trimRecentLocked(db, mainIcao.trim().uppercase())
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

    suspend fun saveWeather(airport: Airport, weather: WeatherBundle, mainIcao: String) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val db = writableDatabase
            db.beginTransaction()
            try {
                upsertAirportBase(db, airport, now)
                ContentValues().apply {
                    put("weather_json", weatherToJson(weather).toString())
                    put("weather_cached_at", now)
                    put("last_access", now)
                }.also { values ->
                    db.update("airport_cache", values, "icao=?", arrayOf(airport.icao.uppercase()))
                }
                trimRecentLocked(db, mainIcao.trim().uppercase())
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

    suspend fun setFavorite(airport: Airport, favorite: Boolean, mainIcao: String) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val db = writableDatabase
            db.beginTransaction()
            try {
                upsertAirportBase(db, airport, now)
                ContentValues().apply {
                    put("favorite", if (favorite) 1 else 0)
                    put("last_access", now)
                }.also { values ->
                    db.update("airport_cache", values, "icao=?", arrayOf(airport.icao.uppercase()))
                }
                trimRecentLocked(db, mainIcao.trim().uppercase())
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

    suspend fun favoriteIcaos(): List<String> = withContext(Dispatchers.IO) {
        val out = mutableListOf<String>()
        readableDatabase.query(
            "airport_cache",
            arrayOf("icao"),
            "favorite=1",
            null,
            null,
            null,
            "last_access DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) out += cursor.getString(0)
        }
        out
    }

    suspend fun favoriteAirports(): List<AirportCache> = withContext(Dispatchers.IO) {
        readAirportList("favorite=1", null, "last_access DESC", 50)
    }

    suspend fun recentAirports(mainIcao: String, limit: Int = RECENT_LIMIT): List<AirportCache> =
        withContext(Dispatchers.IO) {
            readAirportList(
                "icao<>? AND favorite=0",
                arrayOf(mainIcao.trim().uppercase()),
                "last_access DESC",
                limit.coerceIn(1, 20)
            )
        }

    suspend fun recordRecentRoute(
        from: String,
        to: String,
        fl: Int,
        route: String
    ) = withContext(Dispatchers.IO) {
        val fromCode = from.trim().uppercase()
        val toCode = to.trim().uppercase()
        val cleanRoute = route.trim().uppercase()
        val now = System.currentTimeMillis()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existingId = db.query(
                "briefing_routes",
                arrayOf("id"),
                "saved=0 AND from_icao=? AND to_icao=? AND cruise_fl=? AND route_text=?",
                arrayOf(fromCode, toCode, fl.toString(), cleanRoute),
                null,
                null,
                "last_used DESC",
                "1"
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

            if (existingId != null) {
                ContentValues().apply { put("last_used", now) }.also {
                    db.update("briefing_routes", it, "id=?", arrayOf(existingId.toString()))
                }
            } else {
                ContentValues().apply {
                    put("name", null as String?)
                    put("from_icao", fromCode)
                    put("to_icao", toCode)
                    put("cruise_fl", fl)
                    put("route_text", cleanRoute)
                    put("saved", 0)
                    put("last_used", now)
                }.also { db.insert("briefing_routes", null, it) }
            }

            db.execSQL(
                """
                DELETE FROM briefing_routes
                WHERE saved=0 AND id NOT IN (
                    SELECT id FROM briefing_routes
                    WHERE saved=0
                    ORDER BY last_used DESC
                    LIMIT 10
                )
                """.trimIndent()
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun saveRoute(
        name: String?,
        from: String,
        to: String,
        fl: Int,
        route: String
    ) = withContext(Dispatchers.IO) {
        val fromCode = from.trim().uppercase()
        val toCode = to.trim().uppercase()
        val cleanRoute = route.trim().uppercase()
        val now = System.currentTimeMillis()
        val db = writableDatabase
        val existing = db.query(
            "briefing_routes",
            arrayOf("id"),
            "saved=1 AND from_icao=? AND to_icao=? AND cruise_fl=? AND route_text=?",
            arrayOf(fromCode, toCode, fl.toString(), cleanRoute),
            null,
            null,
            "last_used DESC",
            "1"
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

        val values = ContentValues().apply {
            put("name", name?.trim()?.takeIf { it.isNotBlank() } ?: "$fromCode-$toCode")
            put("from_icao", fromCode)
            put("to_icao", toCode)
            put("cruise_fl", fl)
            put("route_text", cleanRoute)
            put("saved", 1)
            put("last_used", now)
        }
        if (existing == null) db.insert("briefing_routes", null, values)
        else db.update("briefing_routes", values, "id=?", arrayOf(existing.toString()))
    }

    suspend fun savedRoutes(limit: Int = 20): List<RoutePreset> = withContext(Dispatchers.IO) {
        readRouteList("saved=1", limit)
    }

    suspend fun recentRoutes(limit: Int = 10): List<RoutePreset> = withContext(Dispatchers.IO) {
        readRouteList("saved=0", limit)
    }

    private fun readRouteList(selection: String, limit: Int): List<RoutePreset> {
        val out = mutableListOf<RoutePreset>()
        readableDatabase.query(
            "briefing_routes",
            null,
            selection,
            null,
            null,
            null,
            "last_used DESC",
            limit.coerceIn(1, 50).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out += RoutePreset(
                    id = cursor.long("id"),
                    name = cursor.stringOrNull("name"),
                    from = cursor.string("from_icao"),
                    to = cursor.string("to_icao"),
                    fl = cursor.int("cruise_fl"),
                    route = cursor.string("route_text"),
                    saved = cursor.int("saved") == 1,
                    lastUsedMs = cursor.long("last_used")
                )
            }
        }
        return out
    }

    private fun readAirportList(
        selection: String,
        args: Array<String>?,
        order: String,
        limit: Int
    ): List<AirportCache> {
        val out = mutableListOf<AirportCache>()
        readableDatabase.query(
            "airport_cache",
            null,
            selection,
            args,
            null,
            null,
            order,
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) out += cursor.toAirportCache()
        }
        return out
    }

    private fun upsertAirportBase(db: SQLiteDatabase, airport: Airport, now: Long) {
        val existing = db.query(
            "airport_cache",
            arrayOf("favorite", "chart_geo", "weather_json", "weather_cached_at"),
            "icao=?",
            arrayOf(airport.icao.uppercase()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) null
            else ExistingAirport(
                favorite = cursor.getInt(0),
                chart = cursor.getString(1),
                weather = cursor.getString(2),
                weatherAt = cursor.getLong(3)
            )
        }

        val values = ContentValues().apply {
            put("icao", airport.icao.uppercase())
            put("airport_id", airport.id)
            put("iata", airport.iata)
            put("name", airport.name)
            put("city", airport.city)
            put("lat", airport.lat)
            put("lon", airport.lon)
            if (airport.elevationFt == null) putNull("elevation_ft") else put("elevation_ft", airport.elevationFt)
            put("favorite", existing?.favorite ?: 0)
            put("chart_geo", existing?.chart)
            put("weather_json", existing?.weather)
            put("weather_cached_at", existing?.weatherAt ?: 0L)
            put("last_access", now)
        }
        db.insertWithOnConflict("airport_cache", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun trimRecentLocked(db: SQLiteDatabase, mainIcao: String) {
        val protected = mutableSetOf(mainIcao)
        db.query(
            "airport_cache",
            arrayOf("icao"),
            "favorite=1",
            null,
            null,
            null,
            null
        ).use { cursor -> while (cursor.moveToNext()) protected += cursor.getString(0) }

        val unprotected = mutableListOf<String>()
        db.query(
            "airport_cache",
            arrayOf("icao"),
            "favorite=0 AND icao<>?",
            arrayOf(mainIcao),
            null,
            null,
            "last_access DESC"
        ).use { cursor -> while (cursor.moveToNext()) unprotected += cursor.getString(0) }

        unprotected.drop(RECENT_LIMIT).forEach { icao ->
            if (icao !in protected) db.delete("airport_cache", "icao=?", arrayOf(icao))
        }
    }

    private data class ExistingAirport(
        val favorite: Int,
        val chart: String?,
        val weather: String?,
        val weatherAt: Long
    )

    private fun Cursor.toAirportCache(): AirportCache {
        val weatherJson = stringOrNull("weather_json")
        return AirportCache(
            airport = Airport(
                id = int("airport_id"),
                icao = string("icao"),
                iata = stringOrNull("iata"),
                name = string("name"),
                city = stringOrNull("city"),
                lat = double("lat"),
                lon = double("lon"),
                elevationFt = intOrNull("elevation_ft")
            ),
            chartGeoJson = stringOrNull("chart_geo"),
            weather = weatherJson?.let { runCatching { weatherFromJson(JSONObject(it)) }.getOrNull() },
            weatherCachedAtMs = long("weather_cached_at"),
            favorite = int("favorite") == 1,
            lastAccessMs = long("last_access")
        )
    }

    private fun weatherToJson(bundle: WeatherBundle): JSONObject = JSONObject()
        .put("icao", bundle.icao)
        .put("source", bundle.source)
        .put("fetchedAt", bundle.fetchedAt)
        .put("metar", productToJson(bundle.metar))
        .put("taf", productToJson(bundle.taf))

    private fun productToJson(product: WeatherProduct): JSONObject = JSONObject()
        .put("available", product.available)
        .put("raw", product.raw)
        .put("source", product.source)
        .put("transport", product.transport)

    private fun weatherFromJson(json: JSONObject): WeatherBundle = WeatherBundle(
        icao = json.optString("icao"),
        source = json.optString("source", "AviationWeather.gov"),
        fetchedAt = json.optNullableStringLocal("fetchedAt"),
        metar = productFromJson(json.optJSONObject("metar")),
        taf = productFromJson(json.optJSONObject("taf"))
    )

    private fun productFromJson(json: JSONObject?): WeatherProduct {
        if (json == null) return WeatherProduct(false, null, null, null)
        return WeatherProduct(
            available = json.optBoolean("available", false),
            raw = json.optNullableStringLocal("raw"),
            source = json.optNullableStringLocal("source"),
            transport = json.optNullableStringLocal("transport")
        )
    }

    private fun Cursor.index(name: String): Int = getColumnIndexOrThrow(name)
    private fun Cursor.string(name: String): String = getString(index(name))
    private fun Cursor.stringOrNull(name: String): String? =
        index(name).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.int(name: String): Int = getInt(index(name))
    private fun Cursor.intOrNull(name: String): Int? =
        index(name).let { if (isNull(it)) null else getInt(it) }
    private fun Cursor.long(name: String): Long = getLong(index(name))
    private fun Cursor.double(name: String): Double = getDouble(index(name))

    companion object {
        private const val DB_NAME = "yulcaribe_local.db"
        private const val DB_VERSION = 1
        private const val RECENT_LIMIT = 5

        @Volatile
        private var instance: YcLocalStore? = null

        fun get(context: Context): YcLocalStore =
            instance ?: synchronized(this) {
                instance ?: YcLocalStore(context.applicationContext).also { instance = it }
            }
    }
}

private fun JSONObject.optNullableStringLocal(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() && it != "null" }
}
