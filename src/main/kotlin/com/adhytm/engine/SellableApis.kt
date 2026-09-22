/*
 * Adhyatmic astro-engine — sellable dedicated APIs over frozen com.adhytm.astronomy.
 */

package com.adhytm.engine

import com.adhytm.astronomy.DashaSystem
import com.adhytm.astronomy.Graha
import com.adhytm.astronomy.NatalChart
import com.adhytm.astronomy.PanchangaConcept
import com.adhytm.astronomy.PanchangaGlossary
import com.adhytm.astronomy.PanchangaPrimer
import com.adhytm.astronomy.Varga
import com.adhytm.astronomy.aspectLinks
import com.adhytm.astronomy.gandaMoolaDoshaOf
import com.adhytm.astronomy.kalaSarpaDoshaOf
import com.adhytm.astronomy.mangalDoshaOf
import com.adhytm.astronomy.sankalpaFrame
import com.adhytm.astronomy.vargaChart
import com.adhytm.common.model.GeoCoordinates
import com.adhytm.common.model.MaasaReckoning
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import java.time.ZoneId
import kotlin.time.Instant

// ---------------------------------------------------------------------------
// Request / response DTOs
// ---------------------------------------------------------------------------

@Serializable
data class VargaRequest(
    val latitude: Double = 28.6139,
    val longitude: Double = 77.2090,
    val zone: String = "Asia/Kolkata",
    val at: String,
    /** Enum name (D9) or displayName (Navamsha). Defaults to Navamsha. */
    val varga: String = "D9",
)

@Serializable
data class VargaInfoDto(
    val name: String,
    val displayName: String,
    val divisions: Int,
    val needsExactBirthTime: Boolean,
)

@Serializable
data class VargaGrahaDto(
    val graha: String,
    val displayName: String,
    val rasi: RasiDto,
    val house: Int,
)

@Serializable
data class VargaResponse(
    val source: String = "adhytm-astronomy",
    val instant: String,
    val latitude: Double,
    val longitude: Double,
    val zone: String,
    val varga: VargaInfoDto,
    val lagna: RasiDto,
    val houses: List<RasiDto>,
    val grahas: List<VargaGrahaDto>,
)

@Serializable
data class DashaRequest(
    val latitude: Double = 28.6139,
    val longitude: Double = 77.2090,
    val zone: String = "Asia/Kolkata",
    val at: String,
    /** VIMSHOTTARI | ASHTOTTARI | YOGINI (or display name). */
    val system: String = "VIMSHOTTARI",
    val dashaDepth: Int = 2,
)

@Serializable
data class DashaSystemInfoDto(
    val name: String,
    val displayName: String,
    val totalYears: Int,
)

@Serializable
data class DashaResponse(
    val source: String = "adhytm-astronomy",
    val instant: String,
    val latitude: Double,
    val longitude: Double,
    val zone: String,
    val system: DashaSystemInfoDto,
    val periods: List<DashaPeriodDto>,
)

@Serializable
data class AshtakavargaRequest(
    val latitude: Double = 28.6139,
    val longitude: Double = 77.2090,
    val zone: String = "Asia/Kolkata",
    val at: String,
)

@Serializable
data class BinnaRowDto(
    val graha: String,
    val displayName: String,
    /** Bindus per rashi, Mesha first (12 ints, each 0..8). */
    val bindus: List<Int>,
    val total: Int,
)

@Serializable
data class AshtakavargaResponse(
    val source: String = "adhytm-astronomy",
    val instant: String,
    val latitude: Double,
    val longitude: Double,
    val zone: String,
    val sarva: List<Int>,
    val sarvaTotal: Int,
    val binna: List<BinnaRowDto>,
)

@Serializable
data class DoshasRequest(
    val latitude: Double = 28.6139,
    val longitude: Double = 77.2090,
    val zone: String = "Asia/Kolkata",
    val at: String,
)

@Serializable
data class ChartDoshaDto(
    val name: String,
    val present: Boolean,
    val rule: String,
    val summary: String? = null,
)

@Serializable
data class DoshasResponse(
    val source: String = "adhytm-astronomy",
    val instant: String,
    val latitude: Double,
    val longitude: Double,
    val zone: String,
    val mangal: MangalDoshaDto,
    val kalaSarpa: ChartDoshaDto,
    val gandaMoola: ChartDoshaDto,
)

