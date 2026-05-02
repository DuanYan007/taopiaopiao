#!/usr/bin/env bash

set -euo pipefail

BIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${BIN_DIR}/../../.." && pwd)"

VIP_IP="${VIP_IP:-192.168.3.50}"
VIP_PREFIX="${VIP_PREFIX:-24}"
VRRP_PASS="${VRRP_PASS:-tppvrrp}"
INTERFACE="${INTERFACE:-enp3s0}"
CONF_TEMPLATE="${BIN_DIR}/keepalived-node-b.conf.example"
TMP_CONF="/tmp/keepalived-node-b.conf"

cd "${ROOT_DIR}"

echo "== HOST =="
hostname -I

echo "== TEMPLATE =="
ls -l "${CONF_TEMPLATE}"

echo "== GIT PULL =="
git pull --ff-only origin master

echo "== LOCAL ENTRY =="
curl -I http://127.0.0.1/admin/
curl -I http://127.0.0.1/client/
curl -s http://127.0.0.1/api/client/sessions
echo
curl -s http://127.0.0.1/payment/query?orderNo=VIPCHECK
echo

sed \
    -e "s/__INTERFACE__/${INTERFACE}/" \
    -e "s/__VRRP_PASSWORD__/${VRRP_PASS}/" \
    -e "s/__VIP_IP__/${VIP_IP}/" \
    -e "s/__VIP_PREFIX__/${VIP_PREFIX}/" \
    "${CONF_TEMPLATE}" > "${TMP_CONF}"

echo "== RENDERED CONF =="
wc -c "${TMP_CONF}"
sed -n '1,120p' "${TMP_CONF}"

sudo install -d -m 755 /etc/keepalived
sudo install -m 755 "${BIN_DIR}/check-openresty.sh" /etc/keepalived/check-openresty.sh
sudo install -m 755 "${BIN_DIR}/check-tpp-entry.sh" /etc/keepalived/check-tpp-entry.sh
sudo install -m 644 "${TMP_CONF}" /etc/keepalived/keepalived.conf

sudo apt update
sudo apt install -y keepalived
sudo systemctl enable keepalived
sudo systemctl restart keepalived
sleep 5

echo "== KEEPALIVED STATUS =="
sudo systemctl status keepalived --no-pager || true

echo "== KEEPALIVED LOG =="
sudo journalctl -u keepalived -n 30 --no-pager || true

echo "== VIP ON ${INTERFACE} =="
ip addr show dev "${INTERFACE}" | grep -n '192.168.3.' || true

echo "== VIP ENTRY =="
curl -I "http://${VIP_IP}/admin/" || true
curl -I "http://${VIP_IP}/client/" || true
curl -s "http://${VIP_IP}/api/client/sessions" || true
echo
curl -s "http://${VIP_IP}/payment/query?orderNo=VIPCHECK" || true
echo
