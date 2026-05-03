# NodeB Bootstrap Guide

本指南用于在一台全新的 Ubuntu 主机上部署 `NodeB`。

当前原则：

- `NodeB` 当前按纯命令行主机处理，不依赖桌面环境
- `NodeB` 先按宿主机部署
- 当前核心服务先使用仓库 `bin` 脚本托管，后续再统一收敛到 `systemd`
- 暂不把 `NodeB` 做成独立中间件节点
- 当前只接入 `NodeA (192.168.3.36)` 的 Nacos / RocketMQ / MySQL / Redis
- 当前 `NodeB` 主运行 IP 为 `192.168.3.41`
- 当前不要把 `192.168.3.39` 当作 `NodeB` 的主服务注册地址写入活跃配置
- 当前 keepalived / VIP 已经纳入双节点基线，VIP 为 `192.168.3.50`

适用范围：

- Ubuntu 22.04 / 24.04
- `amd64`
- 当前主机仅能使用 CLI
- 当前仓库默认路径示例：`/home/duan/projects/taopiaopiao`

---

## 1. 部署目标

`NodeB` 当前基线已经包括：

- OpenResty
- gateway
- seckill-service
- order-service
- session-service
- payment-system
- keepalived standby

当前阶段不做：

- MySQL 副本
- Redis 副本
- RocketMQ slave
- Nacos 第二节点

说明：

- 核心无状态链路与 VIP 漂移已经完成并验证
- 当前下一阶段不是再补 NodeB 基础接入，而是继续推进 Redis / MySQL 数据层高可用

---

## 2. 登录后先确认系统信息

```bash
whoami
hostname -I
cat /etc/os-release
uname -m
timedatectl
```

建议设置时区：

```bash
sudo timedatectl set-timezone Asia/Shanghai
timedatectl
```

---

## 3. 处理软件源与网络

先判断机器是否具备科学上网能力：

### 3.1 有科学上网

可以保留 Ubuntu 官方源，直接安装。

先更新：

```bash
sudo apt update
sudo apt -y upgrade
```

### 3.2 没有科学上网

建议先把 Ubuntu 软件源切到国内镜像，再安装基础软件。

清华镜像站说明：

- https://mirrors.tuna.tsinghua.edu.cn/help/ubuntu/

#### Ubuntu 24.04

24.04 默认使用 `/etc/apt/sources.list.d/ubuntu.sources`。

先备份：

```bash
sudo cp /etc/apt/sources.list.d/ubuntu.sources /etc/apt/sources.list.d/ubuntu.sources.bak
```

写入清华源：

```bash
sudo tee /etc/apt/sources.list.d/ubuntu.sources >/dev/null <<'EOF'
Types: deb
URIs: https://mirrors.tuna.tsinghua.edu.cn/ubuntu/
Suites: noble noble-updates noble-backports
Components: main restricted universe multiverse
Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg

Types: deb
URIs: https://mirrors.tuna.tsinghua.edu.cn/ubuntu/
Suites: noble-security
Components: main restricted universe multiverse
Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg
EOF
```

#### Ubuntu 22.04

22.04 默认使用 `/etc/apt/sources.list`。

先备份：

```bash
sudo cp /etc/apt/sources.list /etc/apt/sources.list.bak
```

写入清华源：

```bash
sudo tee /etc/apt/sources.list >/dev/null <<'EOF'
deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu/ jammy main restricted universe multiverse
deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu/ jammy-updates main restricted universe multiverse
deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu/ jammy-backports main restricted universe multiverse
deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu/ jammy-security main restricted universe multiverse
EOF
```

然后更新：

```bash
sudo apt update
sudo apt -y upgrade
```

### 3.3 需要 VPN 的下载统一处理

当前 `NodeB` 是 CLI-only 主机，且外网能力可能不稳定。对以下内容，不要边装边试，先决定下载路径：

- GitHub 仓库拉取
- OpenResty 官方仓库 / GPG key
- Docker 官方仓库 / GPG key
- 任何不能被国内 apt 镜像直接覆盖的资源

优先顺序：

1. 能直连就直接下载
2. 不能直连但 `NodeA` 可以下载，则先在 `NodeA` 下载，再 `scp` / `rsync` 到 `NodeB`
3. 仍不稳定时，优先使用源码目录同步，不要依赖现场 `git clone`