@Serializable
data class DrishtiRequest(
    val latitude: Double = 28.6139,
    val longitude: Double = 77.2090,
    val zone: String = "Asia/Kolkata",
    val at: String,
)

@Serializable
data class AspectLinkDto(
    val fromGraha: String,
    val fromDisplayName: String,
    val fromRasi: RasiDto,
    val toGraha: String,
    val toDisplayName: String,
    val toRasi: RasiDto,
    val house: Int,
)

@Serializable
data class DrishtiResponse(
    val source: String = "adhytm-astronomy",
    val instant: String,
    val latitude: Double,
    val longitude: Double,
    val zone: String,
    val aspects: List<AspectLinkDto>,
)

@Serializable
data class SankalpaRequest(
    val latitude: Double = 28.6139,
    val longitude: Double = 77.2090,
    val zone: String = "Asia/Kolkata",
    val at: String? = null,
    val place: String? = null,
    /** AMANTA or PURNIMANTA. */
    val maasaReckoning: String = "AMANTA",
)

@Serializable
data class SankalpaCoordinateDto(
    val label: String,
    val value: String,
)

@Serializable
data class SankalpaResponse(
    val source: String = "adhytm-astronomy",
    val instant: String,
    val latitude: Double,
    val longitude: Double,
    val zone: String,
    val place: String? = null,
    val coordinates: List<SankalpaCoordinateDto>,
    val asText: String,
)

@Serializable
data class PrimerEntryDto(
    val concept: String,
    val title: String,
    val oneLine: String,
    val body: String,
)

@Serializable
data class GlossaryResponse(
    val name: String,
    val significance: String?,
)

// ---------------------------------------------------------------------------
// Routes
// ---------------------------------------------------------------------------

internal fun Route.installSellableRoutes() {
    get("/v1/vargas") { call.respond(listVargas()) }
    post("/v1/varga") { call.respond(computeVarga(call.receive())) }

    get("/v1/dasha/systems") { call.respond(listDashaSystems()) }
    post("/v1/dasha") { call.respond(computeDasha(call.receive())) }

    post("/v1/ashtakavarga") { call.respond(computeAshtakavarga(call.receive())) }
    post("/v1/doshas") { call.respond(computeDoshas(call.receive())) }
    post("/v1/drishti") { call.respond(computeDrishti(call.receive())) }
    post("/v1/sankalpa") { call.respond(computeSankalpa(call.receive())) }

    get("/v1/primer") { call.respond(listPrimer()) }
    get("/v1/primer/{concept}") {
        val concept = call.parameters["concept"]
            ?: throw IllegalArgumentException("concept path param required")
        call.respond(primerOf(concept))
    }
    get("/v1/glossary") {
        val name = call.request.queryParameters["name"]
            ?: throw IllegalArgumentException("Query param 'name' is required")
        call.respond(GlossaryResponse(name = name, significance = PanchangaGlossary.significanceOf(name)))
    }
}

internal fun sellableRouteInfos(): List<RouteInfo> =
    listOf(
        RouteInfo("GET", "/v1/vargas", "List supported divisional charts (vargas)"),
        RouteInfo("POST", "/v1/varga", "Divisional chart (D-9 Navamsha, D-10, …) for a birth"),
        RouteInfo("GET", "/v1/dasha/systems", "List dasha systems (Vimshottari, Ashtottari, Yogini)"),
        RouteInfo("POST", "/v1/dasha", "Dasha timeline for a birth in a chosen system"),
        RouteInfo("POST", "/v1/ashtakavarga", "Sarva + binna ashtakavarga bindus for a birth"),
        RouteInfo("POST", "/v1/doshas", "Mangal, Kala Sarpa, and Ganda Moola for a birth"),
        RouteInfo("POST", "/v1/drishti", "Vedic whole-sign graha aspects for a birth"),
        RouteInfo("POST", "/v1/sankalpa", "Sankalpa time-frame from a panchanga snapshot"),
        RouteInfo("GET", "/v1/primer", "Plain-language panchanga concept primer (all)"),
        RouteInfo("GET", "/v1/primer/{concept}", "Primer entry for one concept"),
        RouteInfo("GET", "/v1/glossary", "Significance blurb for a named panchanga item (?name=)"),
    )

