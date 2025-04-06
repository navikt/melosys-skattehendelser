CREATE TABLE pensjonsgivende_inntekt
(
    id                BIGSERIAL PRIMARY KEY,
    periode_id        BIGINT    NOT NULL
        CONSTRAINT fk_periode REFERENCES periode ON DELETE CASCADE,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    historisk_inntekt JSONB     NOT NULL
);