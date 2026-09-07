# Multi-stage build for Adhyatmic astro-engine (Ktor + frozen Vedic Mitra core)

FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY src ./src
COPY third_party ./third_party

RUN chmod +x gradlew \
  && ./gradlew --no-daemon installDist -q

FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

RUN useradd --system --uid 10001 --create-home appuser
COPY --from=build /app/build/install/astro-engine/ ./

USER appuser
ENV VEDIC_ENGINE_PORT=8090
EXPOSE 8090

CMD ["./bin/astro-engine"]
