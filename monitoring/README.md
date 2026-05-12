# Steam5 monitoring stack

Prometheus + Grafana, containerised, scraping the Steam5 Spring Boot backend
via `/actuator/prometheus` over HTTP basic auth.

## Overview

| Component  | Image                  | Default host port | Purpose                                                                  |
|------------|------------------------|-------------------|--------------------------------------------------------------------------|
| Prometheus | `prom/prometheus:v3.5.1` | `9090`            | Scrapes the backend, retains TSDB locally.                               |
| Grafana    | `grafana/grafana:12.3.1` | `3001`            | Visualises Prometheus data, ships 5 provisioned Steam5 dashboards.       |

The backend is **not** part of this stack — it must already be running and
exposing `/actuator/prometheus` (port `8081` by default in dev). The
Prometheus container reaches the backend via `host.docker.internal:8081`.

Layout:

```
monitoring/
├── docker-compose.yml
├── .env.example                # source of truth for tunable env vars
├── prometheus/
│   ├── prometheus.yml.tpl      # config template (see entrypoint.sh)
│   └── entrypoint.sh           # renders template + writes basic-auth secrets
└── grafana/
    ├── provisioning/
    │   ├── datasources/        # Prometheus datasource (auto-provisioned)
    │   └── dashboards/         # dashboards.yml provider (auto-provisioned)
    └── dashboards-json/        # JSON dashboards picked up by the provider
```

## Local development

### Prerequisites

