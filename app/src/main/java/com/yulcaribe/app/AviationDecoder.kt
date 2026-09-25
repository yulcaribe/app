package com.yulcaribe.app

data class DecodedSection(
    val title: String,
    val rows: List<Pair<String, String>>
)

object AviationDecoder {
    fun decodeMetar(raw: String?): List<DecodedSection> {
        val clean = clean(raw)
        if (clean.isBlank()) return emptyList()
        val tokens = clean.split(" ")
        val rows = mutableListOf<Pair<String, String>>()

        tokens.firstOrNull { it.matches(Regex("^[A-Z]{4}$")) }
            ?.let { rows += "Station" to it }

        tokens.firstOrNull { it.matches(Regex("^\\d{6}Z$")) }?.let {
            rows += "Observation" to
                (it.substring(0, 2) + " " + it.substring(2, 4) + ":" + it.substring(4, 6) + " UTC")
        }

        rows += decodeConditions(tokens)
        return listOf(DecodedSection("METAR", rows))
    }

    fun decodeTaf(raw: String?): List<DecodedSection> {
        val clean = clean(raw)
        if (clean.isBlank()) return emptyList()
        val tokens = clean.split(" ")
        val sections = mutableListOf<DecodedSection>()
        val header = mutableListOf<Pair<String, String>>()

        tokens.firstOrNull { it.matches(Regex("^[A-Z]{4}$")) }
            ?.let { header += "Station" to it }

        tokens.firstOrNull { it.matches(Regex("^\\d{6}Z$")) }?.let {
            header += "Issued" to
                (it.substring(0, 2) + " " + it.substring(2, 4) + ":" + it.substring(4, 6) + " UTC")
        }

        val validityIndex = tokens.indexOfFirst { it.matches(Regex("^\\d{4}/\\d{4}$")) }
        if (validityIndex >= 0) header += "Validity" to decodePeriod(tokens[validityIndex])
        if (header.isNotEmpty()) sections += DecodedSection("TAF", header)

        val start = (validityIndex + 1).coerceAtLeast(0)
        var currentTitle = "Initial conditions"
        var currentTokens = mutableListOf<String>()

        fun push() {
            if (currentTokens.isNotEmpty()) {
                sections += DecodedSection(currentTitle, decodeConditions(currentTokens))
                currentTokens = mutableListOf()
            }
        }

        var i = start
        while (i < tokens.size) {
            val token = tokens[i]
            when {
                token == "BECMG" || token == "TEMPO" -> {
                    push()
                    val range = tokens.getOrNull(i + 1)
                        ?.takeIf { it.matches(Regex("^\\d{4}/\\d{4}$")) }
                    if (range != null) i++
                    currentTitle =
                        (if (token == "BECMG") "BECMG · Becoming" else "TEMPO · Temporary") +
                            (range?.let { " · " + decodePeriod(it) } ?: "")
                }
                token.matches(Regex("^FM\\d{6}$")) -> {
                    push()
                    val t = token.substring(2)
                    currentTitle =
                        "FM · From " + t.substring(0, 2) + " " +
                            t.substring(2, 4) + ":" + t.substring(4, 6) + " UTC"
                }
                token.matches(Regex("^PROB(?:30|40)$")) -> {
                    push()
                    var title = token.removePrefix("PROB") + "% probability"
                    if (tokens.getOrNull(i + 1) == "TEMPO") {
                        title += " · TEMPO"
                        i++
                    }
                    val range = tokens.getOrNull(i + 1)
                        ?.takeIf { it.matches(Regex("^\\d{4}/\\d{4}$")) }
                    if (range != null) {
                        i++
                        title += " · " + decodePeriod(range)
                    }
                    currentTitle = title
                }
                else -> currentTokens += token
            }
            i++
        }
        push()
        return sections
    }

    private fun decodeConditions(tokens: List<String>): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()

        tokens.firstOrNull { it.matches(Regex("^(VRB|\\d{3})\\d{2,3}(G\\d{2,3})?KT$")) }
            ?.let { rows += "Wind" to decodeWind(it) }

        if ("CAVOK" in tokens) {
            rows += "Visibility / cloud" to "CAVOK"
        } else {
            tokens.firstOrNull { it.matches(Regex("^\\d{4}$")) }?.let {
                rows += "Visibility" to if (it == "9999") {
                    "10 km or more"
                } else {
                    (it.toIntOrNull()?.toString() ?: it) + " m"
                }
            }
        }

