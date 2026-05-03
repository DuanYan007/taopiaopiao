#!/usr/bin/env bash

set -euo pipefail

REDIS_PORT="${REDIS_PORT:-6349}"

echo "== NODEA HOST =="
hostname -I

echo "== NODEA REDIS PROCESS =="
ps -ef | grep redis-server | grep -v grep || true

echo "== NODEA REDIS PORT =="
ss -ltnp | grep "${REDIS_PORT}" || true

echo "== NODEA REDIS SERVICE =="
systemctl status redis-server --no-pager || true
systemctl status redis --no-pager || true

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
