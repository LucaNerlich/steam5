#!/bin/sh
# Templating shim for the Prometheus container.
#
# Prometheus does not natively expand ${VAR} placeholders inside its config
# (only --enable-feature=expand-external-labels covers external_labels). This
# script renders /etc/prometheus/prometheus.yml.tpl into /tmp/prometheus.yml
# at startup, substituting @@VAR@@ markers from the container's environment,
# then execs the prometheus binary with that rendered file. Basic-auth
# credentials are written to per-file paths read via username_file /
# password_file so they never appear on the prometheus command line.
set -e

RENDERED=/tmp/prometheus.yml
SECRETS_DIR=/tmp/prometheus-secrets

mkdir -p "$SECRETS_DIR"
# `printf %s` (no newline) so basic-auth values don't include a trailing \n.
printf '%s' "${STEAM5_METRICS_USERNAME:-}" > "$SECRETS_DIR/username"
printf '%s' "${STEAM5_METRICS_PASSWORD:-}" > "$SECRETS_DIR/password"

sed \
  -e "s|@@PROMETHEUS_SCRAPE_INTERVAL@@|${PROMETHEUS_SCRAPE_INTERVAL:-15s}|g" \
  -e "s|@@STEAM5_METRICS_TARGET@@|${STEAM5_METRICS_TARGET:-host.docker.internal:8081}|g" \
  -e "s|@@STEAM5_METRICS_SCHEME@@|${STEAM5_METRICS_SCHEME:-http}|g" \
  -e "s|@@STEAM5_METRICS_PATH@@|${STEAM5_METRICS_PATH:-/actuator/prometheus}|g" \
  -e "s|@@PROM_SECRETS_DIR@@|${SECRETS_DIR}|g" \
  /etc/prometheus/prometheus.yml.tpl > "$RENDERED"

exec /bin/prometheus --config.file="$RENDERED" "$@"
