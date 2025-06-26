CREATE TABLE publiserings_historikk
(
    id                 BIGSERIAL PRIMARY KEY,
    periode_id         BIGINT    NOT NULL,
    sekvensnummer      BIGINT    NOT NULL,
    siste_hendelse_tid TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (periode_id) REFERENCES periode (id),
    CONSTRAINT unique_periode_sekvensnummer UNIQUE (periode_id, sekvensnummer)
);
