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

## 3. 与 keepalived 配合

如果下一步要把浏览器入口切成 VIP，请同时参考：

- `deploy/ha/keepalived/README.md`
- `deploy/ha/keepalived/check-openresty.sh`
- `deploy/ha/keepalived/check-tpp-entry.sh`

当前 OpenResty 仍然保持本机回环转发：

- `127.0.0.1:8080` -> gateway
- `127.0.0.1:7500` -> payment-system

这意味着 keepalived 的健康检查不应该只看 80 端口存活，还要检查：

1. OpenResty 配置和进程本身
2. 通过 `127.0.0.1` 访问 `/admin/`、`/client/`、`/api/client/sessions`、`/payment/query`
