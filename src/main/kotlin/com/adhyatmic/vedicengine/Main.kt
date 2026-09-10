/*
 * Adhyatmic Vedic engine sidecar — commercial engine-only use of Vedic Mitra
 * :core:astronomy. Does not include :feature:* UI. See LICENSING.md.
 */

package com.adhyatmic.vedicengine

import io.github.vedicmitra.core.astronomy.*
import io.github.vedicmitra.core.common.coroutines.DispatcherProvider
import io.github.vedicmitra.core.common.model.GeoCoordinates
import io.github.vedicmitra.core.common.result.AppResult
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.Instant as JavaInstant
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

private object EngineDispatchers : DispatcherProvider {
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val main: CoroutineDispatcher = Dispatchers.Default
}

private val engine: AstronomyEngine = DefaultAstronomyEngine(EngineDispatchers)

// ---------------------------------------------------------------------------
// Shared request shapes
// ---------------------------------------------------------------------------

@Serializable
data class BirthInput(
    val at: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** Location + time, the common shape most endpoints take. Defaults to New Delhi, now, IST. */
@Serializable
data class GeoTimeRequest(
    val latitude: Double = 28.6139,
    val longitude: Double = 77.2090,
    val zone: String = "Asia/Kolkata",
    /** ISO date or date-time; default now in [zone]. */
    val at: String? = null,
)

@Serializable
data class FestivalsRequest(
    val latitude: Double = 28.6139,
    val longitude: Double = 77.2090,
    val zone: String = "Asia/Kolkata",
    val at: String? = null,
    val withinDays: Int = 30,
    val limit: Int = 10,
)

@Serializable
data class NextTithiRequest(
    val latitude: Double = 28.6139,
    val longitude: Double = 77.2090,
    val zone: String = "Asia/Kolkata",
    val at: String? = null,
    /** Amanta month name to require (e.g. "Kartika"); null = any month. */
    val maasa: String? = null,
    /** Global tithi numbers 1..30 to match. */
    val tithis: List<Int> = emptyList(),
    val withinDays: Int = 60,
)

@Serializable
data class NatalChartRequest(
    val latitude: Double = 28.6139,
    val longitude: Double = 77.2090,
    val zone: String = "Asia/Kolkata",
    val at: String? = null,
    /** Dasha nesting depth to return: 1 = mahadasha, 2 = +antardasha, 3 = +pratyantardasha. */
    val dashaDepth: Int = 2,
)

@Serializable
data class MuhurtaRequest(
    val latitude: Double = 28.6139,
    val longitude: Double = 77.2090,
    val zone: String = "Asia/Kolkata",
    val at: String? = null,
    /** MuhurtaActivity enum name or displayName, e.g. "VIVAH" or "Vivah". */
    val activity: String,
    val days: Int = 30,
    /** Optional birth for a personalised (Tarabala + Chandrabala) ranking. */
    val birth: BirthInput? = null,
)

@Serializable
data class PanchakRequest(
    val latitude: Double = 28.6139,
    val longitude: Double = 77.2090,
    val zone: String = "Asia/Kolkata",
    val at: String? = null,
    /** How far ahead to search for upcoming panchak windows. */
    val withinDays: Int = 40,
    /** Max upcoming windows to return (current window is separate). */
    val limit: Int = 3,
)

@Serializable
data class RashifalRequest(
    val rashi: String? = null,
    val rasiIndex: Int? = null,
    val latitude: Double = 28.6139,
    val longitude: Double = 77.2090,
    val zone: String = "Asia/Kolkata",
    val at: String? = null,
    val days: Int = 7,
    val birth: BirthInput? = null,
)

// ---------------------------------------------------------------------------
// Response DTOs (Instants are rendered as ISO-8601 with the request's zone offset)
// ---------------------------------------------------------------------------

@Serializable data class ErrorBody(val error: String)

@Serializable data class HealthBody(val ok: Boolean = true)

@Serializable data class RasiDto(val index: Int, val name: String)

@Serializable data class TithiDto(val number: Int, val paksha: String, val name: String)

@Serializable data class NakshatraDto(val number: Int, val name: String)

@Serializable data class YogaDto(val number: Int, val name: String)

@Serializable data class KaranaDto(val number: Int, val name: String)

@Serializable
data class MaasaDto(val number: Int, val name: String, val adhika: Boolean, val displayName: String)

@Serializable
data class SamvatsaraDto(
    val number: Int,
    val name: String,
    val shakaYear: Int,
    val vikrama: Int,
    val shaka: Int,
    val kali: Int,
)

@Serializable
data class GoldenHourDto(
    val morningStart: String?,
    val morningEnd: String?,
    val eveningStart: String?,
    val eveningEnd: String?,
)

@Serializable
data class MuhurtaDto(
    val kind: String,
    val name: String,
    val start: String,
    val end: String,
    val quality: String,
)

@Serializable
data class ChoghadiyaDto(
    val name: String,
    val label: String,
    val start: String,
    val end: String,
    val isDay: Boolean,
    val quality: String,
)

@Serializable
data class LimbWindowDto(val start: String, val end: String, val angularFraction: Double)

@Serializable
data class LimbStepDto(
    val limb: String,
    val position: Int,
    val previous: String,
    val current: String,
    val next: String,
    val fraction: Double?,
    val window: LimbWindowDto?,
)

@Serializable
data class SnapshotDto(
    val instant: String,
    val latitude: Double,
    val longitude: Double,
    val zone: String,
    val sunrise: String?,
    val sunset: String?,
    val moonrise: String?,
    val moonset: String?,
    val tithi: TithiDto,
    val nakshatra: NakshatraDto,
    val moonRasi: RasiDto?,
    val moonPada: Int?,
    val sunRasi: RasiDto?,
    val yoga: YogaDto,
    val karana: KaranaDto,
    val vara: String,
    val maasa: MaasaDto,
    val samvatsara: SamvatsaraDto,
    val ayana: String,
    val ritu: String,
    val moonPhase: String,
    val goldenHour: GoldenHourDto,
    val muhurtas: List<MuhurtaDto>,
    val choghadiya: List<ChoghadiyaDto>,
    val limbs: List<LimbStepDto>,
)

@Serializable
data class DaySummaryDto(val tithi: TithiDto, val nakshatra: NakshatraDto, val moonPhase: String)

@Serializable
data class PanchangaNowDto(
    val instant: String,
    val tithi: TithiDto,
    val nakshatra: NakshatraDto,
    val yoga: YogaDto,
    val karana: KaranaDto,
    val tithiWindow: LimbWindowDto,
    val nakshatraWindow: LimbWindowDto,
    val yogaWindow: LimbWindowDto,
    val karanaWindow: LimbWindowDto,
)

@Serializable
data class PanchakTypeDto(
    val id: String,
    val name: String,
    val nakshatraNumber: Int,
    val nakshatraName: String,
)

@Serializable
data class PanchakWindowDto(
    val start: String,
    val end: String,
    val types: List<PanchakTypeDto>,
)

@Serializable
data class PanchakDto(
    val instant: String,
    val inPanchak: Boolean,
    val type: String?,
    val typeName: String?,
    val nakshatra: NakshatraDto,
    val pada: Int,
    val window: PanchakWindowDto?,
    val upcoming: List<PanchakWindowDto>,
)

@Serializable data class FestivalDto(val name: String, val atSunrise: String, val type: String)

@Serializable data class FestivalOnResponse(val festival: FestivalDto?)

@Serializable
data class GrahaPositionDto(
    val graha: String,
    val displayName: String,
    val rasi: RasiDto,
    val pravesh: String?,
)

@Serializable data class PlanetaryPositionsDto(val positions: List<GrahaPositionDto>)

@Serializable data class LagnaDto(val siderealLongitude: Double, val rasi: RasiDto)

@Serializable
data class NatalGrahaDto(
    val graha: String,
    val displayName: String,
    val siderealLongitude: Double,
    val rasi: RasiDto,
    val house: Int,
    val houseFromMoon: Int,
    val retrograde: Boolean,
    val combust: Boolean,
    val degrees: Int,
    val minutes: Int,
    val nakshatra: NakshatraDto,
    val pada: Int,
    val navamsha: RasiDto,
    val dignity: String?,
)

@Serializable
data class DashaPeriodDto(
    val lord: String,
    val start: String,
    val end: String,
    val level: Int,
    val subPeriods: List<DashaPeriodDto> = emptyList(),
)

@Serializable
data class JatakaDto(
    val janmaRashi: RasiDto,
    val nakshatra: NakshatraDto,
    val pada: Int,
    val gana: String,
    val varna: String,
    val vashya: String,
    val yoni: String,
    val nadi: String,
    val lagna: RasiDto,
    val rashiLord: String,
    val sunRashi: RasiDto,
    val sunSign: String,
    val ayanamsa: Double,
    val shakaSamvat: Int,
    val vikramSamvat: Int,
    val samvatsara: String,
)

@Serializable data class ChartYogaDto(val name: String, val rule: String, val summary: String)

@Serializable
data class NatalChartDto(
    val instant: String,
    val latitude: Double,
    val longitude: Double,
    val zone: String,
    val lagna: LagnaDto,
    val houses: List<RasiDto>,
    val moonHouses: List<RasiDto>,
    val grahas: List<NatalGrahaDto>,
    val moonNakshatra: NakshatraDto,
    val moonPada: Int,
    val jataka: JatakaDto?,
    val yogas: List<ChartYogaDto>,
    val vimshottari: List<DashaPeriodDto>,
    val sarvashtakavarga: List<Int>,
)

@Serializable data class SunriseDto(val sunrise: String?)

@Serializable data class MuhurtaReasonDto(val favourable: Boolean, val text: String)

@Serializable
data class DayMuhurtaScoreDto(
    val score: Int,
    val rating: String,
    val stars: Int,
    val reasons: List<MuhurtaReasonDto>,
)

@Serializable
data class RankedMuhurtaDayDto(val atSunrise: String, val score: DayMuhurtaScoreDto)

@Serializable
data class MuhurtaFinderResponse(
    val activity: String,
    val activityDisplayName: String,
    val personalized: Boolean,
    val days: List<RankedMuhurtaDayDto>,
)

@Serializable
data class MuhurtaActivityDto(
    val name: String,
    val displayName: String,
    val category: String,
    val categoryDisplayName: String,
)

// --- rashifal (kept from the original engine) ---

@Serializable
data class TaraDto(val name: String, val strength: String, val number: Int)

@Serializable
data class RashiDayDto(
    val atSunrise: String,
    val band: String,
    val bandLabel: String,
    val chandrabala: String,
    val chandraPosition: Int,
    val moonRasi: String,
    val nakshatra: String,
    val tara: TaraDto?,
)

@Serializable
data class RashifalResponse(
    val source: String = "vedic-mitra-core-astronomy",
    val licensed: String = "engine-only",
    val rashi: String,
    val rasiIndex: Int,
    val personalized: Boolean,
    val latitude: Double,
    val longitude: Double,
    val zone: String,
    val today: RashiDayDto,
    val week: List<RashiDayDto>,
)

@Serializable data class RouteInfo(val method: String, val path: String, val summary: String)

@Serializable data class IndexResponse(val service: String, val routes: List<RouteInfo>)

// ---------------------------------------------------------------------------
// Server
// ---------------------------------------------------------------------------

fun main() {
    val port = System.getenv("VEDIC_ENGINE_PORT")?.toIntOrNull() ?: 8090
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    prettyPrint = false
                    encodeDefaults = true
                },
            )
        }
        install(StatusPages) {
            exception<IllegalArgumentException> { call, cause ->
                call.respond(HttpStatusCode.BadRequest, ErrorBody(cause.message ?: "bad request"))
            }
            exception<Throwable> { call, cause ->
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorBody(cause.message ?: "internal error"),
                )
            }
        }
        routing {
            get("/") { call.respond(indexResponse()) }
            get("/health") { call.respond(HealthBody()) }

            post("/v1/snapshot") {
                call.respond(computeSnapshot(call.receive()))
            }
            post("/v1/day-summary") {
                call.respond(computeDaySummary(call.receive()))
            }
            post("/v1/panchanga-now") {
                call.respond(computePanchangaNow(call.receive()))
            }
            post("/v1/planetary-positions") {
                call.respond(computePlanetaryPositions(call.receive()))
            }
            post("/v1/sunrise") {
                call.respond(computeSunrise(call.receive()))
            }
            post("/v1/festivals") {
                call.respond(computeFestivals(call.receive()))
            }
            post("/v1/festival-on") {
                call.respond(computeFestivalOn(call.receive<GeoTimeRequest>()))
            }
            post("/v1/next-tithi") {
                call.respond(computeNextTithi(call.receive()))
            }
            post("/v1/natal-chart") {
                call.respond(computeNatalChart(call.receive()))
            }
            post("/v1/muhurta") {
                call.respond(computeMuhurta(call.receive()))
            }
            get("/v1/muhurta/activities") {
                call.respond(muhurtaActivities())
            }
            post("/v1/panchak") {
                call.respond(computePanchak(call.receive()))
            }
            post("/v1/rashifal") {
                call.respond(computeRashifal(call.receive()))
            }
        }
    }.start(wait = true)
}

