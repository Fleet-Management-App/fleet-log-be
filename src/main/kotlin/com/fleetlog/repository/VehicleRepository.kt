package com.fleetlog.repository

import com.fleetlog.entity.Vehicle
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface VehicleRepository : JpaRepository<Vehicle, UUID> {

	fun findAllByOrganizationIdOrderByCreatedAt(organizationId: UUID): List<Vehicle>

	fun findByIdAndOrganizationId(id: UUID, organizationId: UUID): Vehicle?

	fun existsByOrganizationIdAndPlate(organizationId: UUID, plate: String): Boolean

	fun existsByOrganizationIdAndVin(organizationId: UUID, vin: String): Boolean

}
