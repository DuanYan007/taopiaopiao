# Two-Node HA Plan

## 1. Scope

This document is split into two parts:

1. the current single-node deployment reality
2. the future dual-node HA target

Current machine reality:

- Node A is active on `192.168.3.36`
- Node B standby is deployed on `192.168.3.41`
- `192.168.3.39` is not the primary runtime registration IP for Node B

Future target nodes:

- Node A: primary host, `32GB` RAM
- Node B: secondary host, `16GB` RAM

The goal is not "full automatic multi-master HA". With only two machines, the practical target is:

- high recoverability
- hot standby for core services
- fast manual or semi-automatic failover
- no new hardcoded single-host assumptions

For components that require quorum or a third vote, this document explicitly distinguishes:

- what is safe with two nodes
- what should wait for a third lightweight node

## 2. Current Single-Node State

The repository has already completed the first round of de-localhost refactoring. The current limitation is deployment completeness, not code binding.

### 2.1 Current deployed nodes

- active host: `192.168.3.36`
- standby host: `192.168.3.41`
- active Nacos: `192.168.3.36:8848`
- active RocketMQ nameserver: `192.168.3.36:9876`
- both nodes can run local OpenResty plus local core stateless services
- verified manual failover for the core stateless path: after stopping Node A core services, Nacos retained only Node B instances and Node B local traffic remained available
- verified VIP entry failover on `192.168.3.50`: Node B can automatically take over the VIP when Node A keepalived stops, and the repo keepalived templates now support manual failback instead of automatic VIP grab-back on Node A recovery

### 2.2 Current service configuration state

- service configs load `backend-common.yaml + <service>.yaml` from Nacos
- forced `127.0.0.1` registration overrides have been removed from active service configs
- local `application.yml` files are now reduced to bootstrap and service-local items

### 2.3 Current payment path state

- payment access has been externalized
- browser-facing pay URL is now a relative path under `/payment/`
- current single-node deployment proxies `/payment/` to local `127.0.0.1:7500`

### 2.4 Current OpenResty topology

- OpenResty proxies to local gateway `127.0.0.1:8080`
- OpenResty proxies to local payment `127.0.0.1:7500`
- this is correct for current single-node deployment
- this is also acceptable later if each node runs its own local gateway and payment-system

### 2.5 Current conclusion

- current repo and runtime are ready for single-node integrated operation
- current repo is structurally ready for dual-node stateless standby
- Node B standby deployment and manual failover drill have been validated for `payment-system`, `gateway`, `session-service`, `seckill-service`, and `order-service`
- keepalived-based VIP entry failover has been deployed and validated on both nodes
- one operational gap remains on Node A: some historical service processes were not started by the repo `bin` scripts, so `bin/stop-all-services.sh` cannot stop them unless they are re-managed or stopped by port/PID
- repeatable operator commands for this drill are documented in `deploy/ha/manual-failover-sop.md`

## 3. Future Dual-Node HA Target

### 3.1 Core path to protect first

Protect only the core transaction path first:

`OpenResty -> gateway -> seckill-service -> order-service -> session-service -> Redis / MySQL / RocketMQ / payment-system`

Lower priority services:

- `user-service`
- `venue-service`
- `event-service`
- `seat-template-service`

These can remain secondary in the first HA milestone.

### 3.2 Realistic two-node target

Recommended target:

- active-passive at infrastructure level
- active-active deployment for stateless services where cheap
- one write master for MySQL and Redis
- hot standby for failover
- one external entry VIP or equivalent switch point

Avoid:

- MySQL dual-primary
- Redis dual-primary
- "automatic" two-node arbitration without a third vote

## 4. Future Two-Node Topology

### 4.1 Node roles

### Node A: primary production traffic, 32GB

Primary responsibilities:

- OpenResty
- gateway
- seckill-service
- order-service
- session-service
- payment-system
- Redis master
- MySQL primary
- RocketMQ nameserver
- RocketMQ broker master
- Nacos node 1
- optional support services