private fun indexResponse() =
    IndexResponse(
        service = "astro-engine (vedic-mitra core:astronomy, engine-only)",
        routes =
            listOf(
                RouteInfo("GET", "/health", "Liveness probe"),
                RouteInfo("POST", "/v1/snapshot", "Full daily panchanga snapshot for an instant + place"),
                RouteInfo("POST", "/v1/day-summary", "Lightweight tithi/nakshatra/moon-phase for a day"),
                RouteInfo("POST", "/v1/panchanga-now", "Panchanga limbs in force at an instant, with windows"),
                RouteInfo("POST", "/v1/planetary-positions", "Graha rashis and next ingress (pravesh)"),
                RouteInfo("POST", "/v1/sunrise", "Sunrise of the civil day"),
                RouteInfo("POST", "/v1/festivals", "Upcoming festivals/observances within N days"),
                RouteInfo("POST", "/v1/festival-on", "Most notable event on a given day"),
                RouteInfo("POST", "/v1/next-tithi", "Next day matching a tithi set (+ optional maasa)"),
                RouteInfo("POST", "/v1/natal-chart", "Birth chart: grahas, houses, jataka, dasha, yogas"),
                RouteInfo("POST", "/v1/muhurta", "Best dates for an activity (optionally personalised)"),
                RouteInfo("GET", "/v1/muhurta/activities", "List the supported muhurta activities"),
                RouteInfo("POST", "/v1/panchak", "Panchak kaal: current flag, type, and upcoming windows"),
                RouteInfo("POST", "/v1/rashifal", "Daily/weekly Moon-transit outlook for a rashi"),
            ),
    )

