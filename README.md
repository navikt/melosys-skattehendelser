# melosys-skattehendelser

Tjeneste for behandling av skattehendelser i Melosys-systemet.

## Beskrivelse

`melosys-skattehendelser` er en Spring Boot-applikasjon skrevet i Kotlin som behandler skattehendelser fra Skatteetaten og publiserer relevante hendelser til Melosys-systemet. Tjenesten henter pensjonsgivende inntekt fra Sigrun-API og publiserer hendelser til Kafka for videre behandling.

## Hovedfunksjonalitet

### Skattehendelse-prosessering
- **Henter skattehendelser** fra Sigrun-API med konfigurerbar batch-størrelse
- **Validerer pensjonsgivende inntekt** mot eksisterende data
- **Publiserer hendelser** til Kafka-topic for downstream-systemer
- **Sporer sekvensummer** for å sikre at ingen hendelser går tapt

### Kafka-integrasjon
- **Consumer**: Mottar vedtakshendelser fra Melosys
- **Producer**: Publiserer skattehendelser til videre behandling
- **Duplikat-håndtering**: Forhindrer behandling av samme hendelse flere ganger

## Teknisk oversikt

### Arkitektur
- **Språk**: Kotlin
- **Rammeverk**: Spring Boot 3.3.5
- **Database**: PostgreSQL med Flyway-migrasjoner
- **Meldingskø**: Apache Kafka
- **Bygging**: Gradle med Kotlin DSL
- **JVM**: Java 17

### Sentrale komponenter

#### SkatteHendelsePublisering
Hovedorchestrerator for hendelsesprosessering:
- Asynkron behandling med jobbovervåking
- Duplikatdeteksjon og inntektsvalidering
- Robust feilhåndtering

#### SkatteHendelserCronjob
Periodisk jobb som kjører daglig kl. 02:00:
- Bruker ShedLock for distribuert låsing
- Starter hendelsesprosessering automatisk

#### VedtakHendelseConsumer
Kafka-consumer for vedtakshendelser:
- Håndterer person- og periodedata
- Lagrer relevante medlemskapsperioder

## Utvikling

### Forutsetninger
- Java 17 eller nyere
- Docker (for lokal PostgreSQL og Kafka)
- Gradle 8.x

### Lokal kjøring

1. **Klon repositoryet**:
   ```bash
   git clone https://github.com/navikt/melosys-skattehendelser.git
   cd melosys-skattehendelser
   ```

2. **Start lokale tjenester**:
   ```bash
   docker-compose up -d postgres kafka
   ```

3. **Bygg og start applikasjonen**:
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=local'
   ```

### Testing

```bash
# Kjør alle tester
./gradlew test
```

### Bygging

```bash
# Bygg JAR-fil
./gradlew bootJar

# Bygg Docker-image
docker build -t melosys-skattehendelser .
```

## Konfigurasjon

### Miljøvariabler

| Variabel | Beskrivelse | Standard |
|----------|-------------|----------|
| `DB_JDBC_URL` | PostgreSQL connection string | - |
| `DB_USERNAME` | Database brukernavn | - |
| `DB_PASSWORD` | Database passord | - |
| `KAFKA_BROKERS` | Kafka broker URL-er | - |
| `SIGRUN_REST_URL` | Sigrun API base URL | - |
| `CRON_JOB_PROSESSER_SKATTE_HENDELSER` | Cron-uttrykk for scheduled job | `0 0 2 * * *` |
| `SKATT_FETCHER_BATCH_SIZE` | Batch-størrelse for hendelseshenting | `500` |
| `DRY_RUN_PUBLISERING` | Tørrkjør publisering (for testing) | `false` |

### Profiler
- `local` - Lokal utvikling
- `test` - Testing
- `q1`/`q2` - Testmiljøer
- Standard profil for produksjon

## API-endepunkter

### Admin-endepunkter
- `GET /admin/status` - Status for hendelsesprosessering
- `POST /admin/stop` - Stopp pågående prosessering
- `GET /admin/personer` - Liste over personer i systemet

### Interne endepunkter
- `GET /internal/health` - Helsesjekk
- `GET /internal/prometheus` - Metrics
- `GET /internal/metrics` - Detaljerte metrics

## Deployment

### NAIS
Applikasjonen deployes til NAIS-plattformen med konfigurasjon i `nais/nais.yml`.

**Miljøer**:
- `dev-gcp` (Q1/Q2)
- `prod-gcp` (Produksjon)

### Secrets
Følgende secrets må konfigureres i NAIS:
- Database-tilkoblingsdetaljer
- Kafka-sertifikater
- Azure AD-konfigurasjon
- API-nøkler

## Overvåking og logging

### Metrics
- **Micrometer** med Prometheus-eksport
- **Custom metrics** for hendelsesbehandling
- **JVM metrics** for ytelsesovervåking

### Logging
- **Strukturert logging** med logstash-format
- **Korrelasjon-ID** for sporbarhet
- **Log-aggregering** til Elastic Stack

### Alerting
Alerts konfigureres basert på:
- Feilede hendelsesbehandlinger
- Database-tilkoblingsproblemer
- Kafka-consumer lag

## Bidrag

### Utviklingsretningslinjer
1. Følg Kotlin coding standards
2. Skriv tester for ny funksjonalitet
3. Oppdater dokumentasjon
4. Bruk feature branches og pull requests

### Commit-meldinger
Følg conventional commits format:
```
type(scope): beskrivelse

feat(kafka): legg til retry-logikk for consumer
fix(db): løs connection pool leak
docs(readme): oppdater API-dokumentasjon
```

## Feilsøking

### Vanlige problemer

**Database-tilkoblingsfeil**:
- Sjekk at PostgreSQL kjører
- Verifiser connection string og credentials

**Kafka-tilkoblingsproblemer**:
- Kontroller broker-URL-er
- Sjekk sertifikat-konfigurasjon

**Hendelser behandles ikke**:
- Sjekk cron-job konfigurasjon
- Verifiser at ShedLock ikke blokkerer

### Logger og debugging
- Øk log-nivå til `DEBUG` for detaljert logging
- Bruk `/internal/metrics` for å sjekke tilstand
- Overvåk Kafka consumer lag

## Team og kontakt

**Team**: Team Melosys
**Slack**: #team-melosys

## Lisens

MIT License - se `LICENSE` fil for detaljer.