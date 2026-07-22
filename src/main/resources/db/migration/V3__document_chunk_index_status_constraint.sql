ALTER TABLE document_chunk
    ADD CONSTRAINT chk_document_chunk_index_status
    CHECK (index_status IN ('PENDING', 'READY'));
