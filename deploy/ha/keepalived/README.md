# Keepalived VIP Plan

本目录用于当前两节点阶段的入口高可用准备。

当前目标不是把整套中间件自动切换，而是先让浏览器入口具备稳定的 VIP：

- Node A: `192.168.3.36`
- Node B: `192.168.3.41`
- VIP: 由运维自行选定，同网段、未被占用

适用前提：

- 两台机器都已经部署 OpenResty
- 两台机器都已经能本机提供 `/admin/`、`/client/`、`/api/`、`/payment/` 入口
- 当前仍然是手动切换业务服务，VIP 只负责入口漂移

## 1. 目录说明

- `keepalived-node-a.conf.example`: Node A 模板
- `keepalived-node-b.conf.example`: Node B 模板
- `check-openresty.sh`: 检查本机 OpenResty 进程与配置
- `check-tpp-entry.sh`: 检查本机入口是否还能正常服务
- `setup-node-a.sh`: Node A 一键安装与渲染脚本
- `setup-node-b.sh`: Node B 一键安装与渲染脚本

## 2. 推荐策略

当前阶段建议采用：

- Node A `MASTER`
- Node B `BACKUP`
- `nopreempt`

原因：

1. 先追求稳定，不追求自动抢回
2. 避免 Node A 短暂恢复后又把 VIP 抢回去
3. 更适合当前还在整理服务托管方式的阶段

## 3. 健康检查建议

建议 keepalived 同时检查两件事：

1. OpenResty 进程与本机 80 端口
2. 本机通过 `127.0.0.1` 访问关键入口是否仍然返回正常结果

这比只看端口存活更可靠。

## 4. 首次部署步骤

1. 选定一个未占用的 LAN VIP，例如 `<VIP_IP>/<PREFIX>`
2. 根据模板生成两台机器的 `/etc/keepalived/keepalived.conf`
3. 把检查脚本部署到两台机器的同一路径，例如 `/etc/keepalived/check-openresty.sh` 和 `/etc/keepalived/check-tpp-entry.sh`
4. 赋予执行权限
5. 在两台机器上安装并启动 keepalived
6. 先在 Node A 上确认 VIP 漂到主机
7. 再按 [manual-failover-sop.md](/home/duan/projects/taopiaopiao/deploy/ha/manual-failover-sop.md) 的思路演练业务服务停机，同时观察 VIP 是否漂到 Node B

## 5. 建议的安装与验证命令

两台机器都执行：

```bash
sudo apt update
sudo apt install -y keepalived
sudo install -d -m 755 /etc/keepalived
sudo install -m 755 deploy/ha/keepalived/check-openresty.sh /etc/keepalived/check-openresty.sh
sudo install -m 755 deploy/ha/keepalived/check-tpp-entry.sh /etc/keepalived/check-tpp-entry.sh
```

如果你更倾向于“先把脚本同步过去，再在目标机直接执行”，可以直接使用：

```bash
bash deploy/ha/keepalived/setup-node-a.sh
bash deploy/ha/keepalived/setup-node-b.sh
```

可覆盖变量：

```bash
VIP_IP=192.168.3.50 VIP_PREFIX=24 VRRP_PASS=tppvrrp INTERFACE=enp131s0 bash deploy/ha/keepalived/setup-node-a.sh
VIP_IP=192.168.3.50 VIP_PREFIX=24 VRRP_PASS=tppvrrp INTERFACE=enp3s0  bash deploy/ha/keepalived/setup-node-b.sh
```

把模板改成真实配置后：

```bash
sudo install -m 644 /path/to/real/keepalived.conf /etc/keepalived/keepalived.conf
sudo systemctl enable keepalived
sudo systemctl restart keepalived
sudo systemctl status keepalived --no-pager
ip addr show
```

## 6. 演练重点

入口层 VIP 演练至少观察三件事：

1. `ip addr show` 中 VIP 当前挂在哪台机器
2. 从客户端访问 VIP 时，`/admin/` 与 `/client/` 是否仍然返回 `200`
3. `curl http://<VIP>/api/client/sessions` 与 `curl http://<VIP>/payment/query?orderNo=VIPCHECK` 是否仍然返回 JSON

## 7. 当前不要做的事情

当前阶段不要把 VIP 切换和以下动作绑成一套自动化：

- MySQL 主从切换
- Redis 主从切换
- RocketMQ 主从切换
- Nacos 双节点自动仲裁

现在先把入口漂移做稳，再继续向数据层高可用推进。
