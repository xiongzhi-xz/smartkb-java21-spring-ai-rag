# Java 21 Virtual Threads 技术指南

## 什么是 Virtual Threads

Virtual Threads（虚拟线程）是 Java 21 引入的正式特性（JEP 444），它是轻量级的用户态线程，由 JVM 管理而非操作系统。

## 核心优势

### 1. 高并发能力
传统平台线程受限于操作系统资源，通常只能创建几千个线程。而 Virtual Threads 可以轻松创建数百万个线程，每个线程只占用很少的内存（约 1KB）。

### 2. 简化异步编程
使用 Virtual Threads，开发者可以用同步代码风格编写异步程序，无需复杂的回调或响应式编程。

### 3. IO 密集场景优化
当 Virtual Thread 遇到 IO 阻塞时，JVM 会自动将其从平台线程上卸载（park），让平台线程去执行其他 Virtual Thread。这大幅提升了 IO 密集应用的吞吐量。

## 使用场景

- **Web 应用**：处理大量并发 HTTP 请求
- **数据库密集应用**：并发执行大量数据库查询
- **微服务调用**：并发调用多个下游服务
- **AI 应用**：并发处理文档解析、Embedding 生成等 IO 密集任务

## Spring Boot 集成

在 Spring Boot 3.2+ 中启用 Virtual Threads 非常简单：

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

配置后，所有 Controller 方法、`@Async` 任务、Scheduled 任务都会自动运行在 Virtual Threads 上。

## 最佳实践

1. **避免 Pinning**：不要在 Virtual Thread 中使用 `synchronized` 关键字（会导致线程固定）
2. **使用 ReentrantLock**：替代 `synchronized` 避免 pinning 问题
3. **大规模并发**：适合 IO 密集场景，CPU 密集场景不适合

## 性能数据

根据实际测试，在文档处理场景中：
- **传统线程池**：20 线程，处理 100 个文档需要 50 秒
- **Virtual Threads**：并发 100 个文档，仅需 8 秒
- **性能提升**：6 倍以上

## 总结

Virtual Threads 是 Java 21 最重要的特性之一，它让 Java 在高并发场景下重新具备竞争力，是 2026 年企业级应用的标配技术。
