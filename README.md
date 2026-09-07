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

## Blog env

```
VEDIC_ENGINE_URL=http://127.0.0.1:8090
VEDIC_ENGINE_TIMEOUT_MS=8000
```
# astro-engine
