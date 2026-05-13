# Deploy

## 1. 安装 OpenResty

安装与当前环境一致的版本：

```bash
openresty/1.29.2.1
```

下载来源不要求与当前机器一致，但运行目录需要保持为：

```bash
/usr/local/openresty/nginx
```

## 2. 同步项目内文件

- `deploy/openresty/nginx.conf` 对应 `/usr/local/openresty/nginx/conf/nginx.conf`
- `deploy/openresty/app.conf` 对应 `/usr/local/openresty/nginx/conf/app.conf`
- `deploy/openresty/lua/` 对应 `/usr/local/openresty/nginx/lua/`
- `html/` 对应 `/usr/local/openresty/nginx/html/`

## 3. 当前本机转发基线

当前 OpenResty 保持本机回环转发：

- `127.0.0.1:8080` -> gateway
- `127.0.0.1:7500` -> payment-system

联调时重点检查：

1. OpenResty 配置和进程本身
2. 通过 `127.0.0.1` 访问 `/admin/`、`/client/`、`/api/client/sessions`、`/payment/query`
