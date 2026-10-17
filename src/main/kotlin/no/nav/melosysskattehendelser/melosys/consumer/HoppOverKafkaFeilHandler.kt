package no.nav.melosysskattehendelser.melosys.consumer

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.RecordDeserializationException
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger { }

/**
 * Stopper lytteren ved feil, som [CommonContainerStoppingErrorHandler], og husker meldingen som feilet.
 * Lytteren leser en melding som er merket med [merkForÅHoppeOver], på nytt når den starter. Feiler meldingen
 * igjen, committes offset + 1, og lytteren fortsetter med neste. Går den gjennom, er den behandlet som vanlig.
 *
 * Lagrer ikke innholdet i meldingen, bare hvor den ligger og feilkjeden.
 */
@Component
class HoppOverKafkaFeilHandler : CommonContainerStoppingErrorHandler() {

    private val feiledeMeldinger = ConcurrentHashMap<String, FeiletMelding>()
    private val skalHoppesOver = ConcurrentHashMap.newKeySet<String>()

    fun feiledeMeldinger(): Map<String, FeiletMelding> = feiledeMeldinger.toSortedMap()

    /** Gir false når ingen melding med nøkkelen har feilet. */
    fun merkForÅHoppeOver(nøkkel: String): Boolean {
        if (feiledeMeldinger.remove(nøkkel) == null) return false
        skalHoppesOver.add(nøkkel)
        return true
    }

    /** Gir false når ingen melding med nøkkelen har feilet. */
    fun fjern(nøkkel: String): Boolean {
        skalHoppesOver.remove(nøkkel)
        return feiledeMeldinger.remove(nøkkel) != null
    }

    override fun handleRemaining(
        thrownException: Exception,
        records: MutableList<ConsumerRecord<*, *>>,
        consumer: Consumer<*, *>,
        container: MessageListenerContainer
    ) {
        val record = records.firstOrNull()
        if (record != null) {
            val topicPartition = TopicPartition(record.topic(), record.partition())
            if (hoppOverHvisMerket(consumer, topicPartition, record.offset())) {
                seekTilResten(consumer, records.drop(1))
                return
            }
            lagre(topicPartition, record.offset(), thrownException)
        }
        super.handleRemaining(thrownException, records, consumer, container)
    }

    override fun handleOtherException(
        thrownException: Exception,
        consumer: Consumer<*, *>,
        container: MessageListenerContainer,
        batchListener: Boolean
    ) {
        if (thrownException is RecordDeserializationException) {
            val topicPartition = thrownException.topicPartition()
            if (hoppOverHvisMerket(consumer, topicPartition, thrownException.offset())) return
            lagre(topicPartition, thrownException.offset(), thrownException)
        }
        super.handleOtherException(thrownException, consumer, container, batchListener)
    }

    private fun hoppOverHvisMerket(consumer: Consumer<*, *>, topicPartition: TopicPartition, offset: Long): Boolean {
        val nøkkel = nøkkel(topicPartition, offset)
        if (!skalHoppesOver.remove(nøkkel)) return false

        return try {
            consumer.commitSync(mapOf(topicPartition to OffsetAndMetadata(offset + 1)))
            consumer.seek(topicPartition, offset + 1)
            log.warn { "Hoppet over melding $nøkkel" }
            true
        } catch (e: Exception) {
            // Lytteren stopper, og feilen vises igjen, så utvikleren kan prøve å hoppe over på nytt
            log.error(e) { "Klarte ikke å hoppe over melding $nøkkel" }
            false
        }
    }

    // Meldingene etter den som ble hoppet over, er hentet, men ikke lest. Uten seek ville de gått tapt.
    private fun seekTilResten(consumer: Consumer<*, *>, records: List<ConsumerRecord<*, *>>) {
        records.groupBy { TopicPartition(it.topic(), it.partition()) }
            .forEach { (topicPartition, recordsIPartisjon) ->
                consumer.seek(topicPartition, recordsIPartisjon.minOf { it.offset() })
            }
    }

    private fun lagre(topicPartition: TopicPartition, offset: Long, feil: Exception) {
        val nøkkel = nøkkel(topicPartition, offset)
        feiledeMeldinger[nøkkel] = FeiletMelding(
            topic = topicPartition.topic(),
            partisjon = topicPartition.partition(),
            offset = offset,
            tidspunkt = Instant.now(),
            feilkjede = feilkjede(feil)
        )
        log.error { "Lytteren stopper på melding $nøkkel. Se GET /admin/kafka/errors" }
    }

    private fun feilkjede(feil: Throwable): List<String> =
        generateSequence(feil) { it.cause?.takeIf { årsak -> årsak !== it } }
            .take(MAKS_FEILKJEDE)
            .map { "${it.javaClass.simpleName}: ${it.message?.let(::maskerFødselsnummer)}" }
            .toList()

    // Feilmeldinger kan sitere meldingen, og endepunktet skal aldri gi ut fødselsnummer.
    // Maskerer alle tall på 11 siffer eller mer, også når fødselsnummeret står inntil et annet tall.
    private fun maskerFødselsnummer(tekst: String) = ELLEVE_SIFFER.replace(tekst, "***********")

    data class FeiletMelding(
        val topic: String,
        val partisjon: Int,
        val offset: Long,
        val tidspunkt: Instant,
        val feilkjede: List<String>
    )

    companion object {
        private const val MAKS_FEILKJEDE = 10
        private val ELLEVE_SIFFER = Regex("""\d{11,}""")

        fun nøkkel(topicPartition: TopicPartition, offset: Long) =
            "${topicPartition.topic()}-${topicPartition.partition()}-$offset"
    }
}
