ALTER TABLE ingestion_job ADD COLUMN idempotency_key VARCHAR(128);
CREATE UNIQUE INDEX uq_ingestion_job_idempotency ON ingestion_job (idempotency_key) WHERE idempotency_key IS NOT NULL;
