CREATE TABLE sekvens_historikk
(
    id                 BIGSERIAL PRIMARY KEY,
    person_id          BIGINT    NOT NULL,
    sekvensnummer      BIGINT    NOT NULL,
    antall             INT       NOT NULL DEFAULT 0,
    siste_Hendelse_tid TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (person_id) REFERENCES person (id)
);

BEGIN;
INSERT INTO sekvens_historikk (person_id, sekvensnummer)
SELECT id, sekvensnummer
FROM person
WHERE sekvensnummer IS NOT NULL;
COMMIT;

ALTER TABLE person
DROP COLUMN sekvensnummer;