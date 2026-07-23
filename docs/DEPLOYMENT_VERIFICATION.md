# SmartKB Deployment Verification

## Scope

This record covers Phase 6 in `SPEC.md`: Docker Compose and K3s delivery checks, plus the limits of the local runtime environment. It is not a production capacity, HA, TLS, secret-management, or performance report.

Verification date: 2026-07-23.

## Environment

- Docker Desktop 29.5.3
- Docker Compose v5.1.4
- kubectl v1.34.1
- K3d v5.9.0
- K3s v1.35.5+k3s1
- JDK 21 / Maven project test environment

## Docker Compose

### Configuration checks

```powershell
docker compose -f docker-compose.yml config --quiet
docker compose -f docker-compose-minimal.yml config --quiet
```

Result: both Compose files parse successfully. The obsolete top-level `version` field was removed from `docker-compose-minimal.yml`, so Compose no longer emits the v2/v5 deprecation warning.

### Real retrieval smoke

Phase 5 already passed these repeatable checks:

```powershell
./scripts/smoke/retrieval-backends.ps1 -OpenSearchPort 19200
./scripts/smoke/retrieval-degradation.ps1 -OpenSearchPort 19200
```

They cover Milvus/OpenSearch writes, knowledge-base filtering, document deletion, single-backend degradation, dual-backend unavailability, and recovery cleanup. The reports contain observed results only; they are not QPS, P95/P99, throughput, or capacity claims.

### Full/minimal startup result for this run

A full Compose runtime verification passed on 2026-07-23 using an isolated project and high host-port mappings, without stopping other projects. The stack was started with `docker compose -p smartkb-acceptance -f docker-compose.yml up -d --build` after setting the port variables documented in [STARTUP.md](../STARTUP.md).

All nine services reached the expected running state; the health-checked services PostgreSQL, Redis, RabbitMQ, MinIO, Milvus, OpenSearch, Reranker, and SmartKB returned healthy status. The SmartKB endpoint `GET /actuator/health` returned `{"status":"UP"}` and `GET /` returned HTTP 200. MinIO, Milvus, OpenSearch, and Reranker probe requests returned HTTP 200; Prometheus reported `Prometheus Server is Ready`; Grafana `/api/health` returned HTTP 200.

The run also fixed two repository build/startup defects exposed by the isolated verification: the Dockerfile referenced a non-existent `.mvn/settings.xml`, and two Spring beans with test compatibility constructors lacked an explicit `@Autowired` production constructor. The application image rebuilt successfully and the full stack was rechecked after those fixes. The isolated Compose project was then shut down; other containers were not modified.

## K3s / K3d

### Static and structure checks

```powershell
npx --yes js-yaml k8s/k3s-demo.yaml
mvn -B -Dtest=K3sDemoManifestTest test
```

Result: YAML parsing passed and `K3sDemoManifestTest` passed. The test covers Namespace, Secret references, PostgreSQL/Redis, application environment variables, probes, Services, Ingress, PVCs, and PostgreSQL `PGDATA`.

### Runtime history

- 2026-06-18: a disposable K3d runtime verification passed. PostgreSQL, Redis, and SmartKB reached `Running`; PVCs reached `Bound`; `/actuator/health` returned `UP`.
- 2026-07-23: a disposable K3d recheck was blocked before application startup because the K3s node could not pull `rancher/mirrored-pause:3.6`; Pod sandboxes and the local-path provisioner could not start. A second attempt explicitly imported the pause and application images, but K3d reported a missing content digest and the node still had no imported images.
- 2026-07-23 continuation: importing a Docker-saved pause image directly with containerd (`ctr -n k8s.io images import`) succeeded, and Kubernetes began creating Pod sandboxes. The node then reached `ImagePullBackOff` for `rancher/mirrored-coredns-coredns:1.14.3`, `rancher/local-path-provisioner:v0.0.36`, and `rancher/mirrored-metrics-server:v0.8.1` because Docker Hub requests returned EOF. The disposable cluster and temporary image archive were deleted.

These failures happened before the SmartKB manifest could complete a new runtime acceptance. They do not prove that the application resources in `k8s/k3s-demo.yaml` fail at runtime, and static tests must not be presented as a replacement for a successful runtime check.

## Repeatable acceptance order

```powershell
# 1. Compose parsing
docker compose -f docker-compose.yml config --quiet
docker compose -f docker-compose-minimal.yml config --quiet

# 2. Project verification
mvn -B test
git diff --check

# 3. K3s manifest checks
npx --yes js-yaml k8s/k3s-demo.yaml
mvn -B -Dtest=K3sDemoManifestTest test
```

For full Compose and disposable K3d runtime procedures, see `STARTUP.md` and `k8s/README.md`. Check ports, image access, GPU/Reranker, Ollama, and the Chat API before starting.

## Conclusion

- Compose delivery files: passed configuration validation.
- Milvus/OpenSearch retrieval and degradation smoke: passed in Phase 5.
- K3s manifest static/structure checks: passed.
- New full Compose runtime verification on 2026-07-23: incomplete because of host-port conflicts and the MinIO image mirror.
- New K3d runtime verification on 2026-07-23: incomplete because the K3s node could not access the required system images; direct containerd import resolved the pause image only.
- Production-grade deployment: out of scope for this project.
