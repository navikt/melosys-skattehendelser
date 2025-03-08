package no.nav.melosysskattehendelser.melosys.consumer

import mu.KotlinLogging
import no.nav.melosysskattehendelser.domain.Person
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.metrics.Metrikker
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger { }

open class VedtakHendelseConsumer(
    private val vedtakHendelseRepository: PersonRepository,
    private val metrikker: Metrikker
) {
    @KafkaListener(
        id = LISTENER_ID,
        clientIdPrefix = "melosys-vedtak-mottatt",
        topics = ["\${melosys.kafka.consumer.topic}"],
        containerFactory = "melosysVedtakListenerContainerFactory",
        groupId = "\${melosys.kafka.consumer.groupId}"
    )
    @Transactional
    open fun vedtakHendelseConsumer(consumerRecord: ConsumerRecord<String, MelosysHendelse>) {
        val vedtakHendelseMelding = validerOgHentVedtakshendelse(consumerRecord) ?: return

        try {
            leggTilPersonOgEllerPeriode(vedtakHendelseMelding)
        } catch (e: Exception) {
            log.error(e) { "Feil ved konsumering av vedtaksmelding: $consumerRecord" }
            throw e
        }
    }

    private fun validerOgHentVedtakshendelse(consumerRecord: ConsumerRecord<String, MelosysHendelse>): VedtakHendelseMelding? {
        val vedtakHendelseMelding = consumerRecord.value().melding as? VedtakHendelseMelding
            ?: return logAndIgnore("Ignorerer melding av type ${consumerRecord.value().melding.javaClass.simpleName}")

        return vedtakHendelseMelding.takeIf { it.sakstype == Sakstyper.FTRL }
            ?.also { log.info("Mottatt vedtakshendelse sakstype: ${it.sakstype}, sakstema: ${it.sakstema}") }
            ?.takeIf { it.medlemskapsperioder.any { periode -> periode.erGyldig() } }
            ?: logAndIgnore("Ingen gyldige medlemskapsperioder i melding, så lager ikke bruker i databasen")
    }

    private fun logAndIgnore(message: String): VedtakHendelseMelding? {
        log.debug { message }
        return null
    }

    private fun leggTilPersonOgEllerPeriode(vedtakHendelseMelding: VedtakHendelseMelding) {
        val person = hentEllerLagPerson(vedtakHendelseMelding)

        vedtakHendelseMelding.medlemskapsperioder
            .filter { it.erGyldig() }
            .filterNot { person.harPeriode(it) { log.info("perioden $it finnes allerede på person med id:${person.id}") } }
            .forEach {
                log.info("legger til $it på person med id: ${person.id}")
                metrikker.vedtakMottattOgPeriodeLagtTil()
                person.leggTilPeriode(it)
            }
        vedtakHendelseRepository.save(person)
    }

    private fun hentEllerLagPerson(vedtakHendelseMelding: VedtakHendelseMelding): Person =
        vedtakHendelseRepository.findPersonByIdent(vedtakHendelseMelding.folkeregisterIdent)?.also {
            log.info("person med ident(${vedtakHendelseMelding.folkeregisterIdent}) finnes allerede")
        } ?: run {
            metrikker.vedtakMottattOgPersonLagtTil()
            Person(ident = vedtakHendelseMelding.folkeregisterIdent)
        }

    private fun Person.harPeriode(periode: Periode, block: () -> Unit) = perioder.any {
        it.fom == periode.fom && it.tom == periode.tom
    }.also { if (it) block() }

    private fun Person.leggTilPeriode(periode: Periode) {
        perioder.add(
            no.nav.melosysskattehendelser.domain.Periode(
                fom = periode.fom ?: throw IllegalArgumentException("fom kan ikke være null"),
                tom = periode.tom ?: throw IllegalArgumentException("tom kan ikke være null"),
                person = this
            )
        )
    }

    companion object {
        const val LISTENER_ID = "melosysVedtakMottatt"
    }
}
