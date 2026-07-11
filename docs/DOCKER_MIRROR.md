# Docker 镜像加速配置指南

## 问题
Docker 无法从 Docker Hub 下载镜像（网络问题）

## 解决方案：配置镜像加速器

### 方法 1：通过 Docker Desktop GUI 配置（推荐）

1. 打开 Docker Desktop
2. 点击右上角 **设置图标（齿轮）**
3. 进入 **Docker Engine**
4. 在 JSON 配置中添加：

```json
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://dockerproxy.com",
    "https://docker.mirrors.ustc.edu.cn"
  ]
}
```

5. 点击 **Apply & Restart**
6. 等待 Docker 重启完成

### 方法 2：手动编辑配置文件

编辑文件：`C:\Users\你的用户名\.docker\daemon.json`

如果文件不存在，创建它并添加：

```json
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://dockerproxy.com",
    "https://docker.mirrors.ustc.edu.cn"
  ]
}
```

保存后重启 Docker Desktop。

---

## 重新启动容器

配置完成后，重新执行：

```bash
docker compose up -d
```

---

## 临时方案：使用已有的 PostgreSQL（如果安装了）

如果你本地已经安装了 PostgreSQL 16，可以：

1. 跳过 Docker，直接使用本地 PostgreSQL
2. 创建数据库和用户：
   ```sql
   CREATE DATABASE smartkb;
   CREATE USER smartkb WITH PASSWORD 'smartkb123';
   GRANT ALL PRIVILEGES ON DATABASE smartkb TO smartkb;
   ```
3. 安装 pgvector 扩展：
   ```sql
   CREATE EXTENSION vector;
   ```
4. 修改 application-hybrid.yml：
   ```yaml
   datasource:
     url: jdbc:postgresql://localhost:5432/smartkb
     username: smartkb
     password: smartkb123
   ```

---

## 验证镜像加速是否生效

```bash
docker info | grep "Registry Mirrors"
```

应该看到配置的镜像地址。