推荐做法：

```bash
# 在 NodeA 上执行，把仓库直接同步到 NodeB
rsync -avz --delete /home/duan/projects/taopiaopiao/ <nodeb-user>@192.168.3.41:/home/<nodeb-user>/projects/taopiaopiao/
```

如果 OpenResty / Docker 官方仓库拉不通，也优先在 `NodeA` 验证后，再决定是否：

- 导出 `.deb` 包后复制到 `NodeB`
- 或暂时跳过 Docker，只先完成 Java 服务与 OpenResty 部署

---

## 4. 安装基础软件

```bash
sudo apt install -y \
  curl wget git vim unzip zip rsync lsof jq htop tree \
  ca-certificates gnupg lsb-release software-properties-common \
  build-essential net-tools iproute2
```

---

## 5. 安装 Java 17 与 Maven

```bash
sudo apt install -y openjdk-17-jdk maven
java -version
mvn -v
```

如果你后续仍然使用 Maven 拉依赖，建议准备用户级 Maven 配置：

```bash
mkdir -p "$HOME/.m2"
tee "$HOME/.m2/settings.xml" >/dev/null <<'EOF'
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <mirror>
      <id>aliyunmaven</id>
      <mirrorOf>*</mirrorOf>
      <name>Aliyun Maven</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
EOF
```

---

## 6. 安装 OpenResty

OpenResty 官方 Ubuntu 包文档：

- https://openresty.org/en/linux-packages.html

如果官方仓库访问不稳定，优先在 `NodeA` 验证安装流程，再决定是否改为：

- 在 `NodeA` 下载包后传到 `NodeB`
- 或直接把 `NodeA` 已部署好的 OpenResty 配置与静态资源同步到 `NodeB`

### 6.1 安装前处理

如果系统里已有 nginx，先停掉：

```bash
sudo systemctl disable nginx || true
sudo systemctl stop nginx || true
```

### 6.2 添加 OpenResty 官方仓库

```bash
sudo apt-get -y install --no-install-recommends wget gnupg ca-certificates lsb-release
wget -O - https://openresty.org/package/pubkey.gpg | sudo gpg --dearmor -o /usr/share/keyrings/openresty.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/openresty.gpg] http://openresty.org/package/ubuntu $(lsb_release -sc) main" | sudo tee /etc/apt/sources.list.d/openresty.list >/dev/null
sudo apt update
sudo apt install -y openresty
```

验证：

```bash
/usr/local/openresty/nginx/sbin/nginx -v
```

---

## 7. 安装 Docker

当前 `NodeB` 不走全容器化，但保留 Docker 方便后续部署 Nacos 或其它辅助组件。

Docker 官方 Ubuntu 安装文档：

- https://docs.docker.com/engine/install/ubuntu/

如果 Docker 官方仓库访问不稳定，可以先跳过本节。当前第一阶段 NodeB 部署并不依赖 Docker。

### 7.1 安装

```bash
sudo apt update
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

验证：

```bash
sudo systemctl enable docker
sudo systemctl start docker
sudo docker run hello-world
```

可选：把当前用户加入 docker 组。

```bash
sudo usermod -aG docker "$USER"
newgrp docker
docker ps
```

---

## 8. 准备代码目录

建议和 `NodeA` 保持一致：

```bash
mkdir -p "$HOME/projects"
cd "$HOME/projects"
git clone https://github.com/DuanYan007/taopiaopiao taopiaopiao
cd taopiaopiao
```

如果当前机器无法稳定访问 GitHub，建议直接不要在 `NodeB` 上 `git clone`，而是：

1. 在 `NodeA` 打包仓库
2. 用 `scp` / `rsync` 同步到 `NodeB`

示例：

```bash
rsync -avz --delete /home/duan/projects/taopiaopiao/ <nodeb-user>@192.168.3.41:/home/<nodeb-user>/projects/taopiaopiao/
```

---

## 9. 准备运行目录

```bash
cd "$HOME/projects/taopiaopiao"
mkdir -p logs .run
```

---

## 10. 准备 NodeB 环境变量

当前阶段 `NodeB` 只接入 `NodeA` 的中间件：

```bash
cp conf/ha-node-b.env.example "$HOME/taopiaopiao-nodeb.env"
vim "$HOME/taopiaopiao-nodeb.env"
```

当前应确保至少这些值正确：

```bash
NODE_ROLE=standby
NODE_A_IP=192.168.3.36
NODE_B_IP=

