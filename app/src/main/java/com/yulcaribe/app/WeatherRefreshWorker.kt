package com.yulcaribe.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class WeatherRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(
            "yulcaribe_preferences",
            Context.MODE_PRIVATE
        )
        val mainIcao = prefs.getString("main_airport", "LTAI")
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.matches(Regex("^[A-Z0-9]{3,4}$")) }
            ?: "LTAI"

        val store = YcLocalStore.get(applicationContext)
        val targets = (listOf(mainIcao) + store.favoriteIcaos()).distinct()
        var successCount = 0

        for (icao in targets) {
            runCatching {
                val airport = store.loadAirport(icao)?.airport
                    ?: YcApi.airportDetail(icao)
                val weather = YcApi.weather(airport.icao)
                store.saveWeather(airport, weather, mainIcao)
            }.onSuccess {
                successCount++
            }
        }

        return when {
            targets.isEmpty() -> Result.success()
            successCount > 0 -> Result.success()
            else -> Result.retry()
        }
    }
}

fun scheduleWeatherRefresh(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val request = PeriodicWorkRequestBuilder<WeatherRefreshWorker>(30, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
        "yulcaribe-weather-refresh",
        ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
}