// ---------------------------------------------------------------------------
// Handlers
// ---------------------------------------------------------------------------

private suspend fun computeSnapshot(req: GeoTimeRequest): SnapshotDto {
    val zoneId = ZoneId.of(req.zone)
    val fmt = isoFormatter(zoneId)
    val instant = parseInstant(req.at, zoneId)
    val location = GeoCoordinates(req.latitude, req.longitude)
    val snap = engine.snapshotAt(instant, location).orThrow("snapshotAt")
    return snapshotDto(snap, req.latitude, req.longitude, req.zone, fmt)
}

private suspend fun computeDaySummary(req: GeoTimeRequest): DaySummaryDto {
    val zoneId = ZoneId.of(req.zone)
    val instant = parseInstant(req.at, zoneId)
    val location = GeoCoordinates(req.latitude, req.longitude)
    val s = engine.daySummaryAt(instant, location).orThrow("daySummaryAt")
    return DaySummaryDto(tithiDto(s.tithi), nakshatraDto(s.nakshatra), s.moonPhase.displayName)
}

private suspend fun computePanchangaNow(req: GeoTimeRequest): PanchangaNowDto {
    val zoneId = ZoneId.of(req.zone)
    val fmt = isoFormatter(zoneId)
    val instant = parseInstant(req.at, zoneId)
    val p = engine.panchangaNowAt(instant).orThrow("panchangaNowAt")
    return PanchangaNowDto(
        instant = fmt.fmt(p.instant),
        tithi = tithiDto(p.tithi),
        nakshatra = nakshatraDto(p.nakshatra),
        yoga = yogaDto(p.yoga),
        karana = karanaDto(p.karana),
        tithiWindow = limbWindowDto(p.limbs.tithi, fmt),
        nakshatraWindow = limbWindowDto(p.limbs.nakshatra, fmt),
        yogaWindow = limbWindowDto(p.limbs.yoga, fmt),
        karanaWindow = limbWindowDto(p.limbs.karana, fmt),
    )
}

