# 运行阶段 — 本地先 mvn package，Docker 只负责运行 JAR
# 避免 Docker 构建时从外网拉 Maven 依赖（国内网络常超时）
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 创建非 root 用户
RUN addgroup -S smartkb && adduser -S smartkb -G smartkb

# 复制本地构建好的 JAR（必须先执行 mvn clean package -DskipTests）
COPY target/*.jar app.jar

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

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
