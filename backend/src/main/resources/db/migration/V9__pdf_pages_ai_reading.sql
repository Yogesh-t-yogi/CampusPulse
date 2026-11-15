-- Plan 14, Phase C: scanned PDF pages read by an AI vision model (idempotent).

-- The uploaded PDF and its per-page text layer, kept while pages wait to be read (and for re-reading).
CREATE TABLE IF NOT EXISTS syllabus_file (
    syllabus_id   BIGINT PRIMARY KEY REFERENCES syllabus(id) ON DELETE CASCADE,
    sha256        VARCHAR(64) NOT NULL,
    page_count    INT NOT NULL,
    content       BYTEA NOT NULL,
    page_texts    TEXT NOT NULL,          -- JSON array: text layer of every page
    pending_pages TEXT NOT NULL,          -- JSON array of page numbers (1-based) still to read with AI
    page_summary  TEXT,                   -- JSON: how each page was read
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

-- Cache of AI/OCR page text by file hash, so the same PDF is never sent twice.
CREATE TABLE IF NOT EXISTS pdf_page_text (
    sha256     VARCHAR(64) NOT NULL,
    page_no    INT NOT NULL,
    method     VARCHAR(20) NOT NULL,
    text       TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (sha256, page_no)
);

ALTER TABLE syllabus ALTER COLUMN extracted_text DROP NOT NULL;
