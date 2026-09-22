# Astro engine (Adhytm astronomy)

Self-contained production sidecar for Adhyatmic rashifal / panchang / kundli.

| Path | Contents |
|------|----------|
| `src/` | Ktor wrapper (`com.adhytm.engine`) |
| `third_party/adhytm/` | Frozen `com.adhytm.astronomy` + `com.adhytm.common` |

Pinned upstream SHA: `third_party/adhytm/UPSTREAM_COMMIT.txt`.

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
- Matchmaking: `POST https://astro.adhytm.com/v1/matchmaking` (Ashtakoota + porutham + mangal)
- Also: `/v1/varga`, `/v1/dasha`, `/v1/ashtakavarga`, `/v1/doshas`, `/v1/drishti`, `/v1/sankalpa`, `/v1/primer`, `/v1/glossary`
