#!/usr/bin/env bash

set -euo pipefail

VIP_IP="${VIP_IP:-192.168.3.50}"
INTERFACE="${INTERFACE:-enp131s0}"
WAIT_SECONDS="${WAIT_SECONDS:-12}"

echo "== NODEA BEFORE =="
hostname -I
sudo systemctl status keepalived --no-pager || true
ip addr show dev "${INTERFACE}" | grep -n '192.168.3.' || true

echo "== STOP NODEA KEEPALIVED =="
sudo systemctl stop keepalived

sleep "${WAIT_SECONDS}"

echo "== NODEA AFTER STOP =="
sudo systemctl status keepalived --no-pager || true
ip addr show dev "${INTERFACE}" | grep -n '192.168.3.' || true

echo "== RECOVER NODEA KEEPALIVED =="
sudo systemctl start keepalived

sleep "${WAIT_SECONDS}"

echo "== NODEA AFTER RECOVER =="
sudo systemctl status keepalived --no-pager || true
sudo journalctl -u keepalived -n 20 --no-pager || true
ip addr show dev "${INTERFACE}" | grep -n '192.168.3.' || true

echo "== VIP ENTRY FROM NODEA AFTER RECOVER =="
curl -I "http://${VIP_IP}/admin/"
curl -I "http://${VIP_IP}/client/"
curl -s "http://${VIP_IP}/api/client/sessions"
echo
curl -s "http://${VIP_IP}/payment/query?orderNo=VIPCHECK"
echo
