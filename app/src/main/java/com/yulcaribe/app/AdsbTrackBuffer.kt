package com.yulcaribe.app

import kotlin.math.abs

class AdsbTrackBuffer {
    private data class Sample(
        val sourceTimeMs: Long,
        val aircraft: Aircraft
    )

    private val samples = linkedMapOf<String, MutableList<Sample>>()
    private var sourceClockOffsetMs: Double? = null
    private val lastSeenClientMs = mutableMapOf<String, Long>()

    @Synchronized
    fun clear() {
        samples.clear()
        lastSeenClientMs.clear()
        sourceClockOffsetMs = null
    }

    @Synchronized
    fun ingest(snapshot: AdsbSnapshot, clientNowMs: Long = System.currentTimeMillis()) {
        val measured = clientNowMs - snapshot.sourceNowMs
        sourceClockOffsetMs = sourceClockOffsetMs?.let { it * 0.85 + measured * 0.15 } ?: measured.toDouble()

        val live = hashSetOf<String>()
        snapshot.aircraft.forEach { aircraft ->
            val key = aircraft.hex?.takeIf { it.isNotBlank() } ?: return@forEach
            live += key
            lastSeenClientMs[key] = clientNowMs

            val sampleTime = snapshot.sourceNowMs -
                ((aircraft.seenPosSeconds ?: 0.0).coerceAtLeast(0.0) * 1000.0).toLong()
            val list = samples.getOrPut(key) { mutableListOf() }
            val last = list.lastOrNull()

            val duplicateTime = last != null && abs(last.sourceTimeMs - sampleTime) < 50L
            val duplicatePosition = last != null &&
                abs(last.aircraft.lat - aircraft.lat) < 1e-9 &&
                abs(last.aircraft.lon - aircraft.lon) < 1e-9

            if (!duplicateTime && !duplicatePosition) {
                list += Sample(sampleTime, aircraft)
                list.sortBy { it.sourceTimeMs }
            } else if (last != null) {
                list[list.lastIndex] = last.copy(aircraft = aircraft)
            }

            val cutoff = snapshot.sourceNowMs - 20_000L
            while (list.size > 2 && list[1].sourceTimeMs < cutoff) list.removeAt(0)
        }

        val stale = lastSeenClientMs
            .filterValues { clientNowMs - it > 15_000L }
            .keys
            .toList()
        stale.forEach {
            samples.remove(it)
            lastSeenClientMs.remove(it)
        }
    }

    @Synchronized
    fun render(
        clientNowMs: Long = System.currentTimeMillis(),
        delayMs: Long = 4_200L
    ): List<Aircraft> {
        val offset = sourceClockOffsetMs ?: return emptyList()
        val target = clientNowMs - offset.toLong() - delayMs
        return samples.values.mapNotNull { list ->
            if (list.isEmpty()) return@mapNotNull null
            var before: Sample? = null
            var after: Sample? = null
            for (sample in list) {
                if (sample.sourceTimeMs <= target) before = sample
                if (sample.sourceTimeMs >= target) {
                    after = sample
                    break
                }
            }

            val a = before ?: list.first()
            val b = after
            if (b == null || b === a || b.sourceTimeMs <= a.sourceTimeMs) {
                return@mapNotNull a.aircraft
            }

            val t = ((target - a.sourceTimeMs).toDouble() /
                (b.sourceTimeMs - a.sourceTimeMs).toDouble()).coerceIn(0.0, 1.0)
            interpolate(a.aircraft, b.aircraft, t)
        }
    }

    private fun interpolate(a: Aircraft, b: Aircraft, t: Double): Aircraft {
        val headingA = a.track ?: 0.0
        val headingB = shortestHeading(headingA, b.track ?: headingA)
        val heading = ((lerp(headingA, headingB, t) % 360.0) + 360.0) % 360.0

        return b.copy(
            lat = lerp(a.lat, b.lat, t),
            lon = lerpLongitude(a.lon, b.lon, t),
            track = heading,
            groundSpeed = interpolateNullable(a.groundSpeed, b.groundSpeed, t)
        )
    }

    private fun interpolateNullable(a: Double?, b: Double?, t: Double): Double? =
        when {
            a != null && b != null -> lerp(a, b, t)
            b != null -> b
            else -> a
        }

    private fun shortestHeading(a: Double, b: Double): Double {
        val delta = ((b - a + 540.0) % 360.0) - 180.0
        return a + delta
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    private fun lerpLongitude(a: Double, b: Double, t: Double): Double {
        var delta = b - a
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        val value = a + delta * t
        return ((value + 540.0) % 360.0) - 180.0
    }
}
