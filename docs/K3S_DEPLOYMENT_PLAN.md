# K3s Deployment Plan

## Goal

Provide a conservative K3s deployment path for SmartKB that preserves the current Docker Compose demo and avoids committing real secrets or runtime data.

## Current State

Existing files:

- `Dockerfile`: builds the Spring Boot app inside Docker with Maven and runs as a non-root user.
- `docker-compose.yml`: verified local full-chain deployment for PostgreSQL, Redis, app, Prometheus, and Grafana.
- `k8s/deployment.yaml`: draft Kubernetes manifest for namespace, PostgreSQL, Redis, SmartKB app, service, and ingress.
- `k8s/README.md`: generic Kubernetes deployment notes.

The current `k8s/deployment.yaml` should be treated as a draft, not a verified K3s manifest.

## Current Manifest Gaps

- `smartkb-config` is created but not mounted into the application pod.
- The app pod does not set `SPRING_DATASOURCE_URL`, `SPRING_DATA_REDIS_HOST`, or `OLLAMA_BASE_URL`.
- `SPRING_PROFILES_ACTIVE=prod` is set, but the repository does not currently include a dedicated `application-prod.yml`.
- The Secret contains placeholder values and must not be used for real production secrets as-is.
- PostgreSQL uses a Deployment with a PVC; this is acceptable for a local K3s demo, but a StatefulSet is the safer production shape.
- Prometheus and Grafana are not included in the K3s manifest even though they are part of the Docker Compose story.
- No image registry, tag policy, backup policy, or resource sizing for a real cluster is defined yet.

## Recommended K3s Scope

For the next K3s milestone, target a single-node demo environment:

- Namespace: `smartkb`
- PostgreSQL with pgvector and one PVC
- Redis with one PVC or ephemeral storage for the demo
- SmartKB app with one replica first
- Traefik Ingress, because K3s ships with Traefik by default
- Optional port-forward path for local verification
- Secrets created through `kubectl create secret`, not committed values

Keep Prometheus/Grafana as a follow-up unless the K3s demo explicitly needs monitoring.

## Image Strategy

For local K3s:

```powershell
docker build -t smartkb:local .
docker save smartkb:local -o smartkb-local.tar
k3s ctr images import smartkb-local.tar
```

For a remote or shared cluster:

```powershell
docker build -t <registry>/smartkb:<tag> .
docker push <registry>/smartkb:<tag>
```

Use immutable tags for shared environments. Avoid deploying `latest` outside a local demo.

## Secret Strategy

Do not commit real API keys, tokens, cookies, or private keys.

Create secrets at deploy time:

```powershell
kubectl create namespace smartkb
kubectl -n smartkb create secret generic smartkb-secrets `
  --from-literal=postgres-password='<postgres-password>' `
  --from-literal=openai-api-key='<api-key>'
```

Only document required keys:

- `postgres-password`
- `openai-api-key`

## Required App Environment

The app deployment should explicitly provide:

```yaml
env:
  - name: SPRING_DATASOURCE_URL
    value: jdbc:postgresql://postgres-service:5432/smartkb
  - name: SPRING_DATASOURCE_USERNAME
    value: smartkb
  - name: SPRING_DATASOURCE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: smartkb-secrets
        key: postgres-password
  - name: SPRING_DATA_REDIS_HOST
    value: redis-service
  - name: SPRING_DATA_REDIS_PORT
    value: "6379"
  - name: OLLAMA_BASE_URL
    value: http://host.k3d.internal:11434
  - name: OPENAI_API_KEY
    valueFrom:
      secretKeyRef:
        name: smartkb-secrets
        key: openai-api-key
```

For K3s on a real node, replace `OLLAMA_BASE_URL` with the actual reachable Ollama or model gateway URL.

## Verification Path

After applying manifests:

```powershell
kubectl -n smartkb get pods
kubectl -n smartkb get svc
kubectl -n smartkb logs deploy/smartkb-app --tail=100
kubectl -n smartkb port-forward svc/smartkb-service 18080:80
curl.exe http://localhost:18080/actuator/health
```

Expected health details:

- `db`: `UP`
- `redis`: `UP`
- `diskSpace`: `UP`

Then verify one API that does not require a live LLM call:

```powershell
curl.exe http://localhost:18080/api/agent/eval/report
```

## Rollout And Cleanup

Rollout:

```powershell
kubectl -n smartkb rollout status deploy/smartkb-app
kubectl -n smartkb describe pod -l app=smartkb-app
```

Cleanup for a demo cluster:

```powershell
kubectl delete namespace smartkb
```

Do not delete PVCs in a shared environment without an explicit data-retention decision.

## Not Doing Yet

- No production HA PostgreSQL design.
- No cloud registry or CI/CD pipeline.
- No TLS certificate automation.
- No Prometheus/Grafana K3s manifests.
- No secrets manager integration.
- No automatic data migration framework.

## Next Implementation Step

Create a K3s-specific manifest or overlay that fixes the app environment variables and secret flow, then verify it against a disposable local K3s or K3d cluster.