### Node B: standby and failover target, 16GB

Secondary responsibilities:

- OpenResty
- gateway
- seckill-service
- order-service
- session-service
- payment-system
- Redis replica
- MySQL replica
- RocketMQ nameserver
- RocketMQ broker slave
- Nacos node 2
- optional support services on reduced concurrency expectations

### 4.2 External entry strategy

Use one stable entrypoint for LAN clients:

- preferred: `keepalived + VIP`
- acceptable fallback: DNS/manual host switch

Recommended:

- VIP points to Node A normally
- VIP fails over to Node B
- both nodes run OpenResty

This solves the client-facing problem at the right layer. Browser clients should always reach the same logical entrypoint, regardless of which host currently owns traffic.

## 5. Resource Planning

These are starting points, not final limits.

### 5.1 Node A, 32GB

Suggested rough allocation:

- MySQL: `8-10GB`
- Redis: `6-8GB`
- RocketMQ broker + nameserver: `3-4GB`
- Nacos: `1.5-2GB`
- Java services total: `8-10GB`
- OpenResty + OS cache + headroom: `4-6GB`

Node A should keep enough headroom for peak JVM spikes and Redis memory fragmentation.

### 5.2 Node B, 16GB

Suggested rough allocation:

- MySQL replica: `4-5GB`
- Redis replica: `3-4GB`
- RocketMQ slave + nameserver: `2-3GB`
- Nacos: `1-1.5GB`
- Java services total: `4-5GB`
- OpenResty + OS headroom: `1.5-2GB`

Node B should not be treated as equal steady-state capacity. It is a survivability node first.

## 6. Future Component-by-Component HA Design

### 6.1 OpenResty

### Recommended

- deploy OpenResty on both nodes
- maintain identical `nginx.conf`, `app.conf`, and `lua/`
- place a VIP in front of both
- keep static assets synchronized

### Two-node operating model

- normal: VIP -> Node A OpenResty
- failover: VIP -> Node B OpenResty

### Important note

The current repo config proxies through node-local loopback to:

- gateway `127.0.0.1:8080`
- payment `127.0.0.1:7500`

This is acceptable if each node runs its own local gateway and payment-system instance. It keeps OpenResty simple and avoids cross-node proxy chains.

### 6.2 Gateway and stateless services

### Recommended

Run these on both nodes:

- gateway
- seckill-service
- order-service
- session-service
- payment-system

Optional in phase 1:

- user-service
- venue-service
- event-service
- seat-template-service

### Why

These are stateless or mostly stateless from an HA perspective. Their real state is externalized into:

- MySQL
- Redis
- RocketMQ
- Nacos

Running them on both nodes is comparatively cheap and gives the best failover benefit per effort.

### 6.3 MySQL

### Recommended with two nodes

- Node A: primary
- Node B: replica
- row-based replication
- failover initially manual or scripted

### Not recommended yet

- dual-primary writes
- forced automatic promotion without fencing

### Future upgrade

If a third node is added, re-evaluate:

- MySQL MGR
- Orchestrator
- external consensus-assisted failover

### 6.4 Redis

### Recommended with two nodes

- Node A: master
- Node B: replica
- AOF enabled
- periodic backup export

### Do not over-trust two-node Sentinel

Redis Sentinel with only two real data nodes is weak for automatic failover because split-brain and quorum edge cases remain awkward. If used, a third sentinel should be added on a third lightweight node.

### Future upgrade

- third sentinel node
- or Redis Cluster if later justified by scale and key model

### 6.5 Nacos

### Recommended now

- run Nacos on both nodes
- configure all services with both node addresses after Node B is actually deployed
- until then, keep active runtime config on `192.168.3.36` only

### Better target

Three-node Nacos cluster when a third small node is available.

### Risk

Two-node cluster survivability is better than one node, but automatic correctness under network partition is still weaker than a proper odd-sized cluster.

### 6.6 RocketMQ

### Recommended now