NACOS_SERVER_ADDR=192.168.3.36:8848
NACOS_USERNAME=nacos
NACOS_PASSWORD=你的密码
NACOS_CONFIG_GROUP=DEFAULT_GROUP
NACOS_CONFIG_NAMESPACE=

ROCKETMQ_NAMESRV_ADDR=192.168.3.36:9876

OPENRESTY_ROOT=/usr/local/openresty/nginx
OPENRESTY_APP_CONF=/usr/local/openresty/nginx/conf/app.conf
OPENRESTY_GATE_LUA=/usr/local/openresty/nginx/lua/seckill_gate.lua
OPENRESTY_HTML_ROOT=/usr/local/openresty/nginx/html

FRONTEND_REPO="${PWD}/html"
MOCK_PAYMENT_REPO="${PWD}/taopiaopiao-payment-system"
```

生效：

```bash
source "$HOME/taopiaopiao-nodeb.env"
env | grep -E 'NACOS|ROCKETMQ|OPENRESTY|NODE_'
```

---

## 11. 确认 Nacos 配置已就绪

在 `NodeA` 的 Nacos 中确认已存在这些 `dataId`：

- `backend-common.yaml`
- `order-service.yaml`
- `seckill-service.yaml`
- `session-service.yaml`
- `user-service.yaml`
- `event-service.yaml`
- `gateway.yaml`

仓库内对应文件在：

- `deploy/nacos/backend-common.yaml`
- `deploy/nacos/order-service.yaml`
- `deploy/nacos/seckill-service.yaml`
- `deploy/nacos/session-service.yaml`
- `deploy/nacos/user-service.yaml`
- `deploy/nacos/event-service.yaml`
- `deploy/nacos/gateway.yaml`

---

## 12. 构建 NodeB 需要的服务

当前只构建核心链路：

```bash
cd "$HOME/projects/taopiaopiao"

mvn -q -DskipTests -pl taopiaopiao-gateway -am package
mvn -q -DskipTests -pl taopiaopiao-seckill-service/taopiaopiao-seckill-service-application -am package
mvn -q -DskipTests -pl taopiaopiao-order-service/taopiaopiao-order-service-application -am package
mvn -q -DskipTests -pl taopiaopiao-session-service/taopiaopiao-session-service-application -am package
mvn -q -DskipTests -f taopiaopiao-payment-system/pom.xml package
```

---

## 13. 同步 OpenResty 配置与静态资源

```bash
cd "$HOME/projects/taopiaopiao"

sudo install -d /usr/local/openresty/nginx/conf
sudo install -d /usr/local/openresty/nginx/lua
sudo install -d /usr/local/openresty/nginx/html

