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
