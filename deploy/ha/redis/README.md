# Redis HA Preparation

当前阶段，Redis 还没有切成主从。

仓库先提供两个“现状采集脚本”，用于在不手工拼多段命令的情况下，直接确认两台机器的 Redis 现实状态。

当前已知背景：

- Node A：`192.168.3.36`
- Node B：`192.168.3.41`
- 当前业务默认 Redis：`192.168.3.36:6349`
- 下一阶段目标：`NodeA master + NodeB replica`

## 1. 脚本入口

- `deploy/ha/redis/inspect-node-a.sh`
- `deploy/ha/redis/inspect-node-b.sh`

## 2. 使用方式

Node A：

```bash
bash deploy/ha/redis/inspect-node-a.sh
```

Node B：

```bash
bash deploy/ha/redis/inspect-node-b.sh
```

如需覆盖端口，可在单行命令里直接带变量：

```bash
REDIS_PORT=6349 bash deploy/ha/redis/inspect-node-a.sh
REDIS_PORT=6349 bash deploy/ha/redis/inspect-node-b.sh
```

## 3. 采集内容

脚本会输出：

1. 当前主机 IP
2. Redis 进程
3. Redis 监听端口
4. systemd 服务状态
5. 常见 Redis 配置文件
6. 关键配置项
7. `INFO replication`
8. `INFO persistence`
9. 当前数据目录 / RDB / AOF 配置

## 4. 当前建议

在进入 Redis 主从改造前，先收集两台机器完整输出，再决定：

- 现有 Redis 是包安装还是手工安装
- 当前是否已经开启 AOF
- 当前数据目录是否适合直接做副本
- Node B 是否已有残留 Redis 进程或旧配置

## 5. 输出方式

优先直接执行脚本并粘贴完整输出。

不要手工拆脚本里的命令逐条执行，否则容易遗漏当前真实配置。
