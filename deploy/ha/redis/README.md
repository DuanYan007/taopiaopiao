# Redis HA Preparation

当前阶段，Redis 还没有切成主从。

仓库先提供两个“现状采集脚本”，用于在不手工拼多段命令的情况下，直接确认两台机器的 Redis 现实状态。

当前已知背景：

- Node A：`192.168.3.36`
- Node B：`192.168.3.41`
- 当前业务默认 Redis：`192.168.3.36:6349`
- 当前 Redis 现实形态：`NodeA` 上 Docker 容器单机节点
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
2. Redis 容器列表
3. Redis 进程
4. Redis 监听端口
5. systemd 服务状态
6. 容器选中结果与挂载信息
7. 常见 Redis 配置文件
8. 关键配置项
9. 宿主机侧 `redis-cli` 结果
10. 容器内 `redis-cli` 结果
11. 当前数据目录 / RDB / AOF 配置

## 4. 当前建议

在进入 Redis 主从改造前，先收集两台机器完整输出，再决定：

- 现有 Redis 是 Docker 容器还是宿主机服务
- 当前是否已经开启 AOF
- 当前数据目录是否适合直接做副本
- Node B 是否已有残留 Redis 进程或旧配置

当前优先判断点：

- NodeA 容器名是否固定为 `redis`
- NodeA 真实挂载目录是什么
- NodeA 当前 Redis 配置文件是否来自 `scripts/loadtest/redis-loadtest.conf.example`
- NodeB 当前是否完全没有 Redis，还是已经存在残留容器 / 残留端口

## 5. 输出方式

优先直接执行脚本并粘贴完整输出。

不要手工拆脚本里的命令逐条执行，否则容易遗漏当前真实配置。
