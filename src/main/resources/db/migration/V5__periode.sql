CREATE TABLE PERIODE
(
    id        SERIAL PRIMARY KEY,
    person_id BIGINT NOT NULL,
    fom       DATE   NOT NULL,
    tom       DATE   NOT NULL,
    CONSTRAINT fk_person
        FOREIGN KEY (person_id)
            REFERENCES PERSON (id)
            ON DELETE CASCADE
);