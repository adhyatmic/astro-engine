# Astro engine (Vedic Mitra engine-only)

Self-contained production sidecar for Adhyatmic rashifal.

| Path | Contents |
|------|----------|
| `src/` | Our Ktor wrapper (`POST /v1/rashifal`, `GET /health`) |
| `third_party/vedic-mitra/` | Frozen `:core:astronomy` + `:core:common` (DI stripped) |

**Not included:** Vedic Mitra `feature/*` UI (app license exclusive).

Pinned upstream: `third_party/vedic-mitra/UPSTREAM_COMMIT.txt`.

## Run

```bash
cd astro-engine
./gradlew run
# :8090
```

```bash
./gradlew installDist
```

## Docker

```bash
cd astro-engine
docker build -t astro-engine .
docker run --rm -p 8090:8090 astro-engine
# optional: -e VEDIC_ENGINE_PORT=8090
```

## Blog env

```
VEDIC_ENGINE_URL=https://astro.adhytm.com
VEDIC_ENGINE_TIMEOUT_MS=8000
```

## Production (Dokploy)

- App: `astro-engine` in **Adhyatmic Blog** → production  
- Health: https://astro.adhytm.com/health  
- API: `POST https://astro.adhytm.com/v1/rashifal`
