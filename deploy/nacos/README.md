# Deploy

## 1. 启动 Nacos 容器

```bash
docker pull nacos/nacos-server:v2.3.2

mkdir -p "$HOME/data/nacos/data" "$HOME/data/nacos/logs"

docker run -d \
  --name nacos \
  --restart unless-stopped \
  -p 8848:8848 \
  -p 9848:9848 \
  --memory 2g \
  --memory-swap 2g \
  --cpus 1 \
  -e MODE=standalone \
  -e PREFER_HOST_MODE=ip \
  -e TIME_ZONE=Asia/Shanghai \
  -e NACOS_DEBUG=n \
  -e NACOS_USER=nacos \
  -e NACOS_AUTH_IDENTITY_KEY=nacos_auth_key \
  -e NACOS_AUTH_IDENTITY_VALUE=nacos_auth_value \
  -e NACOS_AUTH_TOKEN=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE \
  -e FUNCTION_MODE=all \
  -e TOMCAT_ACCESSLOG_ENABLED=false \
  -e JVM_XMS=1g \
  -e JVM_XMX=1g \
  -e JVM_XMN=512m \
  -e JVM_MS=128m \
  -e JVM_MMS=320m \
  -v "$HOME/data/nacos/data":/home/nacos/data \
  -v "$HOME/data/nacos/logs":/home/nacos/logs \
  nacos/nacos-server:v2.3.2
```

## 2. 配置中心

当前仓库已经让多个服务从 Nacos Config 读取配置覆盖本地默认值。

默认约定：

- `dataId`: `backend-common.yaml`
- `dataId`: `<service-name>.yaml`
- `group`: `DEFAULT_GROUP`
- `namespace`: 使用环境变量 `NACOS_CONFIG_NAMESPACE`，为空时走默认命名空间

加载顺序：

1. `backend-common.yaml`
2. `<service-name>.yaml`

如果有同名配置，以服务自己的 `dataId` 为准。

当前已同步到 `deploy/nacos/` 的实际配置文件：

- `deploy/nacos/backend-common.yaml`
- `deploy/nacos/order-service.yaml`
- `deploy/nacos/seckill-service.yaml`
- `deploy/nacos/session-service.yaml`
- `deploy/nacos/user-service.yaml`
- `deploy/nacos/event-service.yaml`
- `deploy/nacos/gateway.yaml`

建议本地仅保留以下环境变量用于连接 Nacos：

```bash
export NACOS_SERVER_ADDR=192.168.3.36:8848
export NACOS_USERNAME=nacos
export NACOS_PASSWORD=your-nacos-password
export NACOS_CONFIG_GROUP=DEFAULT_GROUP
export NACOS_CONFIG_NAMESPACE=
```

当前拆分规则：

- `backend-common.yaml`：MySQL、Redis、RocketMQ nameserver、基础日志级别、Knife4j
- `<service-name>.yaml`：服务专属项，例如 MQ group、网关路由、上传限制、逻辑删除配置
