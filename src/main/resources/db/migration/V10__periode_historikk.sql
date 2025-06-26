CREATE TABLE periode_historikk
(
    id                 BIGSERIAL PRIMARY KEY,
    periode_id         BIGINT    NOT NULL,
    sekvensnummer      BIGINT    NOT NULL,
    siste_Hendelse_tid TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (periode_id) REFERENCES periode (id)
);
