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