private suspend fun computePlanetaryPositions(req: GeoTimeRequest): PlanetaryPositionsDto {
    val zoneId = ZoneId.of(req.zone)
    val fmt = isoFormatter(zoneId)
    val instant = parseInstant(req.at, zoneId)
    val pos = engine.planetaryPositionsAt(instant).orThrow("planetaryPositionsAt")
    return PlanetaryPositionsDto(
        pos.positions.map {
            GrahaPositionDto(
                graha = it.graha.name,
                displayName = it.graha.displayName,
                rasi = rasiDto(it.rasi),
                pravesh = fmt.fmtOrNull(it.pravesh),
            )
        },
    )
}

private suspend fun computeSunrise(req: GeoTimeRequest): SunriseDto {
    val zoneId = ZoneId.of(req.zone)
    val fmt = isoFormatter(zoneId)
    val instant = parseInstant(req.at, zoneId)
    val location = GeoCoordinates(req.latitude, req.longitude)
    val sunrise = engine.sunriseAt(instant, location).orThrow("sunriseAt")
    return SunriseDto(fmt.fmtOrNull(sunrise))
}

private suspend fun computeFestivals(req: FestivalsRequest): List<FestivalDto> {
    val zoneId = ZoneId.of(req.zone)
    val fmt = isoFormatter(zoneId)
    val instant = parseInstant(req.at, zoneId)
    val location = GeoCoordinates(req.latitude, req.longitude)
    val festivals =
        engine
            .upcomingFestivals(instant, location, req.withinDays.coerceIn(1, 400), req.limit.coerceIn(1, 100))
            .orThrow("upcomingFestivals")
    return festivals.map { FestivalDto(it.name, fmt.fmt(it.atSunrise), it.type.name) }
}

