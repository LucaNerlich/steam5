# Prometheus configuration template.
#
# Rendered into /tmp/prometheus.yml by /usr/local/bin/entrypoint.sh at
# container start. Edit the template, not the rendered copy. Placeholders
# (@@VAR@@) are substituted from the container environment.
#
# Mirror of the inlined `prometheus_config_template` in docker-compose.yml —
# keep them in sync (Coolify's Git deploy mode ships only the compose file).
global:
  scrape_interval: @@PROMETHEUS_SCRAPE_INTERVAL@@
  evaluation_interval: @@PROMETHEUS_SCRAPE_INTERVAL@@

rule_files:
  - /etc/prometheus/alert_rules.yml

scrape_configs:
  - job_name: steam5
    metrics_path: @@STEAM5_METRICS_PATH@@
    scheme: @@STEAM5_METRICS_SCHEME@@
    basic_auth:
      # Credentials are written to files by entrypoint.sh so they never appear
      # on the prometheus process command line. For unauthenticated targets,
      # remove the basic_auth block entirely.
      username_file: @@PROM_SECRETS_DIR@@/username
      password_file: @@PROM_SECRETS_DIR@@/password
    static_configs:
      - targets:
          - @@STEAM5_METRICS_TARGET@@

  - job_name: node
    static_configs:
      - targets:
          - @@NODE_EXPORTER_TARGET@@

  - job_name: cadvisor
    static_configs:
      - targets:
          - @@CADVISOR_TARGET@@

  - job_name: postgres
    static_configs:
      - targets:
          - @@POSTGRES_EXPORTER_TARGET@@