// ---------------------------------------------------------------------------
// Handlers
// ---------------------------------------------------------------------------

private fun listVargas(): List<VargaInfoDto> =
    Varga.entries.map {
        VargaInfoDto(
            name = it.name,
            displayName = it.displayName,
            divisions = it.divisions,
            needsExactBirthTime = it.needsExactBirthTime,
        )
    }

private fun listDashaSystems(): List<DashaSystemInfoDto> =
    DashaSystem.entries.map {
        DashaSystemInfoDto(name = it.name, displayName = it.displayName, totalYears = it.totalYears)
    }

private fun listPrimer(): List<PrimerEntryDto> =
    PanchangaConcept.entries.map { c ->
        val e = PanchangaPrimer.of(c)
        PrimerEntryDto(concept = c.name, title = e.title, oneLine = e.oneLine, body = e.body)
    }

private fun primerOf(raw: String): PrimerEntryDto {
    val concept =
        PanchangaConcept.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "Unknown concept '$raw'. See GET /v1/primer for the list.",
            )
    val e = PanchangaPrimer.of(concept)
    return PrimerEntryDto(concept = concept.name, title = e.title, oneLine = e.oneLine, body = e.body)
}

private suspend fun computeVarga(req: VargaRequest): VargaResponse {
    val varga = parseVarga(req.varga)
    val (chart, instant, zoneId) = loadNatal(req.at, req.latitude, req.longitude, req.zone)
    val fmt = isoFormatter(zoneId)
    val vc = chart.vargaChart(varga)
    return VargaResponse(
        instant = fmt.fmt(instant),
        latitude = req.latitude,
        longitude = req.longitude,
        zone = req.zone,
        varga =
            VargaInfoDto(
                name = varga.name,
                displayName = varga.displayName,
                divisions = varga.divisions,
                needsExactBirthTime = varga.needsExactBirthTime,
            ),
        lagna = rasiDto(vc.lagna),
        houses = vc.houses.map(::rasiDto),
        grahas =
            chart.grahas.map { g ->
                val sign = g.varga(varga)
                VargaGrahaDto(
                    graha = g.graha.name,
                    displayName = g.graha.displayName,
                    rasi = rasiDto(sign),
                    house = vc.houseOf(g),
                )
            },
    )
}

private suspend fun computeDasha(req: DashaRequest): DashaResponse {
    val system = parseDashaSystem(req.system)
    val (chart, instant, zoneId) = loadNatal(req.at, req.latitude, req.longitude, req.zone)
    val fmt = isoFormatter(zoneId)
    val depth = req.dashaDepth.coerceIn(1, 3)
    val periods = chart.dasha(system)
    return DashaResponse(
        instant = fmt.fmt(instant),
        latitude = req.latitude,
        longitude = req.longitude,
        zone = req.zone,
        system =
            DashaSystemInfoDto(
                name = system.name,
                displayName = system.displayName,
                totalYears = system.totalYears,
            ),
        periods = periods.map { dashaDto(it, fmt, depth) },
    )
}

private suspend fun computeAshtakavarga(req: AshtakavargaRequest): AshtakavargaResponse {
    val (chart, instant, zoneId) = loadNatal(req.at, req.latitude, req.longitude, req.zone)
    val fmt = isoFormatter(zoneId)
    val binnaGrahas =
        listOf(
            Graha.SUN,
            Graha.MOON,
            Graha.MANGALA,
            Graha.BUDHA,
            Graha.GURU,
            Graha.SHUKRA,
            Graha.SHANI,
        )
    val binna =
        binnaGrahas.map { g ->
            val row = chart.binnashtakavarga(g)
            BinnaRowDto(
                graha = g.name,
                displayName = g.displayName,
                bindus = row,
                total = row.sum(),
            )
        }
    val sarva = chart.sarvashtakavarga
    return AshtakavargaResponse(
        instant = fmt.fmt(instant),
        latitude = req.latitude,
        longitude = req.longitude,
        zone = req.zone,
        sarva = sarva,
        sarvaTotal = sarva.sum(),
        binna = binna,
    )
}

