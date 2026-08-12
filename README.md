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
- **JVM**: Java 21

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
- Java 21 eller nyere
- Docker (for lokal PostgreSQL og Kafka)
- Gradle 8.x

### Lokal kjøring

1. **Klon repositoryet**:
   ```bash
   git clone https://github.com/navikt/melosys-skattehendelser.git
   cd melosys-skattehendelser
   ```

2. **Start lokale tjenester** (kjøres i `melosys-docker-compose`):
   ```bash
   docker compose up -d postgres kafka mock-oauth2-server mock-oauth2-login-proxy melosys-mock
   ```

   Alle fem trengs. `mock-oauth2-server` publiserer ikke port 8082 til host –
   det er `mock-oauth2-login-proxy` som gjør det. Uten den feiler oppstart med
   `MetaDataNotAvailableException: Could not retrieve metadata from url:
   http://host.docker.internal:8082/isso/.well-known/openid-configuration`.

3. **Bygg og start applikasjonen**:
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=local'
   ```

   Applikasjonen kjører på port **8089**. Helsesjekk: `http://localhost:8089/internal/health`.

   **Profiler:** `local`, `q2` og `ske` er de kjørbare profilene (`test` finnes også,
   men brukes kun av testkjøring). Alle tre kjøres lokalt (mot lokal Postgres/Kafka og
   lokal mock-oauth2); `q2` og `ske` peker Sigrun mot henholdsvis sigrun-q2 og sigrun-ske.
   I NAIS brukes default-profilen, der alle `AZURE_*`-variabler injiseres av plattformen.

   Profilnavn kombineres ikke med bindestrek – `local-q2` er ett enkelt (ikke-eksisterende)
   profilnavn, ikke «local + q2». Da lastes kun `application.yaml`, og oppstart feiler med
   en uløst placeholder (f.eks. `Could not resolve placeholder 'SIGRUN_REST_URL'`).

   Bruk `q2` eller `ske` **alene** – ikke `local,q2`. `application-local.yaml` setter
   `sigrun.rest.url` direkte, mens q2/ske setter `SIGRUN_REST_URL` som `application.yaml`
   interpolerer. Den direkte verdien vinner uansett profilrekkefølge, så `local,q2` henter
   et ekte Azure-token for sigrun-q2 og kaller deretter den lokale mocken.

   Skal du kalle ekte Sigrun i q2/ske, sett `AZURE_APP_CLIENT_ID` og `AZURE_APP_CLIENT_SECRET`
   som miljøvariabler – de overstyrer profilfila. Verdiene må være den ekte Azure-appen til
   melosys-skattehendelser (den som har tilgangsrolle på `api://dev-fss.team-inntekt.sigrun-q2`);
   `melosys-localhost` finnes kun i lokal mock-oauth2 og gir
   `AADSTS501051: Application 'melosys-localhost' is not assigned to a role`.
   Spør i teamkanalen, eller hent dem fra dev-clusteret – pass på at konteksten er `dev-gcp`,
   ikke prod:
   ```bash
   kubectl --context dev-gcp -n teammelosys exec deploy/melosys-skattehendelser \
     -- printenv AZURE_APP_CLIENT_ID AZURE_APP_CLIENT_SECRET
   ```

   Innkommende JWT valideres mot `AZURE_APP_ACCEPTED_AUDIENCE` (defaulter til
   `AZURE_APP_CLIENT_ID` når den ikke er satt, slik NAIS forventer). Lokalt står den til
   `melosys-localhost` i profilfilene, så admin-kall under fortsetter å virke selv når
   `AZURE_APP_CLIENT_ID` overstyres med ekte Azure-app.

   Pass på at du **ikke** har `AZURE_APP_WELL_KNOWN_URL` satt til en ikke-URL (f.eks. `dummy`)
   i run-konfigurasjonen; det gir samme bindingsfeil.

4. **Kall admin-endepunkter lokalt** – krever både JWT og API-nøkkel:
   ```bash
   TOKEN=$(curl -s -X POST http://host.docker.internal:8082/isso/token \
     -d grant_type=client_credentials -d client_id=melosys-localhost \
     -d client_secret=lol -d scope=melosys-localhost | jq -r .access_token)

   curl -H "Authorization: Bearer $TOKEN" \
        -H "X-SKATTEHENDELSER-ADMIN-APIKEY: dummy" \
        http://localhost:8089/admin/hendelseprosessering/status
   ```

   Merk: token må hentes fra `/isso/token`, ikke `/isso/oauth2/v2.0/token` –
   sistnevnte gir en issuer som ikke matcher discovery-dokumentet, og gir 401.
   `scope` må matche `AZURE_APP_ACCEPTED_AUDIENCE`; ellers får du
   `JWT audience rejected: [melosys-localhost]` i loggen og 401 fra endepunktet.

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

| Variabel | Beskrivelse                          | Standard      |
|----------|--------------------------------------|---------------|
| `DB_JDBC_URL` | PostgreSQL connection string         | -             |
| `DB_USERNAME` | Database brukernavn                  | -             |
| `DB_PASSWORD` | Database passord                     | -             |
| `KAFKA_BROKERS` | Kafka broker URL-er                  | -             |
| `SIGRUN_REST_URL` | Sigrun API base URL                  | -             |
| `CRON_JOB_PROSESSER_SKATTE_HENDELSER` | Cron-uttrykk for scheduled job       | `0 0 2 * * *` |
| `SKATT_FETCHER_BATCH_SIZE` | Batch-størrelse for hendelseshenting | `500`         |
| `DRY_RUN_PUBLISERING` | Tørrkjør publisering (for testing)   | `false`       |
| `X-SKATTEHENDELSER-ADMIN-APIKEY` | for tilgang til admin endepunker     | -             |

### Profiler
- `local` - Lokal utvikling
- `test` - Testing
- `q1`/`q2` - Testmiljøer
- Standard profil for produksjon

## API-endepunkter

### Admin-endepunkter
- `GET /admin/status` - Status for hendelsesprosessering
- `POST /admin/start` - Start hendelsesprosessering
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

## Overvåking og logging

### Metrics
- **Micrometer** med Prometheus-eksport
- **Custom metrics** for hendelsesbehandling
- **JVM metrics** for ytelsesovervåking
- grafana-dashboards for visualisering: [melosys-skattehendelser-dashboards](grafana.nav.cloud.nais.io/d/aedqb9of1fzswd/skattehendelser?orgId=1&from=now-7d&to=now&timezone=browser&var-cluster=000000021&var-fss=000000011)

### Logging
- **Strukturert logging** med logstash-format
- **Korrelasjon-ID** for sporbarhet
- **Log-aggregering** til Elastic Stack

### Alerting
Alerts konfigureres basert på:
- Feilede hendelsesbehandlinger
- Database-tilkoblingsproblemer
- Kafka-consumer lag

## Team og kontakt

**Team**: Team Melosys
**Slack**: #team-melosys
