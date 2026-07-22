# Phase 3: Dual Retrieval and Index Consistency Design

## Goal

Replace the new enterprise-document retrieval path with two independent indexes:

- Milvus provides dense-vector retrieval.
- OpenSearch provides keyword retrieval, metadata filtering, and aggregations.
- PostgreSQL remains the source of truth for document lifecycle and chunk metadata.

The existing `PgVectorStore` path remains a compatibility path during migration. It is not a third production index for documents ingested after the Phase 3 cutover.

## Scope and Non-goals

This phase covers index ports, ingestion/deletion orchestration, reciprocal-rank fusion (RRF), metadata filtering, and failure handling. It does not change authentication, tenant isolation, conversation persistence, answer SSE contracts, or database migrations without a separately approved migration review.

## Index Identity Contract

Every indexed chunk must carry the same immutable identity in both systems:

| Field | Source | Meaning |
| --- | --- | --- |
| `chunkId` | PostgreSQL `document_chunk.id` | Primary identifier for fusion, cleanup, and traceability. |
| `documentId` | PostgreSQL `kb_document.id` | Document-level filter and deletion boundary. |
| `knowledgeBaseId` | PostgreSQL `kb_document.knowledge_base_id` | Knowledge-base filter. |
| `versionNo` | PostgreSQL `kb_document.version_no` | Rebuild/version boundary. |
| `ordinal` | PostgreSQL `document_chunk.ordinal` | Stable source order and citation location. |
| `contentHash` | PostgreSQL `document_chunk.content_hash` | Idempotency and integrity check. |

Milvus stores `chunkId` as its primary key and the embedding plus scalar filter fields. OpenSearch stores `chunkId` as `_id`, the text content, and the same scalar fields. Neither index owns lifecycle state.

## Ingestion Consistency Model

PostgreSQL is authoritative. An ingestion job is not marked `READY` until both target indexes acknowledge the complete set of chunks.

```text
RabbitMQ event
  -> lock/claim ingestion job
  -> parse and deterministically chunk source file
  -> persist/verify chunk facts in PostgreSQL (PENDING)
  -> embed and upsert all chunks to Milvus
  -> bulk upsert all chunks to OpenSearch
  -> atomically set chunk index_status and document/job READY in PostgreSQL
```

The delivery is at-least-once. Upserts must therefore be idempotent by `chunkId`. On any failure, the listener records a structured failure code and retains the PostgreSQL job in `FAILED`; retry reruns the same deterministic chunk identities. A partial target-index write is allowed temporarily only while the job is not `READY`; retry overwrites it. A scheduled reconciliation task is explicitly deferred to Phase 5.

Failure codes use `MILVUS_INDEX_FAILED`, `OPENSEARCH_INDEX_FAILED`, and `INDEX_FINALIZATION_FAILED`. The original exception message is retained only in the job error field, not in an API response.

## Delete Consistency Model

Deletion is permitted only for stable `READY` or `FAILED` documents, as it is today. The cleanup order is:

```text
Milvus delete by documentId
  -> OpenSearch delete-by-query documentId and wait for refresh
  -> compatibility pgvector cleanup while migration is active
  -> MinIO source deletion
  -> PostgreSQL document/job/chunk cascade
```

Any external cleanup failure prevents the PostgreSQL deletion, leaving the document retryable. Each index-cleanup adapter must be idempotent when no matching chunks remain.

## Query Contract

The new query service receives a rewritten query, `knowledgeBaseId`, optional document IDs, and `candidateTopK` (default 20). It runs dense and keyword retrieval independently, then joins candidates by `chunkId`.

```text
query
  -> Milvus dense top-K
  -> OpenSearch keyword top-K
  -> discard candidates that do not satisfy the requested metadata filter
  -> weighted RRF by chunkId
  -> hydrate content/metadata from the candidate payload
  -> existing BGE reranker
  -> final top-K evidence
```

The RRF score is deterministic:

```text
score(chunk) = 0.55 / (60 + denseRank) + 0.45 / (60 + keywordRank)
```

A missing rank contributes zero. Tie-breaking is `chunkId` ascending. The result includes source ranks and fusion score for the future retrieval trace. Reranking stays after fusion and is not part of index consistency.

## Availability and Fallback

- If both retrieval backends fail, fail the enterprise retrieval request with a controlled `RETRIEVAL_UNAVAILABLE` error; never silently search another knowledge base.
- If one backend fails, use the healthy backend, tag the retrieval mode as `dense-only` or `keyword-only`, and record the backend failure in the trace/metrics.
- The legacy `PgVectorStore` remains available only to legacy API paths during the migration. Enterprise Phase 3 APIs must not silently fall back to it, because it cannot demonstrate Milvus/OpenSearch consistency.

## Implementation Tasks

- [x] Phase 3a (20-30 min): add immutable chunk/candidate domain records and outbound ports for index write, delete, and retrieval; unit-test idempotent payload construction.
- [x] Phase 3b (20-30 min): add Milvus and OpenSearch Docker Compose services, configuration properties, health checks, and client adapters. Add no schema migration in this task.
- [ ] Phase 3c (20-30 min): change ingestion orchestration to persist stable chunk identities and write both indexes before the existing READY transition; add failure/duplicate-event tests.
- [ ] Phase 3d (20-30 min): replace enterprise retrieval with parallel dense/keyword calls, filter validation, weighted RRF, and single-backend degradation; unit-test ranking and failure modes.
- [ ] Phase 3e (20-30 min): extend deletion cleanup to both indexes, then add adapter integration tests with Testcontainers or a compose-backed acceptance test.

## Acceptance Criteria

1. A successful upload creates the same `chunkId`, `documentId`, `knowledgeBaseId`, and version in Milvus and OpenSearch before its job becomes `READY`.
2. Re-delivering an ingestion event and retrying a failed job do not create duplicate indexed chunks.
3. A Milvus or OpenSearch write failure leaves the document/job `FAILED`, exposes a retryable status, and never exposes the document as enterprise-search READY.
4. A query restricted to a document or knowledge base never returns a chunk outside that filter.
5. RRF output is deterministic for fixed ranked inputs and preserves both source ranks for later tracing.
6. Deleting a stable document removes its chunks from both target indexes before PostgreSQL lifecycle facts are cascaded.
7. Existing unit tests, new focused tests, `mvn test`, and `git diff --check` pass. The full compose acceptance test is recorded separately because it needs Milvus/OpenSearch images and local Docker resources.
