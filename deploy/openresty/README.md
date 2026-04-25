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
