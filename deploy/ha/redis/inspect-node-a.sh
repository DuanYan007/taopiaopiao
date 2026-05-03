#!/usr/bin/env bash

set -euo pipefail

REDIS_PORT="${REDIS_PORT:-6349}"
REDIS_CONTAINER_NAME="${REDIS_CONTAINER_NAME:-redis}"

find_redis_container() {
  if ! command -v docker >/dev/null 2>&1; then
    return 1
  fi

  if docker ps --format '{{.Names}}' | grep -Fx "${REDIS_CONTAINER_NAME}" >/dev/null 2>&1; then
    printf '%s\n' "${REDIS_CONTAINER_NAME}"
    return 0
  fi

  docker ps --format '{{.Names}} {{.Ports}}' \
    | awk -v port="${REDIS_PORT}" '$0 ~ port "->6379/tcp" {print $1; exit}'
}

REDIS_CONTAINER="$(find_redis_container || true)"

echo "== NODEA HOST =="
hostname -I

echo "== NODEA REDIS DOCKER =="
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}' 2>/dev/null | grep -E '(^NAMES|redis)' || true

echo "== NODEA REDIS PROCESS =="
ps -ef | grep redis-server | grep -v grep || true

echo "== NODEA REDIS PORT =="
ss -ltnp | grep "${REDIS_PORT}" || true

echo "== NODEA REDIS SERVICE =="
systemctl status redis-server --no-pager || true
systemctl status redis --no-pager || true

echo "== NODEA REDIS CONTAINER PICK =="
if [[ -n "${REDIS_CONTAINER}" ]]; then
  printf 'container=%s\n' "${REDIS_CONTAINER}"
else
  echo "container=not-found"
fi

echo "== NODEA REDIS CONTAINER INSPECT =="
if [[ -n "${REDIS_CONTAINER}" ]]; then
  docker inspect "${REDIS_CONTAINER}" \
    --format 'name={{.Name}} image={{.Config.Image}} restart={{.HostConfig.RestartPolicy.Name}}' || true
  docker inspect "${REDIS_CONTAINER}" \
    --format 'binds={{range .Mounts}}{{.Source}}:{{.Destination}} {{end}}' || true
fi

echo "== NODEA REDIS CONF FILES =="
find /etc /usr/local/etc -maxdepth 3 -type f 2>/dev/null | grep redis | sort || true

echo "== NODEA REDIS KEY CONFIG =="
grep -nE '^(bind|port|requirepass|masterauth|replicaof|slaveof|appendonly|dir|dbfilename|protected-mode)' \
  /etc/redis/redis.conf /usr/local/etc/redis.conf 2>/dev/null || true

echo "== NODEA REDIS INFO =="
redis-cli -p "${REDIS_PORT}" INFO replication 2>&1 || true
redis-cli -p "${REDIS_PORT}" INFO persistence 2>&1 || true
redis-cli -p "${REDIS_PORT}" CONFIG GET dir 2>&1 || true
redis-cli -p "${REDIS_PORT}" CONFIG GET dbfilename 2>&1 || true
redis-cli -p "${REDIS_PORT}" CONFIG GET appendonly 2>&1 || true

echo "== NODEA REDIS DOCKER INFO =="
if [[ -n "${REDIS_CONTAINER}" ]]; then
  docker exec "${REDIS_CONTAINER}" redis-cli INFO replication 2>&1 || true
  docker exec "${REDIS_CONTAINER}" redis-cli INFO persistence 2>&1 || true
  docker exec "${REDIS_CONTAINER}" redis-cli CONFIG GET dir 2>&1 || true
  docker exec "${REDIS_CONTAINER}" redis-cli CONFIG GET dbfilename 2>&1 || true
  docker exec "${REDIS_CONTAINER}" redis-cli CONFIG GET appendonly 2>&1 || true
fi
