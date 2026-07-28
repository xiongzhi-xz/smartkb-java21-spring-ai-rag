# SmartKB Deployment Verification

## Scope

This record covers Phase 6 in `SPEC.md`: Docker Compose and K3s delivery checks, plus the limits of the local runtime environment. It is not a production capacity, HA, TLS, secret-management, or performance report.

Verification date: 2026-07-24.

## Environment

- Docker Desktop 29.5.3
- Docker Compose v5.1.4
- kubectl v1.34.1
- K3d v5.9.0
- K3s v1.35.5+k3s1
- JDK 21 / Maven project test environment

## Java 25 upgrade verification (2026-07-28)

The Phase 6 entries below are historical Java 21 evidence and must not be read as Java 25 runtime evidence. The Java 25 upgrade was verified with Temurin 25.0.3 using `mvn -B clean verify`: 102 tests passed with zero failures and zero errors. Both Compose files passed `docker compose ... config --quiet`.

`docker build -t smartkb:java25-validation .` succeeded. The resulting runtime image reports Temurin 25.0.3, contains `/app/app.jar`, and runs as non-root UID 100. The Docker build no longer prefetches test-only dependencies with `dependency:go-offline`; the complete test suite is run separately before image construction.

The PostgreSQL Testcontainers portion of `mvn -B -Dapi.version=1.40 -P integration-tests verify` passed on Java 25: PostgreSQL 16.14 started, Flyway V1/V2/V3 completed, and `EnterpriseRagPersistenceIT` passed two tests. The profile also includes retrieval smoke ITs that require running Milvus/OpenSearch and a `smartkb.degradation.scenario` value. They were not supplied in this verification, so their failure is an external-smoke precondition rather than Java 25 evidence.

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

### Air-gap runtime verification

On 2026-07-24, a disposable K3d v5.9.0 cluster using K3s v1.35.5+k3s1 completed runtime verification without registry pulls. The matching official K3s air-gap archive was copied to the server node's `/var/lib/rancher/k3s/agent/images/` directory and loaded by restarting that disposable node. A Docker archive containing `smartkb:local`, `pgvector/pgvector:pg16`, and `redis:7-alpine` was then imported with `ctr -n k8s.io images import`.

The `smartkb` namespace and placeholder-only demo Secret were created, then `k8s/k3s-demo.yaml` was applied. PostgreSQL, Redis, and SmartKB reached `Running`/`Ready`; both PVCs reached `Bound`. A port-forward to `smartkb-service` verified `GET /actuator/health` as `UP` with `db`, `redis`, `diskSpace`, liveness, and readiness all `UP`; `GET /` returned HTTP 200.

The run exposed two deployment compatibility defects that were fixed and rechecked: the Milvus client had been eagerly constructed despite the retrieval adapter's lazy-failure design, and RabbitMQ is deliberately absent from the compact K3s demo but was included in its health result. The Milvus Bean is now lazy, and the demo manifest disables only the Rabbit health indicator. This does not claim that the compact manifest provides document-ingestion or enterprise dual-index retrieval; those components remain covered by the full Compose deployment.

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
- Full Compose runtime verification on 2026-07-23: passed with an isolated project and high host ports.
- Disposable K3d air-gap runtime verification on 2026-07-24: passed with imported K3s system and application images.
- Production-grade deployment: out of scope for this project.