private suspend fun computeFestivalOn(req: GeoTimeRequest): FestivalOnResponse {
    val zoneId = ZoneId.of(req.zone)
    val fmt = isoFormatter(zoneId)
    val instant = parseInstant(req.at, zoneId)
    val location = GeoCoordinates(req.latitude, req.longitude)
    val name = engine.festivalOn(instant, location).orThrow("festivalOn")
        ?: return FestivalOnResponse(null)
    // festivalOn reports only the name; anchor it to the day's sunrise for a consistent shape.
    val sunrise = engine.sunriseAt(instant, location).orThrow("sunriseAt") ?: instant
    return FestivalOnResponse(FestivalDto(name, fmt.fmt(sunrise), "UNKNOWN"))
}

private suspend fun computeNextTithi(req: NextTithiRequest): SunriseDto {
    val zoneId = ZoneId.of(req.zone)
    val fmt = isoFormatter(zoneId)
    val instant = parseInstant(req.at, zoneId)
    val location = GeoCoordinates(req.latitude, req.longitude)
    require(req.tithis.isNotEmpty()) { "Provide at least one tithi (1..30) in 'tithis'" }
    val next =
        engine
            .nextTithiOccurrence(
                instant,
                location,
                req.maasa,
                req.tithis.toSet(),
                req.withinDays.coerceIn(1, 400),
            ).orThrow("nextTithiOccurrence")
    return SunriseDto(fmt.fmtOrNull(next))
}

private suspend fun computeNatalChart(req: NatalChartRequest): NatalChartDto {
    val zoneId = ZoneId.of(req.zone)
    val fmt = isoFormatter(zoneId)
    val instant = parseInstant(req.at, zoneId)
    val location = GeoCoordinates(req.latitude, req.longitude)
    val chart =
        engine.natalChartAt(instant, location).orThrow("natalChartAt")
            ?: throw IllegalArgumentException("natalChartAt returned no chart")
    val depth = req.dashaDepth.coerceIn(1, 3)
    return NatalChartDto(
        instant = fmt.fmt(instant),
        latitude = req.latitude,
        longitude = req.longitude,
        zone = req.zone,
        lagna = LagnaDto(chart.lagna.siderealLongitude, rasiDto(chart.lagna.rasi)),
        houses = chart.houses.map(::rasiDto),
        moonHouses = chart.moonHouses.map(::rasiDto),
        grahas = chart.grahas.map(::natalGrahaDto),
        moonNakshatra = nakshatraDto(chart.moonNakshatra),
        moonPada = chart.moonPada,
        jataka = chart.jataka?.let(::jatakaDto),
        yogas = chart.yogas.map { ChartYogaDto(it.name, it.rule, it.summary) },
        vimshottari = chart.vimshottari.map { dashaDto(it, fmt, depth) },
        sarvashtakavarga = chart.sarvashtakavarga,
    )
}

