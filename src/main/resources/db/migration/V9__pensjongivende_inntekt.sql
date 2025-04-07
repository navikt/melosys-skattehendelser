CREATE TABLE pensjonsgivende_inntekt
(
    id                BIGSERIAL PRIMARY KEY,
    periode_id        BIGINT    NOT NULL
        CONSTRAINT fk_periode REFERENCES periode ON DELETE CASCADE,
    duplikater        INT       NOT NULL default (0),
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    historisk_inntekt JSONB     NOT NULL
);