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
        id = "melosysVedtakMottatt",
        clientIdPrefix = "melosys-vedtak-mottatt",
        topics = ["\${melosys.kafka.consumer.topic}"],
        containerFactory = "melosysVedtakListenerContainerFactory",
        groupId = "\${melosys.kafka.consumer.groupId}"
    )
    @Transactional
    open fun vedtakHendelseConsumer(consumerRecord: ConsumerRecord<String, MelosysHendelse>) {
        val melding = consumerRecord.value().melding
        val vedtakHendelseMelding = melding as? VedtakHendelseMelding
            ?: return log.debug { "Ignorerer melding av type ${melding.javaClass.simpleName} " }

        if (vedtakHendelseMelding.sakstype != Sakstyper.FTRL) {
            return log.debug { "Ignorerer melding med sakstype ${vedtakHendelseMelding.sakstype} " }
        }

        log.info("Mottatt vedtakshendelse sakstype: ${vedtakHendelseMelding.sakstype} sakstema: ${vedtakHendelseMelding.sakstema}")
        if (vedtakHendelseMelding.medlemskapsperioder.none { it.erGyldig() }) {
            return log.info { "Ingen gyldige medlemskapsperioder i melding, så lager ikke bruker i databasen" }
        }

        try {
            leggTilPersonOgEllerPeriode(vedtakHendelseMelding)
        } catch (e: Exception) {
            log.error(e) { "Feil ved konsumering av vedtaksmelding: $consumerRecord" }
            throw e
        }
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
}
