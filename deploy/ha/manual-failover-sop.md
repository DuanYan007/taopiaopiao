# Manual Failover SOP

This SOP is for the current first-phase two-node standby model:

- Node A: `192.168.3.36`
- Node B: `192.168.3.41`
- core path only: `payment-system`, `gateway`, `session-service`, `seckill-service`, `order-service`

This SOP assumes:

- OpenResty is already deployed on both nodes
- Node B already has the current repository checkout and env file
- Nacos is still served by Node A at `192.168.3.36:8848`
- this is a manual failover drill, not VIP automation

## 1. Purpose

Use this SOP when you need to:

1. verify Node B standby readiness
2. stop Node A core stateless services
3. confirm Node B has taken over local traffic
4. later recover Node A

## 2. Pre-check on Node B

Run on `192.168.3.41`:

```bash
cd /home/duan/projects/taopiaopiao

printf '\n== NODEB HOST ==\n'
hostname -I

printf '\n== NODEB START ==\n'
bash bin/start-payment-system.sh
bash bin/start-core-services.sh

sleep 10

printf '\n== NODEB STATUS ==\n'
bash bin/status-services.sh

printf '\n== NODEB LISTEN ==\n'
ss -ltnp | grep -E '7500|8080|8084|8086|8087' || true

printf '\n== NODEB TRAFFIC ==\n'
curl -I http://127.0.0.1/admin/
curl -I http://127.0.0.1/client/
curl -s http://127.0.0.1/api/client/sessions
printf '\n'
curl -s http://127.0.0.1/payment/query?orderNo=TEST_ORDER
printf '\n'

printf '\n== NACOS NOW ==\n'
for s in payment-system gateway session-service seckill-service order-service; do
  printf '\n--- %s ---\n' "$s"
  curl -s "http://192.168.3.36:8848/nacos/v1/ns/instance/list?serviceName=${s}&groupName=DEFAULT_GROUP"
  printf '\n'
done
```

Expected result:

- Node B local ports are listening on `7500/8080/8084/8086/8087`
- local OpenResty static pages return `200`
- local API and payment query return JSON, not `502`
- Nacos shows both Node A and Node B instances before failover

## 3. Stop Node A Core Services

Run on `192.168.3.36`:

```bash
cd /home/duan/projects/taopiaopiao

printf '\n== NODEA HOST ==\n'
hostname -I

printf '\n== NODEA STATUS BEFORE STOP ==\n'
bash bin/status-services.sh

printf '\n== NODEA STOP ==\n'
bash bin/stop-all-services.sh

sleep 8

printf '\n== NODEA STATUS AFTER STOP ==\n'
bash bin/status-services.sh

printf '\n== NODEA LISTEN AFTER STOP ==\n'
ss -ltnp | grep -E '7500|8080|8084|8086|8087' || true
```

Important note:

- `bin/stop-all-services.sh` now has a port-based fallback
- if a service was not started by the repo scripts, `status-services.sh` should report `running, pid=... (not started by script)` before stop
- if any of the five core ports still listen after the stop step, stop those remaining processes by PID before continuing

## 4. Verify Node B Takeover

Return to `192.168.3.41` and run:

```bash
cd /home/duan/projects/taopiaopiao

printf '\n== NODEB STATUS AFTER NODEA STOP ==\n'
bash bin/status-services.sh

printf '\n== NACOS FROM NODEB AFTER NODEA STOP ==\n'
for s in payment-system gateway session-service seckill-service order-service; do
  printf '\n--- %s ---\n' "$s"
  curl -s "http://192.168.3.36:8848/nacos/v1/ns/instance/list?serviceName=${s}&groupName=DEFAULT_GROUP"
  printf '\n'
done

printf '\n== NODEB TRAFFIC AFTER NODEA STOP ==\n'
curl -I http://127.0.0.1/admin/
curl -I http://127.0.0.1/client/
curl -s http://127.0.0.1/api/client/sessions
printf '\n'
curl -s http://127.0.0.1/payment/query?orderNo=TEST_ORDER
printf '\n'
```

Pass criteria:

1. Nacos retains only Node B instances for the five core services
2. Node B `status-services.sh` reports the five core services as running
3. local OpenResty static pages still return `200`
4. local API and payment query still return valid JSON

## 5. Recover Node A

After the drill, recover Node A on `192.168.3.36`:

```bash
cd /home/duan/projects/taopiaopiao

printf '\n== NODEA RECOVER ==\n'
bash bin/start-payment-system.sh
bash bin/start-core-services.sh

sleep 10

printf '\n== NODEA STATUS AFTER RECOVER ==\n'
bash bin/status-services.sh

printf '\n== NODEA LISTEN AFTER RECOVER ==\n'
ss -ltnp | grep -E '7500|8080|8084|8086|8087' || true
```

Then re-check Nacos from either node:

```bash
for s in payment-system gateway session-service seckill-service order-service; do
  printf '\n--- %s ---\n' "$s"
  curl -s "http://192.168.3.36:8848/nacos/v1/ns/instance/list?serviceName=${s}&groupName=DEFAULT_GROUP"
  printf '\n'
done
```

Expected result:

- Nacos returns to dual-instance state for the five core services

## 6. Common Pitfalls

### 6.1 Running the stop step on the wrong node

Always run `hostname -I` first.

- Node A should show `192.168.3.36`
- Node B should show `192.168.3.41`

Do not run the Node A stop block on Node B.

### 6.2 SSH to Node A is unavailable

If `ssh duan@192.168.3.36` is refused, use a local shell on Node A instead of trying to automate from Node B.

### 6.3 Old unmanaged Java processes on Node A

If `status-services.sh` shows `running, pid=... (not started by script)`, this means the process exists but was not launched through the repo `.run/*.pid` workflow.

The current `stop-all-services.sh` should still stop it by port, but operators should treat this as a process-ownership cleanup item.

## 7. When to Use This SOP

Use this SOP again when:

- validating a fresh Node B deployment
- re-checking failover after OpenResty or Nacos config changes
- confirming that process-management cleanup on Node A did not break takeover
