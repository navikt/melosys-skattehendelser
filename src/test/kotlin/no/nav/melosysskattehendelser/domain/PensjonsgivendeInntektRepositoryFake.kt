package no.nav.melosysskattehendelser.domain

import java.util.*

class PensjonsgivendeInntektRepositoryFake : PensjonsgivendeInntektRepository {
    private val items = mutableMapOf<Long, PensjonsgivendeInntekt>()

    override fun <S : PensjonsgivendeInntekt?> save(entity: S & Any): S & Any {
        items[entity.id] = entity
        return entity
    }

    override fun <S : PensjonsgivendeInntekt?> saveAll(entities: MutableIterable<S>): MutableIterable<S> {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun findAll(): MutableIterable<PensjonsgivendeInntekt> = items.values

    override fun findAllById(ids: MutableIterable<Long>): MutableIterable<PensjonsgivendeInntekt> {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun count(): Long {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun delete(entity: PensjonsgivendeInntekt) {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun deleteAllById(ids: MutableIterable<Long>) {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun deleteAll(entities: MutableIterable<PensjonsgivendeInntekt>) {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun deleteAll() {
        items.clear()
    }

    override fun deleteById(id: Long) {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun existsById(id: Long): Boolean {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun findById(id: Long): Optional<PensjonsgivendeInntekt> {
        throw UnsupportedOperationException("Not yet implemented")
    }


}