sudo cp deploy/openresty/nginx.conf /usr/local/openresty/nginx/conf/nginx.conf
sudo cp deploy/openresty/app.conf /usr/local/openresty/nginx/conf/app.conf
sudo cp deploy/openresty/lua/*.lua /usr/local/openresty/nginx/lua/
sudo rsync -av --delete html/ /usr/local/openresty/nginx/html/
```

验证：

```bash
sudo /usr/local/openresty/nginx/sbin/nginx -t
```

---

## 14. 生成服务启动脚本

先创建统一运行目录：

```bash
sudo install -d -m 0755 /opt/taopiaopiao/bin
sudo install -d -m 0755 /opt/taopiaopiao/logs
sudo install -d -m 0755 /opt/taopiaopiao/run
```

创建 `start-java-service.sh`：

```bash
sudo tee /opt/taopiaopiao/bin/start-java-service.sh >/dev/null <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="$1"
JAR_PATH="$2"
ENV_FILE="$3"

source "$ENV_FILE"

exec /usr/bin/java -jar "$JAR_PATH"
EOF
sudo chmod +x /opt/taopiaopiao/bin/start-java-service.sh
```

---

## 15. 为服务创建 systemd 单元

先找出真实 jar 文件：

```bash
find "$HOME/projects/taopiaopiao" -path '*/target/*.jar' ! -name 'original-*.jar' | sort
```

下面 5 个服务都按这个模式创建。

### 15.1 gateway

```bash
sudo tee /etc/systemd/system/tpp-gateway.service >/dev/null <<EOF
[Unit]
Description=TaoPiaoPiao Gateway
After=network.target

[Service]
Type=simple
User=$USER
WorkingDirectory=$HOME/projects/taopiaopiao
EnvironmentFile=$HOME/taopiaopiao-nodeb.env
ExecStart=/opt/taopiaopiao/bin/start-java-service.sh gateway $HOME/projects/taopiaopiao/taopiaopiao-gateway/target/taopiaopiao-gateway-1.0-SNAPSHOT.jar $HOME/taopiaopiao-nodeb.env
Restart=always
RestartSec=5
StandardOutput=append:/opt/taopiaopiao/logs/gateway.log
StandardError=append:/opt/taopiaopiao/logs/gateway.log

[Install]
WantedBy=multi-user.target
EOF
```

### 15.2 seckill-service

```bash
sudo tee /etc/systemd/system/tpp-seckill.service >/dev/null <<EOF
[Unit]
Description=TaoPiaoPiao Seckill Service
After=network.target

[Service]
Type=simple
User=$USER
WorkingDirectory=$HOME/projects/taopiaopiao
EnvironmentFile=$HOME/taopiaopiao-nodeb.env
ExecStart=/opt/taopiaopiao/bin/start-java-service.sh seckill-service $HOME/projects/taopiaopiao/taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/target/taopiaopiao-seckill-service-application-1.0-SNAPSHOT.jar $HOME/taopiaopiao-nodeb.env
Restart=always
RestartSec=5
StandardOutput=append:/opt/taopiaopiao/logs/seckill-service.log
StandardError=append:/opt/taopiaopiao/logs/seckill-service.log

[Install]
WantedBy=multi-user.target
EOF
```

### 15.3 order-service

```bash
sudo tee /etc/systemd/system/tpp-order.service >/dev/null <<EOF
[Unit]
Description=TaoPiaoPiao Order Service
After=network.target

[Service]
Type=simple
User=$USER
WorkingDirectory=$HOME/projects/taopiaopiao
EnvironmentFile=$HOME/taopiaopiao-nodeb.env
ExecStart=/opt/taopiaopiao/bin/start-java-service.sh order-service $HOME/projects/taopiaopiao/taopiaopiao-order-service/taopiaopiao-order-service-application/target/taopiaopiao-order-service-application-1.0-SNAPSHOT.jar $HOME/taopiaopiao-nodeb.env
Restart=always
RestartSec=5
StandardOutput=append:/opt/taopiaopiao/logs/order-service.log
StandardError=append:/opt/taopiaopiao/logs/order-service.log

[Install]
WantedBy=multi-user.target
EOF
```

### 15.4 session-service

```bash
sudo tee /etc/systemd/system/tpp-session.service >/dev/null <<EOF
[Unit]
Description=TaoPiaoPiao Session Service
After=network.target

[Service]
Type=simple
User=$USER
WorkingDirectory=$HOME/projects/taopiaopiao
EnvironmentFile=$HOME/taopiaopiao-nodeb.env
ExecStart=/opt/taopiaopiao/bin/start-java-service.sh session-service $HOME/projects/taopiaopiao/taopiaopiao-session-service/taopiaopiao-session-service-application/target/taopiaopiao-session-service-application-1.0-SNAPSHOT.jar $HOME/taopiaopiao-nodeb.env
Restart=always
RestartSec=5
StandardOutput=append:/opt/taopiaopiao/logs/session-service.log
StandardError=append:/opt/taopiaopiao/logs/session-service.log

[Install]
WantedBy=multi-user.target
EOF
```

### 15.5 payment-system

```bash
sudo tee /etc/systemd/system/tpp-payment.service >/dev/null <<EOF
[Unit]
Description=TaoPiaoPiao Payment System
After=network.target

[Service]
Type=simple
User=$USER
WorkingDirectory=$HOME/projects/taopiaopiao
EnvironmentFile=$HOME/taopiaopiao-nodeb.env
ExecStart=/opt/taopiaopiao/bin/start-java-service.sh payment-system $HOME/projects/taopiaopiao/taopiaopiao-payment-system/target/taopiaopiao-payment-system-1.0.0.jar $HOME/taopiaopiao-nodeb.env
Restart=always
RestartSec=5
StandardOutput=append:/opt/taopiaopiao/logs/payment-system.log
StandardError=append:/opt/taopiaopiao/logs/payment-system.log

[Install]
WantedBy=multi-user.target
EOF
```

---

## 16. 启动服务

```bash
sudo systemctl daemon-reload

sudo systemctl enable tpp-payment
sudo systemctl enable tpp-session
sudo systemctl enable tpp-order
sudo systemctl enable tpp-seckill
sudo systemctl enable tpp-gateway

sudo systemctl start tpp-payment
sudo systemctl start tpp-session
sudo systemctl start tpp-order
sudo systemctl start tpp-seckill
sudo systemctl start tpp-gateway
```

检查状态：

```bash
sudo systemctl status tpp-payment --no-pager
sudo systemctl status tpp-session --no-pager
sudo systemctl status tpp-order --no-pager
sudo systemctl status tpp-seckill --no-pager
sudo systemctl status tpp-gateway --no-pager
```

看日志：

```bash
tail -f /opt/taopiaopiao/logs/payment-system.log
tail -f /opt/taopiaopiao/logs/order-service.log
tail -f /opt/taopiaopiao/logs/gateway.log
```

---

## 17. 启动 OpenResty

```bash
sudo /usr/local/openresty/nginx/sbin/nginx
```

如果已经启动过：

```bash
sudo /usr/local/openresty/nginx/sbin/nginx -s reload
```

---

## 18. 验证 NodeB 是否具备接管能力

### 18.1 本机端口检查

```bash
ss -ltnp | grep -E '7500|8080|8084|8086|8087'
```

### 18.1.1 CLI-only 主机说明

当前 `NodeB` 没有桌面依赖，验证全部通过命令行完成，不要求本机图形访问能力。

### 18.2 Nacos 服务注册检查

到 `NodeA` 的 Nacos 控制台确认：

- `payment-system`
- `gateway`
- `session-service`
- `seckill-service`
- `order-service`

### 18.3 本机接口检查

```bash
curl -I http://127.0.0.1/admin/
curl -I http://127.0.0.1/client/
curl -I http://127.0.0.1/api/
curl -s http://127.0.0.1/payment/query?orderNo=TEST_ORDER
```

### 18.4 订单服务与支付服务联通检查

重点观察：

- `order-service` 启动时是否能正常拿到 `payment-system`
- `payment-system` 是否成功注册到 Nacos
- `order-service` 是否还出现旧的 `payment.base-url` 相关错误

---

## 19. 当前阶段验收标准

达到以下条件即可认为 `NodeB` 第一阶段完成：

1. `NodeB` 能启动 5 个核心服务
2. 这些服务都能注册到 `NodeA` 的 Nacos
3. `order-service` 能通过服务发现调用 `payment-system`
4. OpenResty 能正常提供静态资源和 API 入口
5. 从 `NodeB` 本机访问时，核心购票链路可走通
6. 在停掉 `NodeA` 对应核心服务后，Nacos 中只剩 `NodeB(192.168.3.41)` 的核心实例，且 `NodeB` 本机访问仍可成功
7. keepalived 启动后，NodeB 能在 NodeA keepalived 停止时接管 VIP，并在 NodeA 恢复后按“手动回切”策略保持当前持有状态

---

## 20. 当前不要做的事情

当前阶段不要提前做这些：

1. 不要把 `192.168.3.39` 写成 `NodeB` 当前主服务注册地址
2. 不要把 RocketMQ nameserver 改成双节点
3. 不要在 `NodeB` 单独改成全 Docker
4. 不要先上 MySQL / Redis 自动切换
5. 不要假设 `NodeA` 现有核心服务一定是由仓库 `bin` 脚本托管；演练前先核对 `.run/*.pid` 或按端口确认真实进程

---

## 21. 下一阶段

`NodeB` 第一阶段完成后，再继续：

1. 为 `NodeB` 保持 OpenResty 配置与静态资源同步流程
2. 继续推进 Redis 主从
3. 再推进 MySQL 主从
4. 再决定是否部署：
   - RocketMQ slave
   - Nacos 第二节点或第三轻量节点方案