private suspend fun computeMuhurta(req: MuhurtaRequest): MuhurtaFinderResponse {
    val zoneId = ZoneId.of(req.zone)
    val fmt = isoFormatter(zoneId)
    val instant = parseInstant(req.at, zoneId)
    val location = GeoCoordinates(req.latitude, req.longitude)
    val activity =
        MuhurtaActivity.entries.firstOrNull {
            it.name.equals(req.activity, ignoreCase = true) ||
                it.displayName.equals(req.activity, ignoreCase = true)
        } ?: throw IllegalArgumentException(
            "Unknown activity '${req.activity}'. See GET /v1/muhurta/activities.",
        )

    var person: PersonalMuhurtaContext? = null
    val birth = req.birth
    if (birth != null) {
        val birthLoc = GeoCoordinates(birth.latitude ?: req.latitude, birth.longitude ?: req.longitude)
        val chart = engine.natalChartAt(parseInstant(birth.at, zoneId), birthLoc).orThrow("natalChartAt")
        if (chart != null) {
            val moon = chart.grahas.first { it.graha == Graha.MOON }
            person = PersonalMuhurtaContext(chart.moonNakshatra.number, moon.rasi.index)
        }
    }

    val ranked =
        engine
            .bestMuhurtasFor(activity, instant, req.days.coerceIn(1, 120), location, person)
            .orThrow("bestMuhurtasFor")
    return MuhurtaFinderResponse(
        activity = activity.name,
        activityDisplayName = activity.displayName,
        personalized = person != null,
        days =
            ranked.map { day ->
                RankedMuhurtaDayDto(
                    atSunrise = fmt.fmt(day.atSunrise),
                    score =
                        DayMuhurtaScoreDto(
                            score = day.score.score,
                            rating = day.score.rating.name,
                            stars = day.score.rating.stars,
                            reasons = day.score.reasons.map { MuhurtaReasonDto(it.favourable, it.text) },
                        ),
                )
            },
    )
}

private fun muhurtaActivities(): List<MuhurtaActivityDto> =
    MuhurtaActivity.entries.map {
        MuhurtaActivityDto(
            name = it.name,
            displayName = it.displayName,
            category = it.category.name,
            categoryDisplayName = it.category.displayName,
        )
    }

private suspend fun computePanchak(req: PanchakRequest): PanchakDto {
    val zoneId = ZoneId.of(req.zone)
    val fmt = isoFormatter(zoneId)
    val instant = parseInstant(req.at, zoneId)
    val result = computePanchak(engine, instant, req.withinDays, req.limit)

    fun typeDto(t: PanchakType) =
        PanchakTypeDto(t.id, t.name, t.nakshatraNumber, t.nakshatraName)

    fun windowDto(w: PanchakWindow) =
        PanchakWindowDto(fmt.fmt(w.start), fmt.fmt(w.end), w.types.map(::typeDto))

    return PanchakDto(
        instant = fmt.fmt(result.instant),
        inPanchak = result.inPanchak,
        type = result.type?.id,
        typeName = result.type?.name,
        nakshatra = NakshatraDto(result.nakshatraNumber, result.nakshatraName),
        pada = result.pada,
        window = result.window?.let(::windowDto),
        upcoming = result.upcoming.map(::windowDto),
    )
}