- Docker (Engine 20.10+ or Docker Desktop on macOS/Windows).
- The Steam5 backend running locally with the management endpoint reachable on
  `localhost:8081` and `/actuator/prometheus` exposed. See the top-level
  [`README.md`](../README.md#actuator-dev-on-port-8081) for backend run instructions.
- Backend basic-auth credentials matching the Prometheus side (defaults
  `metrics` / `metrics` work for zero-config dev).

### Run

```bash
cd monitoring
cp .env.example .env
docker compose --env-file .env up -d
```

Tear down (named volumes survive):

```bash
docker compose down
# wipe TSDB and Grafana state too:
docker compose down -v
```

> **Heads-up — `MANAGEMENT_SERVER_PORT` ↔ `STEAM5_METRICS_TARGET` coupling.**
> If you override `MANAGEMENT_SERVER_PORT` on the backend (default `8081`), you **must** also
> override `STEAM5_METRICS_TARGET` in `monitoring/.env` to match (e.g.
> `host.docker.internal:18081`). The two values are coupled — Prometheus scrapes whatever you
> point it at via `STEAM5_METRICS_TARGET`, but the backend only listens for actuator traffic on
> `MANAGEMENT_SERVER_PORT`.

### Expected URLs

| Service    | URL                            | Notes                                                |
|------------|--------------------------------|------------------------------------------------------|
| Prometheus | <http://localhost:9090>        | `/targets` should show the `steam5` job as **UP**.   |
| Grafana    | <http://localhost:3001>        | Login `admin` / `admin` (override in `.env`).        |

In Grafana → **Dashboards**, all five Steam5 dashboards are pre-loaded under
the `steam5` tag — open any of them directly:

- <http://localhost:3001/d/steam5-jvm>
- <http://localhost:3001/d/steam5-http>
- <http://localhost:3001/d/steam5-hikari>
- <http://localhost:3001/d/steam5-caches>
- <http://localhost:3001/d/steam5-quartz>

## Dashboards

All dashboards filter by `application="steam5"` — a Micrometer common tag set
via `management.metrics.tags.application: steam5` in
`backend/src/main/resources/application.yml`, so multiple environments can
share one Prometheus.

| File                            | UID              | Title                       | Purpose                                                                          |
|---------------------------------|------------------|-----------------------------|----------------------------------------------------------------------------------|
| `dashboards-json/jvm.json`      | `steam5-jvm`     | Steam5 — JVM                | Heap & non-heap usage, GC pause time/rate, threads, classes loaded.              |
| `dashboards-json/http-server.json` | `steam5-http` | Steam5 — HTTP server        | Request rate, error rate, p50/p95/p99 latency, slowest endpoints, status codes. |
| `dashboards-json/hikari.json`   | `steam5-hikari`  | Steam5 — HikariCP           | Connection pool size, active/idle/pending, wait time, timeouts.                  |
| `dashboards-json/caches.json`   | `steam5-caches`  | Steam5 — Caches (Caffeine)  | Per-cache hit ratio, gets/puts/evictions, size; `$cache` template variable.      |
| `dashboards-json/quartz.json`   | `steam5-quartz`  | Steam5 — Quartz jobs        | Job execution rate by outcome, duration percentiles, currently executing jobs.   |

To add a new dashboard, drop a JSON file with a unique `uid` into
`grafana/dashboards-json/` and restart Grafana (`docker compose restart grafana`).

## Environment variables

Source of truth: [`.env.example`](.env.example). The table below summarises
every variable so you can scan it without opening the file.

| Variable                     | Default                              | Purpose                                                                 |
|------------------------------|--------------------------------------|-------------------------------------------------------------------------|
| `PROMETHEUS_IMAGE`           | `prom/prometheus:v3.5.1`             | Prometheus container image (pin a tag, never `:latest`).                |
| `PROMETHEUS_PORT`            | `9090`                               | Host port mapped to Prometheus.                                         |
| `PROMETHEUS_RETENTION`       | `15d`                                | TSDB retention window (`--storage.tsdb.retention.time`).                |
| `PROMETHEUS_SCRAPE_INTERVAL` | `15s`                                | Global `scrape_interval` and `evaluation_interval`.                     |
| `STEAM5_METRICS_TARGET`      | `host.docker.internal:8081`          | `host:port` Prometheus scrapes. Set to backend service DNS in prod.     |
| `STEAM5_METRICS_SCHEME`      | `http`                               | `http` or `https` for the scrape.                                       |
| `STEAM5_METRICS_PATH`        | `/actuator/prometheus`               | Metrics endpoint path on the backend.                                   |
| `STEAM5_METRICS_USERNAME`    | `metrics`                            | Basic-auth user Prometheus presents to the backend.                     |
| `STEAM5_METRICS_PASSWORD`    | `metrics`                            | Basic-auth password (must match backend `METRICS_PASSWORD`).            |
| `GRAFANA_IMAGE`              | `grafana/grafana:12.3.1`             | Grafana container image.                                                |
| `GRAFANA_PORT`               | `3001`                               | Host port mapped to Grafana (3001 to avoid Next.js dev on 3000).        |
| `GF_SECURITY_ADMIN_USER`     | `admin`                              | Initial Grafana admin username.                                         |
| `GF_SECURITY_ADMIN_PASSWORD` | `admin`                              | Initial Grafana admin password — **must override in production**.       |
| `GF_SERVER_ROOT_URL`         | `http://localhost:3001`              | Public URL Grafana uses for redirects, share links, OAuth callbacks.    |
| `GF_SERVER_DOMAIN`           | `localhost`                          | Grafana's external domain (used in cookies, links).                     |
| `GF_USERS_ALLOW_SIGN_UP`     | `false`                              | Disable self-service account creation.                                  |
| `PROMETHEUS_DATA_PATH`       | _(unset → named volume)_             | Optional — bind-mount data volume to a host path.                       |
| `GRAFANA_DATA_PATH`          | _(unset → named volume)_             | Optional — bind-mount data volume to a host path.                       |

## Cross-platform notes

The Prometheus container reaches the host's backend via `host.docker.internal`:

- **macOS / Windows (Docker Desktop)**: resolves to the host natively, no
  configuration needed.
- **Linux (Docker Engine 20.10+)**: requires the `host-gateway` alias, which
  is already declared in `docker-compose.yml`:

  ```yaml
  extra_hosts:
    - "host.docker.internal:host-gateway"
  ```

  No host-side change needed; this is handled by the compose file.

If your host firewall blocks Docker bridge traffic to port `8081`, allow it
explicitly (e.g. `sudo ufw allow from 172.16.0.0/12 to any port 8081`).


## Coolify production deployment

The monitoring stack is deployed as a **separate Coolify service** from the
backend. They communicate over Coolify's internal Docker network — never
expose `/actuator/prometheus` to the public internet.

### 1. Backend env vars (set on the existing `steam5-backend` service)

| Service          | Env var                          | Coolify location     | Example prod value                |
|------------------|----------------------------------|----------------------|-----------------------------------|
| `steam5-backend` | `METRICS_USERNAME`               | Service env (secret) | _generated, ≥ 16 chars_           |
| `steam5-backend` | `METRICS_PASSWORD`               | Service env (secret) | _generated, ≥ 32 chars_           |
| `steam5-backend` | `MANAGEMENT_SERVER_PORT`         | Service env          | `8081`                            |
| `steam5-backend` | `MANAGEMENT_ENDPOINTS_EXPOSURE`  | Service env          | `health,prometheus`               |

`METRICS_USERNAME` / `METRICS_PASSWORD` **must be overridden** in the coolify
profile — the backend logs a startup `WARN` if the defaults (`metrics` /
`metrics`) are still in use. Generate strong values with e.g.
`openssl rand -base64 24`.

### 2. Exposing the management port to the monitoring stack

The management port (`MANAGEMENT_SERVER_PORT=8081`) only needs to be
reachable by the Prometheus container, **not** the public internet. Two
options, in order of preference:

1. **Internal Coolify network (recommended).** Attach the monitoring
   service to the same Coolify project / network as the backend. Prometheus
   then resolves the backend by its Docker DNS name
   (e.g. `steam5-backend:8081`). Do **not** add a public domain or open a
   firewall port for `8081`.
2. **Public domain (only if internal networking is not feasible).** Expose
   `8081` behind Coolify's reverse proxy on a dedicated subdomain
   (e.g. `metrics.steam5.org`), enable HTTPS, and rely on basic auth +
   IP allowlist. Set `STEAM5_METRICS_SCHEME=https` and
   `STEAM5_METRICS_TARGET=metrics.steam5.org:443`. This is strictly less
   secure than option 1 — basic auth is the only barrier.

### 3. Monitoring service env vars

Create a new Coolify service from this `monitoring/docker-compose.yml`. Set:

| Service      | Env var                       | Coolify location     | Example prod value                                |
|--------------|-------------------------------|----------------------|---------------------------------------------------|
| `prometheus` | `PROMETHEUS_IMAGE`            | Service env          | `prom/prometheus:v3.5.1` (pin a tag for prod)     |
| `prometheus` | `PROMETHEUS_PORT`             | Service env          | `9090`                                            |
| `prometheus` | `STEAM5_METRICS_TARGET`       | Service env          | `steam5-backend:8081`                             |
| `prometheus` | `STEAM5_METRICS_SCHEME`       | Service env          | `http` (internal) or `https` (public)             |
| `prometheus` | `STEAM5_METRICS_PATH`         | Service env          | `/actuator/prometheus`                            |
| `prometheus` | `STEAM5_METRICS_USERNAME`     | Service env (secret) | _= backend `METRICS_USERNAME`_                    |
| `prometheus` | `STEAM5_METRICS_PASSWORD`     | Service env (secret) | _= backend `METRICS_PASSWORD`_                    |
| `prometheus` | `PROMETHEUS_RETENTION`        | Service env          | `15d` (or higher with sized volume)               |
| `prometheus` | `PROMETHEUS_SCRAPE_INTERVAL`  | Service env          | `15s`                                             |
| `grafana`    | `GRAFANA_IMAGE`               | Service env          | `grafana/grafana:12.3.1` (pin a tag for prod)     |
| `grafana`    | `GF_SECURITY_ADMIN_USER`      | Service env          | _operator choice (e.g. `steam5-admin`)_           |
| `grafana`    | `GF_SECURITY_ADMIN_PASSWORD`  | Service env (secret) | _strong password, ≥ 16 chars_                     |
| `grafana`    | `GF_SERVER_ROOT_URL`          | Service env          | `https://grafana.steam5.org`                      |
| `grafana`    | `GF_SERVER_DOMAIN`            | Service env          | `grafana.steam5.org`                              |
| `grafana`    | `GF_USERS_ALLOW_SIGN_UP`      | Service env          | `false`                                           |

### 4. Persistent volumes

The compose file declares two named volumes — both **must** be backed by
Coolify persistent storage so data survives redeploys:

| Volume            | Mount path inside container | Holds                                          |
|-------------------|-----------------------------|------------------------------------------------|
| `prometheus-data` | `/prometheus`               | TSDB (size with `PROMETHEUS_RETENTION`).       |
| `grafana-data`    | `/var/lib/grafana`          | Grafana SQLite DB, alert state, user prefs.    |

In Coolify → service → **Storages**, mark both volumes as persistent. Without
this, redeploys wipe metric history and any Grafana customisations.

#### Bind-mount overrides (optional)

By default the volumes above are Docker named volumes living under
`/var/lib/docker/volumes`. If you want the data on a known host path —
e.g. on a VPS with a dedicated `/data` partition for easier backups,
snapshots, or filesystem-level management — set:

```
PROMETHEUS_DATA_PATH=/data/volumes/steam5/prometheus
GRAFANA_DATA_PATH=/data/volumes/steam5/grafana
```

When either variable is set to an absolute host path, docker compose
bind-mounts that directory instead of using the named volume. The host
directory **must exist and be writable by the container UID**
(Prometheus: `65534/nobody`, Grafana: `472`) before `docker compose up` —
Coolify (or whoever provisions the host) is responsible for creating and
chowning the directories. Leave both unset to keep the named-volume
behaviour.

Pre-deploy host prep (run once on the host, as root):

```bash
sudo mkdir -p /data/volumes/steam5/prometheus /data/volumes/steam5/grafana
sudo chown -R 65534:65534 /data/volumes/steam5/prometheus   # Prometheus runs as 'nobody' (UID 65534)
sudo chown -R 472:0       /data/volumes/steam5/grafana      # Grafana container runs as UID 472
```

Without correct ownership the containers will fail on first boot with
`permission denied` errors when they try to write to the mount. This
only applies when bind-mounting; the named-volume case is handled
automatically by Docker.

### 5. Grafana behind Coolify's reverse proxy

Coolify's Traefik handles TLS and routing. Add a Coolify domain mapping
`grafana.steam5.org` → service `grafana` port `3000` (the container port,
**not** the host-side `GRAFANA_PORT`). Then set Grafana env:

```
GF_SERVER_ROOT_URL=https://grafana.steam5.org
GF_SERVER_DOMAIN=grafana.steam5.org
```

Setting these correctly is required for share links, OAuth callbacks, and
correct cookie scoping. Do **not** add a public domain mapping for
Prometheus — keep it internal-only.

### 6. Quick verify in production

After deploy, from any host that can reach the backend (e.g. inside
Coolify's terminal), confirm the scrape target works:

```bash
curl -u "$METRICS_USERNAME:$METRICS_PASSWORD" \
  https://<backend-host>/actuator/prometheus | head
```

You should see Prometheus exposition text starting with `# HELP …`. Then in
the Prometheus UI (<https://prometheus.steam5.org/targets> or via Coolify
port forward), the `steam5` job should be **UP**. In Grafana, open any
dashboard — panels should render within one scrape interval.

## Troubleshooting

| Symptom                                         | Likely cause                                                                  | Fix                                                                                                              |
|-------------------------------------------------|-------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| Target **DOWN**, error `401 Unauthorized`       | Basic-auth credentials don't match backend.                                   | Ensure `STEAM5_METRICS_USERNAME` / `STEAM5_METRICS_PASSWORD` match backend `METRICS_USERNAME` / `METRICS_PASSWORD`. Restart Prometheus to re-render the secret files. |
| Target **DOWN**, error `connection refused`     | Wrong host or port.                                                           | Verify `STEAM5_METRICS_TARGET`. Locally that's `host.docker.internal:8081`; in Coolify it's the backend service DNS, e.g. `steam5-backend:8081`. Confirm the backend is listening on `MANAGEMENT_SERVER_PORT`. |
| Target **DOWN**, error `no such host`           | Linux without `host-gateway` alias, or wrong service name in Coolify.         | Compose file already declares `host.docker.internal:host-gateway`; ensure Docker Engine ≥ 20.10. In Coolify, use the exact service name. |
| Dashboards empty, no data                        | No traffic generated yet, or wrong datasource.                                | Hit the backend (`curl http://localhost:8080/actuator/health`); wait one scrape interval. In Grafana → **Connections → Data sources**, confirm `Prometheus` is healthy. |
| Dashboards empty, datasource healthy             | Backend not emitting `application="steam5"` label, or different label value.  | Confirm `management.metrics.tags.application: steam5` is set in the backend's `application.yml`. Check Prometheus → **Graph** with `up{job="steam5"}`; if it returns `1` but a query like `jvm_memory_used_bytes{application="steam5"}` is empty, the tag is missing or different. |
| Grafana shows "If you're seeing this Grafana has failed to load…" | `GF_SERVER_ROOT_URL` mismatch behind reverse proxy.                            | Set `GF_SERVER_ROOT_URL` to the **external** HTTPS URL and `GF_SERVER_DOMAIN` to the same hostname. Restart Grafana. |
| Prometheus container restarts in a loop         | Bad placeholder substitution in `prometheus.yml.tpl`, or missing env var.     | `docker compose logs prometheus` — the entrypoint script `sed` step will surface unresolved `@@VAR@@` markers. Confirm all `STEAM5_METRICS_*` vars are set. |
| Backend logs `WARN … METRICS_USERNAME … default` | Production running with `metrics` / `metrics` defaults.                       | Set strong `METRICS_USERNAME` / `METRICS_PASSWORD` on the backend service in Coolify; redeploy.                  |
