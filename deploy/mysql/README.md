# Deploy

## 1. 启动 MySQL 容器

```bash
docker pull mysql:8.4.8

mkdir -p "$HOME/data/mysql/conf" "$HOME/data/mysql/data"

docker run -d \
  --name mysql \
  --restart unless-stopped \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=7566 \
  -v "$HOME/data/mysql/conf":/etc/mysql/conf.d \
  -v "$HOME/data/mysql/data":/var/lib/mysql \
  mysql:8.4.8
```

## 2. 按顺序执行脚本

```bash
docker exec -i mysql mysql -uroot -p7566 < deploy/mysql/ddl.sql
docker exec -i mysql mysql -uroot -p7566 < deploy/mysql/dml.sql
```