private suspend fun computeRashifal(req: RashifalRequest): RashifalResponse {
    val rasiIndex =
        req.rasiIndex?.takeIf { it in 0..11 }
            ?: req.rashi?.let { name ->
                RASHI_NAMES.indexOfFirst { it.equals(name, ignoreCase = true) }.takeIf { it >= 0 }
            }
            ?: throw IllegalArgumentException(
                "Provide rasiIndex 0..11 or rashi one of: ${RASHI_NAMES.joinToString()}",
            )

    val zoneId = ZoneId.of(req.zone)
    val fmt = isoFormatter(zoneId)
    val instant = parseInstant(req.at, zoneId)
    val location = GeoCoordinates(latitude = req.latitude, longitude = req.longitude)

    var person: PersonalMuhurtaContext? = null
    var personalized = false
    val birth = req.birth
    if (birth != null) {
        val birthLoc =
            GeoCoordinates(
                latitude = birth.latitude ?: req.latitude,
                longitude = birth.longitude ?: req.longitude,
            )
        val chart = engine.natalChartAt(parseInstant(birth.at, zoneId), birthLoc).orThrow("natalChartAt")
            ?: throw IllegalArgumentException("natalChartAt returned null")
        val moon = chart.grahas.first { it.graha.name == "MOON" }
        // Match Mitra app: Tarabala only when reading birth Moon sign.
        if (moon.rasi.index == rasiIndex) {
            person =
                PersonalMuhurtaContext(
                    birthNakshatraNumber = chart.moonNakshatra.number,
                    birthMoonRasiIndex = moon.rasi.index,
                )
            personalized = true
        }
    }

    val o = engine.rashiOutlook(rasiIndex, instant, location, person, req.days.coerceIn(1, 14)).orThrow("rashiOutlook")

    fun dayDto(d: RashiDay): RashiDayDto =
        RashiDayDto(
            atSunrise = fmt.fmt(d.atSunrise),
            band = d.band.name.lowercase(),
            bandLabel = d.band.label,
            chandrabala = d.chandrabala.name.lowercase(),
            chandraPosition = d.chandraPosition,
            moonRasi = d.moonRasi.name,
            nakshatra = d.nakshatra.name,
            tara =
                d.tara?.let {
                    TaraDto(name = it.name, strength = it.strength.name.lowercase(), number = it.number)
                },
        )

    return RashifalResponse(
        rashi = RASHI_NAMES[rasiIndex],
        rasiIndex = rasiIndex,
        personalized = personalized,
        latitude = req.latitude,
        longitude = req.longitude,
        zone = req.zone,
        today = dayDto(o.today),
        week = o.week.map(::dayDto),
    )
}

// ---------------------------------------------------------------------------
// Mappers
// ---------------------------------------------------------------------------

private fun rasiDto(r: Rasi) = RasiDto(r.index, r.name)

private fun tithiDto(t: Tithi) = TithiDto(t.number, t.paksha.displayName, t.name)

private fun nakshatraDto(n: Nakshatra) = NakshatraDto(n.number, n.name)

private fun yogaDto(y: Yoga) = YogaDto(y.number, y.name)

private fun karanaDto(k: Karana) = KaranaDto(k.number, k.name)

private fun maasaDto(m: Maasa) = MaasaDto(m.number, m.name, m.adhika, m.displayName)

private fun samvatsaraDto(s: Samvatsara) =
    SamvatsaraDto(s.number, s.name, s.shakaYear, s.eras.vikrama, s.eras.shaka, s.eras.kali)

private fun limbWindowDto(w: LimbWindow, fmt: DateTimeFormatter) =
    LimbWindowDto(fmt.fmt(w.start), fmt.fmt(w.end), w.angularFraction)

