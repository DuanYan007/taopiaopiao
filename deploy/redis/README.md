# Deploy

## 1. 启动 Redis 容器

```bash
docker pull redis:8.6.1

mkdir -p "$HOME/data/redis/conf" "$HOME/data/redis/data"
cp scripts/loadtest/redis-loadtest.conf.example "$HOME/data/redis/conf/redis.conf"

docker run -d \
  --name redis \
  --restart unless-stopped \
  -p 6349:6379 \
  --memory 6g \
  --memory-swap 6g \
  --cpus 2 \
  --ulimit nofile=65535:65535 \
  -v "$HOME/data/redis/data":/data \
  -v "$HOME/data/redis/conf/redis.conf":/etc/redis/redis.conf \
  redis:8.6.1 \
  redis-server /etc/redis/redis.conf
```
