#!/usr/bin/env bash

set -euo pipefail

VIP_IP="${VIP_IP:-192.168.3.50}"
INTERFACE="${INTERFACE:-enp3s0}"
WAIT_SECONDS="${WAIT_SECONDS:-12}"

echo "== NODEB BEFORE =="
hostname -I
sudo systemctl status keepalived --no-pager || true
ip addr show dev "${INTERFACE}" | grep -n '192.168.3.' || true

echo "== VIP ENTRY BEFORE =="
curl -I "http://${VIP_IP}/admin/"
curl -I "http://${VIP_IP}/client/"
curl -s "http://${VIP_IP}/api/client/sessions"
echo
curl -s "http://${VIP_IP}/payment/query?orderNo=VIPCHECK"
echo

echo "== WAIT FOR FAILOVER ${WAIT_SECONDS}s =="
sleep "${WAIT_SECONDS}"

echo "== NODEB AFTER FAILOVER =="
sudo systemctl status keepalived --no-pager || true
sudo journalctl -u keepalived -n 20 --no-pager || true
ip addr show dev "${INTERFACE}" | grep -n '192.168.3.' || true

echo "== VIP ENTRY AFTER FAILOVER =="
curl -I "http://${VIP_IP}/admin/"
curl -I "http://${VIP_IP}/client/"
curl -s "http://${VIP_IP}/api/client/sessions"
echo
curl -s "http://${VIP_IP}/payment/query?orderNo=VIPCHECK"
echo
