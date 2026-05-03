#!/usr/bin/env bash

set -euo pipefail

VIP_IP="${VIP_IP:-192.168.3.50}"
INTERFACE="${INTERFACE:-enp3s0}"
WAIT_SECONDS="${WAIT_SECONDS:-12}"

echo "== NODEB BEFORE FAILBACK =="
hostname -I
sudo systemctl status keepalived --no-pager || true
ip addr show dev "${INTERFACE}" | grep -n '192.168.3.' || true

echo "== VIP ENTRY BEFORE FAILBACK =="
curl -I "http://${VIP_IP}/admin/"
curl -I "http://${VIP_IP}/client/"
curl -s "http://${VIP_IP}/api/client/sessions"
echo
curl -s "http://${VIP_IP}/payment/query?orderNo=VIPCHECK"
echo

echo "== STOP NODEB KEEPALIVED FOR MANUAL FAILBACK =="
sudo systemctl stop keepalived

sleep "${WAIT_SECONDS}"

echo "== NODEB AFTER FAILBACK =="
sudo systemctl status keepalived --no-pager || true
ip addr show dev "${INTERFACE}" | grep -n '192.168.3.' || true

echo "== VIP ENTRY AFTER FAILBACK =="
curl -I "http://${VIP_IP}/admin/"
curl -I "http://${VIP_IP}/client/"
curl -s "http://${VIP_IP}/api/client/sessions"
echo
curl -s "http://${VIP_IP}/payment/query?orderNo=VIPCHECK"
echo

echo "== OPTIONAL RECOVER NODEB KEEPALIVED =="
echo "Run manually when you want Node B to rejoin as BACKUP:"
echo "sudo systemctl start keepalived"
