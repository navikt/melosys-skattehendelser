CREATE TABLE publiserings_historikk
(
    id                 BIGSERIAL PRIMARY KEY,
    periode_id         BIGINT     NOT NULL,
    inntektsaar        VARCHAR(4),
    sekvensnummer      BIGINT     NOT NULL,
    siste_hendelse_tid TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (periode_id) REFERENCES periode (id),
    CONSTRAINT unique_periode_sekvensnummer UNIQUE (sekvensnummer)
);
