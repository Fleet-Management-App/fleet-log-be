package com.fleetlog.repository

import com.fleetlog.entity.Trip
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TripRepository : JpaRepository<Trip, UUID> {

	fun findByIdAndOrganizationId(id: UUID, organizationId: UUID): Trip?

	/** Fleet Manager visibility: every trip in the tenant. */
	fun findAllByOrganizationIdOrderByCreatedAt(organizationId: UUID): List<Trip>

	/** Driver visibility: only their own trips. */
	fun findAllByOrganizationIdAndDriverIdOrderByCreatedAt(organizationId: UUID, driverId: UUID): List<Trip>

}