private suspend fun computeDoshas(req: DoshasRequest): DoshasResponse {
    val (chart, instant, zoneId) = loadNatal(req.at, req.latitude, req.longitude, req.zone)
    val fmt = isoFormatter(zoneId)
    val kala = kalaSarpaDoshaOf(chart)
    val ganda = gandaMoolaDoshaOf(chart)
    return DoshasResponse(
        instant = fmt.fmt(instant),
        latitude = req.latitude,
        longitude = req.longitude,
        zone = req.zone,
        mangal = mangalDoshaDto(mangalDoshaOf(chart)),
        kalaSarpa =
            ChartDoshaDto(
                name = kala.name,
                present = kala.present,
                rule = kala.rule,
                summary = kala.summary,
            ),
        gandaMoola =
            ChartDoshaDto(
                name = ganda.name,
                present = ganda.present,
                rule = ganda.rule,
                summary = ganda.summary,
            ),
    )
}

private suspend fun computeDrishti(req: DrishtiRequest): DrishtiResponse {
    val (chart, instant, zoneId) = loadNatal(req.at, req.latitude, req.longitude, req.zone)
    val fmt = isoFormatter(zoneId)
    return DrishtiResponse(
        instant = fmt.fmt(instant),
        latitude = req.latitude,
        longitude = req.longitude,
        zone = req.zone,
        aspects =
            chart.aspectLinks().map { a ->
                AspectLinkDto(
                    fromGraha = a.fromGraha.name,
                    fromDisplayName = a.fromGraha.displayName,
                    fromRasi = rasiDto(a.fromRasi),
                    toGraha = a.toGraha.name,
                    toDisplayName = a.toGraha.displayName,
                    toRasi = rasiDto(a.toRasi),
                    house = a.house,
                )
            },
    )
}

private suspend fun computeSankalpa(req: SankalpaRequest): SankalpaResponse {
    val zoneId = ZoneId.of(req.zone)
    val fmt = isoFormatter(zoneId)
    val instant = parseInstant(req.at, zoneId)
    val location = GeoCoordinates(req.latitude, req.longitude)
    val snap = engine.snapshotAt(instant, location).orThrow("snapshotAt")
    val reckoning =
        MaasaReckoning.entries.firstOrNull { it.name.equals(req.maasaReckoning, ignoreCase = true) }
            ?: throw IllegalArgumentException("maasaReckoning must be AMANTA or PURNIMANTA")
    val frame = snap.sankalpaFrame(place = req.place, reckoning = reckoning)
    return SankalpaResponse(
        instant = fmt.fmt(instant),
        latitude = req.latitude,
        longitude = req.longitude,
        zone = req.zone,
        place = frame.place,
        coordinates = frame.coordinates.map { SankalpaCoordinateDto(it.label, it.value) },
        asText = frame.asText,
    )
}

// ---------------------------------------------------------------------------
// Shared loaders / parsers
// ---------------------------------------------------------------------------

private suspend fun loadNatal(
    at: String,
    latitude: Double,
    longitude: Double,
    zone: String,
): Triple<NatalChart, Instant, ZoneId> {
    require(at.isNotBlank()) { "at is required (ISO birth date-time)" }
    val zoneId = ZoneId.of(zone)
    val instant = parseInstant(at, zoneId)
    val location = GeoCoordinates(latitude, longitude)
    val chart =
        engine.natalChartAt(instant, location).orThrow("natalChartAt")
            ?: throw IllegalArgumentException("natalChartAt returned no chart")
    return Triple(chart, instant, zoneId)
}

private fun parseVarga(raw: String): Varga =
    Varga.entries.firstOrNull {
        it.name.equals(raw, ignoreCase = true) ||
            it.displayName.equals(raw, ignoreCase = true) ||
            raw.equals("D-${it.divisions}", ignoreCase = true) ||
            raw.equals("D${it.divisions}", ignoreCase = true)
    } ?: throw IllegalArgumentException(
        "Unknown varga '$raw'. See GET /v1/vargas.",
    )

private fun parseDashaSystem(raw: String): DashaSystem =
    DashaSystem.entries.firstOrNull {
        it.name.equals(raw, ignoreCase = true) || it.displayName.equals(raw, ignoreCase = true)
    } ?: throw IllegalArgumentException(
        "Unknown dasha system '$raw'. See GET /v1/dasha/systems.",
    )
