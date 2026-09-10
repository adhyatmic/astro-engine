package com.adhyatmic.vedicengine

import io.github.vedicmitra.core.astronomy.AstronomyEngine
import io.github.vedicmitra.core.common.result.AppResult
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Panchak begins at Dhanishta pada 2 and ends when Revati gives way to Ashwini. */
private const val DHANISHTA = 23
private const val REVATI = 27
private const val MAX_HOPS = 48

private data class NakState(
    val instant: Instant,
    val number: Int,
    val name: String,
    val pada: Int,
    val nakStart: Instant,
    val nakEnd: Instant,
    val padaStart: Instant,
    val padaEnd: Instant,
)

internal data class PanchakType(
    val id: String,
    val name: String,
    val nakshatraNumber: Int,
    val nakshatraName: String,
)

internal data class PanchakWindow(
    val start: Instant,
    val end: Instant,
    val types: List<PanchakType>,
)

internal data class PanchakResult(
    val instant: Instant,
    val inPanchak: Boolean,
    val type: PanchakType?,
    val nakshatraNumber: Int,
    val nakshatraName: String,
    val pada: Int,
    val window: PanchakWindow?,
    val upcoming: List<PanchakWindow>,
)

private val TYPE_BY_NAK =
    mapOf(
        23 to PanchakType("rog", "Rog Panchak", 23, "Dhanishta"),
        24 to PanchakType("mrityu", "Mrityu Panchak", 24, "Shatabhisha"),
        25 to PanchakType("agni", "Agni Panchak", 25, "Purva Bhadrapada"),
        26 to PanchakType("chor", "Chor Panchak", 26, "Uttara Bhadrapada"),
        27 to PanchakType("raja", "Raja Panchak", 27, "Revati"),
    )

private fun inPanchak(number: Int, pada: Int): Boolean =
    number > DHANISHTA || (number == DHANISHTA && pada >= 2)

private fun <T> AppResult<T>.orThrow(what: String): T =
    when (this) {
        is AppResult.Success -> data
        is AppResult.Failure -> throw IllegalArgumentException("$what failed: ${cause.message}")
    }

private suspend fun nakStateAt(engine: AstronomyEngine, instant: Instant): NakState {
    val p = engine.panchangaNowAt(instant).orThrow("panchangaNowAt")
    val pada = ((p.limbs.nakshatra.angularFraction * 4.0).toInt()).coerceIn(0, 3) + 1
    return NakState(
        instant = p.instant,
        number = p.nakshatra.number,
        name = p.nakshatra.name,
        pada = pada,
        nakStart = p.limbs.nakshatra.start,
        nakEnd = p.limbs.nakshatra.end,
        padaStart = p.limbs.moonPada.start,
        padaEnd = p.limbs.moonPada.end,
    )
}

private suspend fun findPanchakStart(engine: AstronomyEngine, from: Instant): Instant {
    var state = nakStateAt(engine, from)
    var hops = 0
    while (inPanchak(state.number, state.pada) && hops < MAX_HOPS) {
        val prev = nakStateAt(engine, state.padaStart - 2.seconds)
        if (!inPanchak(prev.number, prev.pada)) {
            return state.padaStart
        }
        state = prev
        hops++
    }
    return state.padaStart
}

private suspend fun findPanchakEnd(engine: AstronomyEngine, from: Instant): Instant {
    var state = nakStateAt(engine, from)
    var hops = 0
    while (state.number != REVATI && hops < MAX_HOPS) {
        state = nakStateAt(engine, state.nakEnd + 2.seconds)
        hops++
    }
    return state.nakEnd
}

private suspend fun typesInWindow(
    engine: AstronomyEngine,
    start: Instant,
    end: Instant,
): List<PanchakType> {
    val seen = linkedMapOf<Int, PanchakType>()
    var t = start + 2.seconds
    var hops = 0
    while (t < end && hops < MAX_HOPS) {
        val s = nakStateAt(engine, t)
        TYPE_BY_NAK[s.number]?.let { seen.putIfAbsent(s.number, it) }
        if (s.number == REVATI) break
        t = s.nakEnd + 2.seconds
        hops++
    }
    return seen.values.toList()
}

private suspend fun windowAt(engine: AstronomyEngine, inside: Instant): PanchakWindow {
    val start = findPanchakStart(engine, inside)
    val end = findPanchakEnd(engine, inside)
    return PanchakWindow(start, end, typesInWindow(engine, start, end))
}

private suspend fun nextPanchakStart(engine: AstronomyEngine, from: Instant, horizon: Instant): Instant? {
    var state = nakStateAt(engine, from)
    var hops = 0
    while (state.instant < horizon && hops < MAX_HOPS) {
        if (inPanchak(state.number, state.pada)) {
            return if (state.number == DHANISHTA && state.pada >= 2) state.padaStart else findPanchakStart(engine, state.instant)
        }
        val jump = if (state.number == DHANISHTA && state.pada < 2) state.padaEnd else state.nakEnd
        state = nakStateAt(engine, jump + 2.seconds)
        hops++
    }
    return null
}

internal suspend fun computePanchak(
    engine: AstronomyEngine,
    at: Instant,
    withinDays: Int,
    limit: Int,
): PanchakResult {
    val now = nakStateAt(engine, at)
    val currentlyIn = inPanchak(now.number, now.pada)
    val type = if (currentlyIn) TYPE_BY_NAK[now.number] else null
    val horizon = at + withinDays.coerceIn(1, 90).days
    val cap = limit.coerceIn(1, 6)

    val upcoming = mutableListOf<PanchakWindow>()
    val currentWindow = if (currentlyIn) windowAt(engine, now.instant) else null

    var cursor = if (currentWindow != null) currentWindow.end + 2.seconds else at
    while (upcoming.size < cap && cursor < horizon) {
        val start = nextPanchakStart(engine, cursor, horizon) ?: break
        if (start >= horizon) break
        val window = windowAt(engine, start + 2.seconds)
        upcoming += window
        cursor = window.end + 2.seconds
    }

    return PanchakResult(
        instant = now.instant,
        inPanchak = currentlyIn,
        type = type,
        nakshatraNumber = now.number,
        nakshatraName = now.name,
        pada = now.pada,
        window = currentWindow,
        upcoming = upcoming,
    )
}
