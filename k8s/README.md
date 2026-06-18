# Kubernetes 部署指南

K3s 部署方案和现有 manifest 审计见：`../docs/K3S_DEPLOYMENT_PLAN.md`。

当前 `k8s/deployment.yaml` 是草案，应用到 K3s 前需要先补齐应用环境变量、Secret 注入和本地集群验证。

K3s demo manifest: `k8s/k3s-demo.yaml`.

```powershell
kubectl create namespace smartkb
kubectl -n smartkb create secret generic smartkb-secrets `
  --from-literal=postgres-password='<postgres-password>' `
  --from-literal=openai-api-key='<api-key>'
kubectl apply -f k8s/k3s-demo.yaml
kubectl -n smartkb rollout status deploy/smartkb-app
kubectl -n smartkb port-forward svc/smartkb-service 18080:80
curl.exe http://localhost:18080/actuator/health
```

## 前置要求

- Kubernetes 集群（K3s/Minikube/EKS/AKS）
- kubectl 命令行工具
- Docker（构建镜像）

## 部署步骤

### 1. 构建 Docker 镜像

```bash
# 构建本地 K3s/K3d demo 镜像
docker build -t smartkb:local .

# （可选）推送到镜像仓库
docker tag smartkb:local your-registry/smartkb:<tag>
docker push your-registry/smartkb:<tag>
```

### 2. 配置 Secrets

编辑 `k8s/deployment.yaml`，修改以下内容：

```yaml
# 在 Secret 中配置你的 OpenAI API Key
stringData:
  openai-api-key: sk-your-actual-api-key-here
```

### 3. 部署到 Kubernetes

```bash
# 应用所有配置
kubectl apply -f k8s/deployment.yaml

# 查看部署状态
kubectl get pods -n smartkb
kubectl get svc -n smartkb
```

### 4. 验证部署

```bash
# 查看应用日志
kubectl logs -f deployment/smartkb-app -n smartkb

# 查看服务状态
kubectl get svc smartkb-service -n smartkb

# 端口转发（本地测试）
kubectl port-forward svc/smartkb-service 8080:80 -n smartkb
```

### 5. 访问应用

```bash
# 如果使用 LoadBalancer
EXTERNAL_IP=$(kubectl get svc smartkb-service -n smartkb -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
curl http://$EXTERNAL_IP

# 如果使用 port-forward
curl http://localhost:8080
```

## 配置 Ingress（可选）

如果需要域名访问，配置 Ingress：

```bash
# 修改 /etc/hosts（本地测试）
echo "127.0.0.1 smartkb.local" | sudo tee -a /etc/hosts

# 访问
curl http://smartkb.local
```

## 监控配置

### 1. 部署 Prometheus

```bash
# 使用 Helm 安装
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install prometheus prometheus-community/kube-prometheus-stack -n smartkb
```

### 2. 导入 Grafana Dashboard

1. 访问 Grafana：`kubectl port-forward svc/prometheus-grafana 3000:80 -n smartkb`
2. 登录（admin/prom-operator）
3. 导入 `monitoring/grafana-dashboard.json`

## 扩缩容

```bash
# 水平扩展
kubectl scale deployment smartkb-app --replicas=3 -n smartkb

# 查看扩展状态
kubectl get pods -n smartkb -w
```

## 滚动更新

```bash
# 更新镜像
kubectl set image deployment/smartkb-app smartkb=smartkb:v2 -n smartkb

# 查看更新状态
kubectl rollout status deployment/smartkb-app -n smartkb

# 回滚
kubectl rollout undo deployment/smartkb-app -n smartkb
```

## 清理资源

```bash
# 删除所有资源
kubectl delete namespace smartkb
```

## 生产环境建议

1. **持久化存储**：使用 StatefulSet 管理 PostgreSQL
2. **高可用**：配置多副本 + PodDisruptionBudget
3. **资源限制**：合理设置 requests/limits
4. **健康检查**：配置 liveness/readiness probe
5. **日志收集**：集成 ELK/Loki
6. **告警配置**：Prometheus Alertmanager

## 常见问题

### Pod 启动失败

```bash
# 查看日志
kubectl logs pod-name -n smartkb
kubectl describe pod pod-name -n smartkb
```

### 数据库连接失败

检查：
1. PostgreSQL Service 是否正常
2. 环境变量是否正确
3. 网络策略是否允许

### Virtual Threads 验证

```bash
# 查看应用日志中的 Virtual Threads 标识
kubectl logs -f deployment/smartkb-app -n smartkb | grep "Virtual Threads"
```
