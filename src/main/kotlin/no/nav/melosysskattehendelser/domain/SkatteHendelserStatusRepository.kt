package no.nav.melosysskattehendelser.domain

import org.springframework.data.repository.CrudRepository

interface SkatteHendelserStatusRepository : CrudRepository<SkatteHendelserSekvens, String>