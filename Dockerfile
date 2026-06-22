# syntax=docker/dockerfile:1.7
# ========== 多阶段构建 — 容器内完成 Maven 编译 ==========
# 设计说明：多阶段构建是云原生标准做法，Dockerfile 自身就是完整构建方案，
# CI/CD 只需 docker build，不依赖宿主机环境。
# 国内网络问题：通过 .mvn/settings.xml 配置阿里云镜像解决。

# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# 先复制 Maven 配置和 pom.xml，利用 Docker 缓存加速依赖下载
COPY .mvn/settings.xml /tmp/maven-settings.xml
COPY pom.xml .

# 下载依赖（阿里云镜像已在 settings.xml 中配置，Docker 内下载速度快）
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -s /tmp/maven-settings.xml dependency:go-offline -B

# 复制源代码并构建
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -s /tmp/maven-settings.xml clean package -DskipTests -B

# ---- 运行阶段 ----
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 创建非 root 用户（安全最佳实践）
RUN addgroup -S smartkb && adduser -S smartkb -G smartkb

# 从构建阶段复制 JAR
COPY --from=builder /app/target/*.jar app.jar

# 修改文件所有者
RUN chown -R smartkb:smartkb /app

# 切换到非 root 用户
USER smartkb

# JVM 参数优化（支持 Virtual Threads）
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# 暴露端口
EXPOSE 8080

# 启动应用（exec 形式确保 Java 进程为 PID 1，正确接收 SIGTERM 实现优雅停机）
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
