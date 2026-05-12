# Prometheus configuration template.
#
# Rendered into /tmp/prometheus.yml by /usr/local/bin/entrypoint.sh at
# container start. Edit the template, not the rendered copy. Placeholders
# (@@VAR@@) are substituted from the container environment.
global:
  scrape_interval: @@PROMETHEUS_SCRAPE_INTERVAL@@
  evaluation_interval: @@PROMETHEUS_SCRAPE_INTERVAL@@

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