- one nameserver on each node after Node B is actually deployed
- broker master on Node A
- broker slave on Node B

Services should be configured with both nameserver addresses only after standby deployment is real, not before.

### Important

RocketMQ HA here is primarily:

- broker replication
- quicker recovery
- lower single-host blast radius

It is not a reason to skip operational procedures for failover.

### 6.7 Payment system

### Recommended now

- deploy the in-memory payment-system on both nodes
- keep access through OpenResty `/payment/`
- never return node-local browser URLs

The current repo has already been adjusted to return relative payment paths instead of node-local browser URLs.

## 7. Migration Status And Remaining Work

### 7.1 Already done in current repo

- service configs load `backend-common.yaml + <service>.yaml` from Nacos
- forced `127.0.0.1` registration overrides have been removed from active service configs
- payment access has been externalized
- browser-facing pay URL is now a relative path under `/payment/`
- `PaymentClient` no longer depends on a hardcoded node-local URL

### 7.2 Remaining work

- decide whether `payment-system` should be registered into Nacos
- keep OpenResty config sync and static asset sync in the operating baseline
- move from stateless-plus-entry HA into data-layer HA: Redis, MySQL, RocketMQ, and later Nacos clustering

## 8. Future Rollout Sequence

### Phase 1: dual-node stateless deployment

1. deploy OpenResty on both nodes
2. deploy gateway/seckill/order/session/payment-system on both nodes
3. verify Node B can serve the whole purchase path manually

Deliverable:

- business services can fail over at application level

### Phase 2: data-layer standby

1. MySQL primary/replica
2. Redis master/replica
3. RocketMQ master/slave
4. Nacos dual-node cluster or dual-node transitional mode

Deliverable:

- infrastructure no longer depends on one machine only

### Phase 3: entry failover

1. deploy VIP with keepalived
2. bind VIP to Node A normally
3. switch VIP to Node B in failover drill

Deliverable:

- client entrypoint remains stable during host failure

### Phase 4: third node for quorum

Add a small third node for:

- Nacos third node
- Redis Sentinel third vote
- possible MySQL quorum component

Deliverable:

- safer automatic failover decisions

## 9. Immediate Next Tasks

1. promote Redis to primary/replica
2. promote MySQL to primary/replica
3. design RocketMQ master/slave rollout
4. evaluate Nacos dual-node transitional mode or a third lightweight node
5. keep rollback and manual failback procedures aligned with the deployed VIP model

Current repo assets for the next step:

- `deploy/ha/manual-failover-sop.md`
- `deploy/ha/keepalived/README.md`
- `deploy/ha/keepalived/keepalived-node-a.conf.example`
- `deploy/ha/keepalived/keepalived-node-b.conf.example`
- `deploy/ha/keepalived/check-openresty.sh`
- `deploy/ha/keepalived/check-tpp-entry.sh`
- `deploy/ha/keepalived/setup-node-a.sh`
- `deploy/ha/keepalived/setup-node-b.sh`
- `deploy/ha/keepalived/drill-observe-node-b.sh`
- `deploy/ha/keepalived/drill-trigger-node-a.sh`
- `deploy/ha/keepalived/manual-failback-node-b.sh`

## 10. Suggested Machine-Local Variables

Use machine-local, untracked variables for deployment:

- `NACOS_SERVER_ADDR`
- `NACOS_USERNAME`
- `NACOS_PASSWORD`
- `NACOS_CONFIG_GROUP`
- `NACOS_CONFIG_NAMESPACE`
- `ROCKETMQ_NAMESRV_ADDR`
- `OPENRESTY_ROLE`
- `NODE_ROLE`

This keeps the repo portable while allowing each machine to be assigned its own role.

Example files added in this repo:

- `conf/ha-node-a.env.example`
- `conf/ha-node-b.env.example`

Recommended interpretation:

- Node A is the only active node at the moment
- Node B is not yet deployed and should not be referenced in active runtime config
- both nodes should eventually use the same Nacos and RocketMQ address sets after standby deployment is completed
