# Redis ChatMemory Verification

## Goal

Verify the Redis-backed `ChatMemory` in two layers:

- Automated integration coverage for the Redis storage adapter itself.
- Manual live smoke coverage for the running SmartKB app, browser conversation flow, and LLM-backed follow-up behavior.

## Automated Coverage

`RedisChatMemoryIT` runs under the existing Maven `integration-tests` profile.

Command:

```powershell
mvn -Pintegration-tests verify
```

The test uses Testcontainers with `redis:7-alpine` and does not depend on local Docker Compose volumes or a local Redis installation.

Covered behavior:

- Writes user and assistant messages to `smartkb:chat:{conversationId}`.
- Stores the conversation as a Redis List.
- Applies a positive TTL.
- Reads messages back in chronological order.
- Deserializes message types and escaped content through `RedisChatMemory`.
- Refreshes TTL on read.
- Deletes the Redis key through `clear(conversationId)`.

Default unit tests remain Docker-free:

```powershell
mvn test
```

## Manual Live Smoke

The SPEC checklist still requires a running SmartKB app plus real Redis and LLM calls. The automated integration test above does not replace that checklist because it does not verify the browser, controller, `MessageChatMemoryAdvisor`, or Advanced RAG query rewrite behavior.

Recommended live path after Docker Desktop is available:

1. Start PostgreSQL and Redis:

   ```powershell
   docker compose -f docker-compose-minimal.yml up -d
   ```

2. Confirm Ollama is listening and has the embedding model:

   ```powershell
   ollama list
   ```

3. Start SmartKB with profile `hybrid`.

4. Confirm startup log includes:

   ```text
   初始化 ChatMemory (Redis 模式, TTL=24h)
   ```

5. Ask one question in conversation mode and confirm Redis key creation:

   ```powershell
   docker exec smartkb-redis redis-cli keys "smartkb:chat:*"
   ```

6. Refresh the browser and ask a follow-up with the same `conversationId`.

7. Restart only the SmartKB app, ask another follow-up with the same `conversationId`, and confirm the model still sees previous context.

8. Repeat in Advanced mode and confirm query rewrite can use previous context.

9. Click "新会话" and confirm the matching Redis key is deleted:

   ```powershell
   docker exec smartkb-redis redis-cli keys "smartkb:chat:*"
   ```

## Current Environment Note

On 2026-06-17, live smoke could not be executed in this Windows environment:

- Docker Desktop service was stopped.
- Docker API was unavailable at `npipe:////./pipe/dockerDesktopLinuxEngine`.
- No local `redis-server`, `redis-cli`, `psql`, or PostgreSQL service was available.
- Port checks showed only Ollama on `11434`.

Docker Desktop was not restarted to avoid disrupting other local project containers.

## Live Verification Record

Date: 2026-06-17

Docker Desktop was later started by the user. The SmartKB Docker Compose stack was already running:

- `smartkb-app`: healthy, mapped to `http://localhost:8082`.
- `smartkb-postgres`: healthy.
- `smartkb-redis`: healthy.
- `smartkb-prometheus` and `smartkb-grafana`: running.

Verified:

- `http://localhost:8082/actuator/health` returned `UP` with PostgreSQL and Redis `UP`.
- `smartkb-app` logs contained `初始化 RedisChatMemory, TTL=24h`.
- `smartkb-app` logs contained `初始化 ChatMemory (Redis 模式, TTL=24h)`.
- Before the live request, `docker exec smartkb-redis redis-cli keys "smartkb:chat:*"` returned no keys.
- A POST to `/api/chat/conversation` with `conversationId=redis-live-20260617111513` created `smartkb:chat:redis-live-20260617111513`.
- The key type was `list`.
- The list length was `1`.
- The TTL was positive and close to 24 hours (`86392` seconds at check time).
- DELETE `/api/chat/memory/redis-live-20260617111513` returned success.
- After DELETE, `exists smartkb:chat:redis-live-20260617111513` returned `0`.

Not verified:

- The conversation request returned HTTP 500 because the Chat model call failed with `401 无效的令牌`.
- Host environment variables `TRANSIT_API_KEY`, `OPENAI_API_KEY`, `TRANSIT_BASE_URL`, and `AI_MODEL` were not present, so the running container used its configured defaults.
- Browser refresh follow-up memory, app restart follow-up memory, and Advanced RAG history-aware query rewrite still need a valid Chat token.
- `mvn -Pintegration-tests verify` completed successfully, but `JdbcEvalCaseRunStoreIT` and `RedisChatMemoryIT` were still skipped because Testcontainers could not obtain a valid Java Docker client through the Windows npipe setup, even though Docker CLI and Docker Compose containers were available.

## Live Verification Record - Completed

Date: 2026-06-17

After a valid transit key was added to local `.env`, Docker Compose was adjusted so `OPENAI_API_KEY` can fall back to `TRANSIT_API_KEY` for container mode.

Additional implementation fix:

- Added a dedicated `conversationChatClient` with only `MessageChatMemoryAdvisor`.
- Kept the existing primary `chatClient` for RAG with `MessageChatMemoryAdvisor + QuestionAnswerAdvisor`.
- Updated `RagService.queryWithContext` and streaming conversation mode to use `conversationChatClient`.
- Reason: with the default RAG client, `QuestionAnswerAdvisor` could inject a no-document constraint that outweighed Redis ChatMemory history when the question was not answerable from uploaded documents.

Verified live with `conversationId=redis-memory-fixed-20260617122343` and phrase `smartkb-cypress-122343`:

- `smartkb-app` started healthy and logged `初始化 Conversation ChatClient with ChatMemory Advisor`.
- First conversation call stored the phrase and wrote Redis list entries.
- Same `conversationId` follow-up returned the expected phrase.
- Redis list length before restart was `4`; TTL was `86400`.
- Restarted only `smartkb-app`; PostgreSQL and Redis were left running.
- Post-restart follow-up with the same `conversationId` returned the expected phrase.
- Redis list length after restart was `6`; TTL was refreshed to `86400`.
- Advanced RAG call loaded 6 ChatMemory messages.
- Advanced RAG query rewrite included `smartkb-cypress-122343` in `rewrittenQuery`.
- DELETE `/api/chat/memory/redis-memory-fixed-20260617122343` returned success.
- Redis `exists smartkb:chat:redis-memory-fixed-20260617122343` returned `0`.

Verification commands run in this stage:

```powershell
mvn test
mvn package -DskipTests
docker cp target/smartkb-java21-spring-ai-rag-1.0.0-SNAPSHOT.jar smartkb-app:/app/app.jar
docker restart smartkb-app
```

Notes:

- Full Docker image rebuild with `docker compose up -d --no-deps --build --force-recreate smartkb-app` timed out after 5 minutes in this environment. The live verification used a local Maven package plus `docker cp` to update the running app container.
- The long-running Docker build process was stopped after timeout so it would not keep consuming local resources.
