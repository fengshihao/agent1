# shellcheck shell=bash
# 启动 dev/catalog-sample 静态服务，供 sync / UC-06 E2E 使用。
# source 后调用 catalog_sample_server_start / catalog_sample_server_stop

catalog_sample_server_start() {
  local repo_root="${1:?repo root}"
  CATALOG_SAMPLE_PORT="${CATALOG_SAMPLE_PORT:-8765}"
  CATALOG_SAMPLE_TMP="${CATALOG_SAMPLE_TMP:-$(mktemp -d)}"
  CATALOG_SAMPLE_PID=""

  cp -a "${repo_root}/dev/catalog-sample/." "${CATALOG_SAMPLE_TMP}/"
  local base="http://127.0.0.1:${CATALOG_SAMPLE_PORT}/"
  if sed --version >/dev/null 2>&1; then
    sed -i "s|https://example.invalid/agent1/catalog-sample/|${base}|g" \
      "${CATALOG_SAMPLE_TMP}/catalog-index.json"
  else
    sed -i '' "s|https://example.invalid/agent1/catalog-sample/|${base}|g" \
      "${CATALOG_SAMPLE_TMP}/catalog-index.json"
  fi

  python3 -m http.server "${CATALOG_SAMPLE_PORT}" --directory "${CATALOG_SAMPLE_TMP}" \
    >/tmp/catalog-sample-http.log 2>&1 &
  CATALOG_SAMPLE_PID=$!

  local i
  for i in $(seq 1 30); do
    if curl -sf "http://127.0.0.1:${CATALOG_SAMPLE_PORT}/catalog-index.json" >/dev/null; then
      export AGENT1_CATALOG_MANIFEST_URL="http://127.0.0.1:${CATALOG_SAMPLE_PORT}/catalog-index.json"
      return 0
    fi
    sleep 0.2
  done
  echo "catalog 样例 HTTP 启动失败，见 /tmp/catalog-sample-http.log" >&2
  return 1
}

catalog_sample_server_stop() {
  if [[ -n "${CATALOG_SAMPLE_PID:-}" ]] && kill -0 "${CATALOG_SAMPLE_PID}" 2>/dev/null; then
    kill "${CATALOG_SAMPLE_PID}" 2>/dev/null || true
    wait "${CATALOG_SAMPLE_PID}" 2>/dev/null || true
  fi
  CATALOG_SAMPLE_PID=""
}
