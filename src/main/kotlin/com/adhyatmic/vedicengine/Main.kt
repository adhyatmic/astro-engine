/*
 * Adhyatmic Vedic engine sidecar — commercial engine-only use of Vedic Mitra
 * :core:astronomy. Does not include :feature:* UI. See LICENSING.md.
 */

package com.adhyatmic.vedicengine

import io.github.vedicmitra.core.astronomy.DefaultAstronomyEngine
import io.github.vedicmitra.core.astronomy.PersonalMuhurtaContext
import io.github.vedicmitra.core.astronomy.RASHI_NAMES
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
import kotlin.time.toKotlinInstant

private object EngineDispatchers : DispatcherProvider {
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val main: CoroutineDispatcher = Dispatchers.Default
}

@Serializable
data class BirthInput(
    val at: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class RashifalRequest(
    val rashi: String? = null,
    val rasiIndex: Int? = null,
    val latitude: Double = 28.6139,
    val longitude: Double = 77.2090,
    val zone: String = "Asia/Kolkata",
    /** ISO date or date-time; default now in [zone]. */
    val at: String? = null,
    val days: Int = 7,
    val birth: BirthInput? = null,
)

@Serializable
data class TaraDto(
    val name: String,
    val strength: String,
    val number: Int,
)

@Serializable
data class DayDto(
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
    val today: DayDto,
    val week: List<DayDto>,
)

@Serializable
data class ErrorBody(
    val error: String,
)

@Serializable
data class HealthBody(
    val ok: Boolean = true,
)

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
            get("/health") {
                call.respond(HealthBody())
            }
            post("/v1/rashifal") {
                val body = call.receive<RashifalRequest>()
                call.respond(computeRashifal(body))
            }
        }
    }.start(wait = true)
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
    val instant = parseInstant(req.at, zoneId)
    val location = GeoCoordinates(latitude = req.latitude, longitude = req.longitude)
    val engine = DefaultAstronomyEngine(EngineDispatchers)

    var person: PersonalMuhurtaContext? = null
    var personalized = false
    val birth = req.birth
    if (birth != null) {
        val birthLoc =
            GeoCoordinates(
                latitude = birth.latitude ?: req.latitude,
                longitude = birth.longitude ?: req.longitude,
            )
        val birthInstant = parseInstant(birth.at, zoneId)
        when (val chartResult = engine.natalChartAt(birthInstant, birthLoc)) {
            is AppResult.Failure ->
                throw IllegalArgumentException("natalChartAt failed: ${chartResult.cause.message}")
            is AppResult.Success -> {
                val chart =
                    chartResult.data
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
        }
    }

    when (
        val outlook =
            engine.rashiOutlook(
                rasiIndex = rasiIndex,
                instant = instant,
                location = location,
                person = person,
                days = req.days.coerceIn(1, 14),
            )
    ) {
        is AppResult.Failure ->
            throw IllegalArgumentException("rashiOutlook failed: ${outlook.cause.message}")
        is AppResult.Success -> {
            val o = outlook.data
            val fmt =
                DateTimeFormatter
                    .ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
                    .withZone(zoneId)

            fun format(i: kotlin.time.Instant): String =
                fmt.format(JavaInstant.ofEpochMilli(i.toEpochMilliseconds()))

            fun dayDto(d: io.github.vedicmitra.core.astronomy.RashiDay): DayDto =
                DayDto(
                    atSunrise = format(d.atSunrise),
                    band = d.band.name.lowercase(),
                    bandLabel = d.band.label,
                    chandrabala = d.chandrabala.name.lowercase(),
                    chandraPosition = d.chandraPosition,
                    moonRasi = d.moonRasi.name,
                    nakshatra = d.nakshatra.name,
                    tara =
                        d.tara?.let {
                            TaraDto(
                                name = it.name,
                                strength = it.strength.name.lowercase(),
                                number = it.number,
                            )
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
    }
}

private fun parseInstant(
    value: String?,
    zoneId: ZoneId,
): kotlin.time.Instant {
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