private fun snapshotDto(
    snap: AstronomySnapshot,
    latitude: Double,
    longitude: Double,
    zone: String,
    fmt: DateTimeFormatter,
): SnapshotDto =
    SnapshotDto(
        instant = fmt.fmt(snap.instant),
        latitude = latitude,
        longitude = longitude,
        zone = zone,
        sunrise = fmt.fmtOrNull(snap.sunTimes.sunrise),
        sunset = fmt.fmtOrNull(snap.sunTimes.sunset),
        moonrise = fmt.fmtOrNull(snap.moonTimes.moonrise),
        moonset = fmt.fmtOrNull(snap.moonTimes.moonset),
        tithi = tithiDto(snap.tithi),
        nakshatra = nakshatraDto(snap.nakshatra),
        moonRasi = snap.moonRasi?.let(::rasiDto),
        moonPada = snap.moonPada,
        sunRasi = snap.sunRasi?.let(::rasiDto),
        yoga = yogaDto(snap.yoga),
        karana = karanaDto(snap.karana),
        vara = snap.vara.displayName,
        maasa = maasaDto(snap.maasa),
        samvatsara = samvatsaraDto(snap.samvatsara),
        ayana = snap.ayana.displayName,
        ritu = snap.ritu.displayName,
        moonPhase = snap.moonPhase.displayName,
        goldenHour =
            GoldenHourDto(
                fmt.fmtOrNull(snap.goldenHour.morningStart),
                fmt.fmtOrNull(snap.goldenHour.morningEnd),
                fmt.fmtOrNull(snap.goldenHour.eveningStart),
                fmt.fmtOrNull(snap.goldenHour.eveningEnd),
            ),
        muhurtas =
            snap.muhurtas.map {
                MuhurtaDto(it.kind.id, it.name, fmt.fmt(it.start), fmt.fmt(it.end), it.quality.name)
            },
        choghadiya =
            snap.choghadiya.map {
                ChoghadiyaDto(it.name.name, it.name.label, fmt.fmt(it.start), fmt.fmt(it.end), it.isDay, it.quality.name)
            },
        limbs =
            snap.limbSteps().map { step ->
                LimbStepDto(
                    limb = step.limb.displayName,
                    position = step.position,
                    previous = step.previous,
                    current = step.current,
                    next = step.next,
                    fraction = step.fraction,
                    window = step.window?.let { limbWindowDto(it, fmt) },
                )
            },
    )

private fun natalGrahaDto(g: NatalGraha) =
    NatalGrahaDto(
        graha = g.graha.name,
        displayName = g.graha.displayName,
        siderealLongitude = g.siderealLongitude,
        rasi = rasiDto(g.rasi),
        house = g.house,
        houseFromMoon = g.houseFromMoon,
        retrograde = g.retrograde,
        combust = g.combust,
        degrees = g.position.degrees,
        minutes = g.position.minutes,
        nakshatra = nakshatraDto(g.nakshatra),
        pada = g.pada,
        navamsha = rasiDto(g.navamsha),
        dignity = g.dignity?.displayName,
    )

private fun jatakaDto(j: JatakaProfile) =
    JatakaDto(
        janmaRashi = rasiDto(j.janmaRashi),
        nakshatra = nakshatraDto(j.nakshatra),
        pada = j.pada,
        gana = j.gana,
        varna = j.varna,
        vashya = j.vashya,
        yoni = j.yoni,
        nadi = j.nadi,
        lagna = rasiDto(j.lagna),
        rashiLord = j.rashiLord.displayName,
        sunRashi = rasiDto(j.sunRashi),
        sunSign = j.sunSign,
        ayanamsa = j.ayanamsa,
        shakaSamvat = j.shakaSamvat,
        vikramSamvat = j.vikramSamvat,
        samvatsara = j.samvatsara,
    )

private fun dashaDto(p: DashaPeriod, fmt: DateTimeFormatter, maxLevel: Int): DashaPeriodDto =
    DashaPeriodDto(
        lord = p.lord.displayName,
        start = fmt.fmt(p.start),
        end = fmt.fmt(p.end),
        level = p.level,
        subPeriods =
            if (p.level >= maxLevel) emptyList() else p.subPeriods.map { dashaDto(it, fmt, maxLevel) },
    )

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun <T> AppResult<T>.orThrow(what: String): T =
    when (this) {
        is AppResult.Success -> data
        is AppResult.Failure -> throw IllegalArgumentException("$what failed: ${cause.message}")
    }

private fun isoFormatter(zoneId: ZoneId): DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(zoneId)

private fun DateTimeFormatter.fmt(i: Instant): String =
    format(JavaInstant.ofEpochMilli(i.toEpochMilliseconds()))

private fun DateTimeFormatter.fmtOrNull(i: Instant?): String? = i?.let { fmt(it) }

private fun parseInstant(
    value: String?,
    zoneId: ZoneId,
): Instant {
    if (value.isNullOrBlank()) {
        return JavaInstant.now().toKotlinInstant()
    }
    return try {
        if (value.contains('T')) {
            val ldt = LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ZonedDateTime.of(ldt, zoneId).toInstant().toKotlinInstant()
        } else {
            val ld = LocalDate.parse(value)
            ZonedDateTime.of(ld.atTime(6, 0), zoneId).toInstant().toKotlinInstant()
        }
    } catch (_: Exception) {
        JavaInstant.parse(value).toKotlinInstant()
    }
}
