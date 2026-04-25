# Deploy

## 1. 安装 Maven

使用与当前环境一致的版本：

```bash
Apache Maven 3.8.7
```

前置条件：

```bash
Java 17
```

## 2. 配置与校验

当前项目没有 Maven Wrapper，默认使用系统 `mvn`。

当前 `~/.m2/settings.xml` 不包含私服或镜像配置，只需要保证本地仓库路径可用即可：

```xml
<settings>
  <localRepository>~/.m2/repository</localRepository>
</settings>
```

校验命令：

```bash
mvn -v
```

## 3. 编译项目

在仓库根目录编译后端主工程：

```bash
mvn -q -DskipTests compile
```

如需完整安装所有后端模块到本地仓库：

```bash
mvn clean install -DskipTests
```

编译独立支付子项目：

```bash
mvn -q -f taopiaopiao-payment-system/pom.xml package -DskipTests
```