        val weather = tokens.filter { isWeatherToken(it) }.map { decodeWeather(it) }.distinct()
        if (weather.isNotEmpty()) rows += "Weather" to weather.joinToString(" · ")

        val clouds = tokens.filter { it.matches(Regex("^(FEW|SCT|BKN|OVC)\\d{3}(CB|TCU)?$")) }
            .map { decodeCloud(it) }
        if (clouds.isNotEmpty()) rows += "Cloud" to clouds.joinToString(" · ")

        tokens.firstOrNull { it.matches(Regex("^M?\\d{2}/M?\\d{2}$")) }?.let {
            val parts = it.split("/")
            rows += "Temperature / dew point" to
                (decodeSignedTemperature(parts[0]) + "°C / " + decodeSignedTemperature(parts[1]) + "°C")
        }

        tokens.firstOrNull { it.matches(Regex("^Q\\d{4}$")) }?.let {
            rows += "QNH" to it.drop(1) + " hPa"
        }

        if ("NOSIG" in tokens) rows += "Trend" to "NOSIG · no significant change"
        return rows
    }

    fun explainMetar(raw: String?): String {
        val clean = clean(raw)
        if (clean.isBlank()) return "Current METAR is not available."
        val tokens = clean.split(" ")
        val pieces = mutableListOf<String>()

        tokens.firstOrNull { it.matches(Regex("^(VRB|\\d{3})\\d{2,3}(G\\d{2,3})?KT$")) }
            ?.let { pieces += "Wind " + decodeWind(it).lowercase() + "." }

        if ("CAVOK" in tokens) {
            pieces += "CAVOK conditions are reported."
        } else {
            tokens.firstOrNull { it.matches(Regex("^\\d{4}$")) }?.let {
                pieces += if (it == "9999") {
                    "Visibility is 10 km or more."
                } else {
                    "Reported visibility is " + (it.toIntOrNull()?.toString() ?: it) + " metres."
                }
            }
        }

        val wx = tokens.filter { isWeatherToken(it) }.map { decodeWeather(it) }.distinct()
        if (wx.isNotEmpty()) pieces += "Reported weather: " + wx.joinToString(", ").lowercase() + "."

        val cloud = tokens.filter { it.matches(Regex("^(FEW|SCT|BKN|OVC)\\d{3}(CB|TCU)?$")) }
        if (cloud.isNotEmpty()) pieces += "Cloud: " + cloud.joinToString(", ") { decodeCloud(it) } + "."

        if ("NOSIG" in tokens) pieces += "No significant short-term change is reported."
        return pieces.joinToString(" ").ifBlank {
            "Live METAR loaded. Refer to the raw observation and decoded fields."
        }
    }

    fun explainTaf(raw: String?): String {
        val clean = clean(raw)
        if (clean.isBlank()) return "Current TAF is not available."
        val upper = clean.uppercase()
        val pieces = mutableListOf<String>()

        Regex("\\b\\d{4}/\\d{4}\\b").find(upper)?.value?.let {
            pieces += "Forecast validity: " + decodePeriod(it) + "."
        }
        if ("BECMG" in upper) pieces += "A BECMG transition is included."
        if ("TEMPO" in upper) pieces += "Temporary conditions are forecast during one or more periods."
        if ("PROB30" in upper || "PROB40" in upper) pieces += "A probability group is present."
        if (Regex("\\bFM\\d{6}\\b").containsMatchIn(upper)) {
            pieces += "One or more FM change groups redefine prevailing conditions."
        }
        return pieces.joinToString(" ").ifBlank {
            "No major change group was detected. Refer to the raw TAF and decoded periods for the complete forecast."
        }
    }

    fun explainNotam(notam: Notam): String {
        val parts = mutableListOf<String>()
        val place = notam.icaoLocation ?: notam.location
        if (!place.isNullOrBlank()) parts += "Applies to " + place + "."

        val state = notam.temporalState ?: notam.status
        if (!state.isNullOrBlank()) parts += "Status: " + state.uppercase() + "."

        notam.effectiveStart?.let { parts += "Effective from " + it + "." }
        val end = notam.effectiveEndRaw ?: notam.effectiveEnd
        if (!end.isNullOrBlank()) parts += "Valid to " + end + "."

        if (notam.minimumFl != null || notam.maximumFl != null) {
            parts += "Vertical range FL" + (notam.minimumFl ?: 0) +
                " to FL" + (notam.maximumFl?.toString() ?: "UNL") + "."
        } else if (notam.lowerLimit != null || notam.upperLimit != null) {
            parts += "Vertical limits " + (notam.lowerLimit ?: "SFC") +
                " to " + (notam.upperLimit ?: "UNL") + "."
        }

        notam.schedule?.takeIf { it.isNotBlank() }?.let { parts += "Schedule: " + it + "." }
        return parts.joinToString(" ").ifBlank {
            "Operational NOTAM for the selected airport. Read the raw text for the authoritative wording."
        }
    }

    private fun decodeWind(token: String): String {
        val match = Regex("^(VRB|\\d{3})(\\d{2,3})(G(\\d{2,3}))?KT$").matchEntire(token)
            ?: return token
        val direction = match.groupValues[1]
        val speed = match.groupValues[2].toIntOrNull() ?: 0
        val gust = match.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() }?.toIntOrNull()
        val base = if (direction == "VRB") {
            "variable at " + speed + " kt"
        } else {
            direction + "° at " + speed + " kt"
        }
        return gust?.let { base + ", gusting " + it + " kt" } ?: base
    }

    private fun decodeCloud(token: String): String {
        val match = Regex("^(FEW|SCT|BKN|OVC)(\\d{3})(CB|TCU)?$").matchEntire(token)
            ?: return token
        val amount = when (match.groupValues[1]) {
            "FEW" -> "few"
            "SCT" -> "scattered"
            "BKN" -> "broken"
            "OVC" -> "overcast"
            else -> match.groupValues[1]
        }
        val feet = (match.groupValues[2].toIntOrNull() ?: 0) * 100
        val convective = match.groupValues[3].takeIf { it.isNotBlank() }?.let { " " + it } ?: ""
        return amount + " at " + feet + " ft" + convective
    }

    private fun isWeatherToken(token: String): Boolean =
        token.matches(
            Regex("^[-+]?((MI|PR|BC|DR|BL|SH|TS|FZ)?(DZ|RA|SN|SG|IC|PL|GR|GS|UP|BR|FG|FU|VA|DU|SA|HZ|PY|PO|SQ|FC|SS|DS)){1,3}$")
        )

    private fun decodeWeather(token: String): String {
        val upper = token.uppercase()
        val intensity = when {
            upper.startsWith("+") -> "heavy "
            upper.startsWith("-") -> "light "
            else -> ""
        }
        var core = upper.removePrefix("+").removePrefix("-")
        val terms = linkedMapOf(
            "TS" to "thunderstorm",
            "SH" to "showers",
            "FZ" to "freezing",
            "RA" to "rain",
            "DZ" to "drizzle",
            "SN" to "snow",
            "GR" to "hail",
            "GS" to "small hail",
            "BR" to "mist",
            "FG" to "fog",
            "HZ" to "haze",
            "FU" to "smoke",
            "VA" to "volcanic ash",
            "SQ" to "squall",
            "FC" to "funnel cloud",
            "DS" to "duststorm",
            "SS" to "sandstorm"
        )
        val words = mutableListOf<String>()
        for ((code, label) in terms) {
            if (core.contains(code)) {
                words += label
                core = core.replace(code, "")
            }
        }
        return intensity + words.joinToString(" ").ifBlank { token }
    }

    private fun decodeSignedTemperature(value: String): String =
        if (value.startsWith("M")) {
            "-" + (value.drop(1).toIntOrNull() ?: 0)
        } else {
            (value.toIntOrNull() ?: 0).toString()
        }

    private fun decodePeriod(token: String): String {
        if (!token.matches(Regex("^\\d{4}/\\d{4}$"))) return token
        val from = token.substring(0, 4)
        val to = token.substring(5, 9)
        return from.substring(0, 2) + " " + from.substring(2, 4) + ":00 → " +
            to.substring(0, 2) + " " + to.substring(2, 4) + ":00 UTC"
    }

    private fun clean(raw: String?): String =
        raw?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
}
