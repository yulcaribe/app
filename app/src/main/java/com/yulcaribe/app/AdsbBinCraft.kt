package com.yulcaribe.app

import com.github.luben.zstd.ZstdInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ln

object AdsbBinCraft {
    fun decodeZstd(compressed: ByteArray): AdsbSnapshot {
        val raw = ByteArrayOutputStream()
        ZstdInputStream(ByteArrayInputStream(compressed)).use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                raw.write(buffer, 0, n)
            }
        }
        return parse(raw.toByteArray())
    }

    fun parse(bytes: ByteArray): AdsbSnapshot {
        if (bytes.size < 52) error("binCraft header too short")

        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun u32(index: Int): Long = bb.getInt(index * 4).toLong() and 0xffffffffL

        val stride = u32(2).toInt()
        val version = u32(10).toInt()

        if (stride !in 108..256 || bytes.size < stride) {
            error("Unexpected binCraft stride: $stride")
        }

        val sourceNowSeconds = u32(0) / 1000.0 + u32(1) * 4294967.296
        val aircraft = ArrayList<Aircraft>(bytes.size / stride)

        var off = stride
        while (off + stride <= bytes.size) {
            val row = ByteBuffer.wrap(bytes, off, stride).slice().order(ByteOrder.LITTLE_ENDIAN)

            fun s32(byteOffset: Int): Int = row.getInt(byteOffset)
            fun u16(byteOffset: Int): Int = row.getShort(byteOffset).toInt() and 0xffff
            fun s16(byteOffset: Int): Int = row.getShort(byteOffset).toInt()
            fun u8(byteOffset: Int): Int = row.get(byteOffset).toInt() and 0xff
            fun ascii(start: Int, end: Int): String {
                val sb = StringBuilder()
                for (i in start until minOf(end, stride)) {
                    val b = u8(i)
                    if (b == 0) break
                    sb.append(b.toChar())
                }
                return sb.toString().trim()
            }

            val packedHex = s32(0)
            val nonIcao = (packedHex and (1 shl 24)) != 0
            val hexCore = (packedHex and ((1 shl 24) - 1)).toString(16).padStart(6, '0')
            val hex = if (nonIcao) "~$hexCore" else hexCore

            val seenPos = if (version >= 20240218) {
                s32(27 * 4) / 10.0
            } else {
                u16(2 * 2) / 10.0
            }

            var lon: Double? = s32(2 * 4) / 1e6
            var lat: Double? = s32(3 * 4) / 1e6
            var alt: Any? = s16(10 * 2) * 25
            var gs: Double? = s16(17 * 2) / 10.0
            var track: Double? = s16(20 * 2) / 90.0
            var magHeading: Double? = s16(22 * 2) / 90.0
            var trueHeading: Double? = s16(23 * 2) / 90.0

            val validity1 = u8(73)
            val validity2 = u8(74)
            val validity4 = u8(76)

            val flight = if ((validity1 and 8) != 0) ascii(78, 86) else ""
            val typeCode = ascii(88, 92)
            val registration = ascii(92, 104)

            if ((validity1 and 16) == 0) alt = null
            if ((validity1 and 64) == 0) {
                lat = null
                lon = null
            }
            if ((validity1 and 128) == 0) gs = null

            if ((validity2 and 8) == 0) track = null
            if ((validity2 and 64) == 0) magHeading = null
            if ((validity2 and 128) == 0) trueHeading = null

            var squawk: String? = null
            if ((validity4 and 4) != 0) {
                val rawSquawk = u16(16 * 2).toString(16).padStart(4, '0')
                squawk = if (rawSquawk.firstOrNull()?.let { it > '9' } == true) {
                    rawSquawk.first().digitToInt(16).toString() + rawSquawk.drop(1)
                } else rawSquawk
            }

            val airGround = u8(68) and 15
            if (airGround == 1) alt = "ground"

            val heading = track ?: trueHeading ?: magHeading ?: 0.0
            val sourceType = when ((u8(67) and 240) shr 4) {
                0 -> "adsb_icao"
                1 -> "adsb_icao_nt"
                2 -> "adsr_icao"
                3 -> "tisb_icao"
                4 -> "adsc"
                5 -> "mlat"
                6 -> "other"
                7 -> "mode_s"
                8 -> "adsb_other"
                9 -> "adsr_other"
                10 -> "tisb_trackfile"
                11 -> "tisb_other"
                12 -> "mode_ac"
                else -> "unknown"
            }

            val latValue = lat
            val lonValue = lon
            if (
                latValue != null && lonValue != null &&
                latValue.isFinite() && lonValue.isFinite() &&
                kotlin.math.abs(latValue) <= 90.0 &&
                kotlin.math.abs(lonValue) <= 180.0
            ) {
                aircraft += Aircraft(
                    hex = hex,
                    flight = flight.takeIf { it.isNotBlank() },
                    registration = registration.takeIf { it.isNotBlank() },
                    type = typeCode.takeIf { it.isNotBlank() } ?: sourceType,
                    lat = latValue,
                    lon = lonValue,
                    track = heading,
                    altitude = alt?.toString(),
                    groundSpeed = gs,
                    squawk = squawk,
                    seenPosSeconds = seenPos.takeIf { it.isFinite() }
                )
            }

            off += stride
        }

        return AdsbSnapshot(
            sourceNowMs = (sourceNowSeconds * 1000.0).toLong(),
            aircraft = aircraft
        )
    }
}
