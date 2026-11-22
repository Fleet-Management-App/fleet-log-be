package com.fleetlog.repository

import com.fleetlog.entity.Vehicle
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface VehicleRepository : JpaRepository<Vehicle, UUID> {

	fun findAllByOrganizationIdOrderByCreatedAt(organizationId: UUID): List<Vehicle>

	fun findByIdAndOrganizationId(id: UUID, organizationId: UUID): Vehicle?

	/** Ignores spaces and case, the same expression the unique index in V4 is built on. */
	@Query(
		"""
		select case when count(v) > 0 then true else false end from Vehicle v
		where v.organization.id = :organizationId and upper(replace(v.plate, ' ', '')) = upper(replace(:plate, ' ', ''))
		""",
	)
	fun existsByOrganizationIdAndPlateIgnoringSpaces(organizationId: UUID, plate: String): Boolean

	fun existsByOrganizationIdAndVin(organizationId: UUID, vin: String): Boolean

}
