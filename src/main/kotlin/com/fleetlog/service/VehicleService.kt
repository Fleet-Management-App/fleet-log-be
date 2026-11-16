package com.fleetlog.service

import com.fleetlog.dto.VehicleCreateRequest
import com.fleetlog.dto.VehicleResponse
import com.fleetlog.entity.Role
import com.fleetlog.entity.Vehicle
import com.fleetlog.exception.ConflictException
import com.fleetlog.mapper.VehicleMapper
import com.fleetlog.repository.OrganizationRepository
import com.fleetlog.repository.VehicleRepository
import com.fleetlog.security.CurrentUser
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Registering and listing the active organization's pool vehicles (SPEC-01). */
@Service
@Transactional
class VehicleService(
	private val currentUser: CurrentUser,
	private val vehicleRepository: VehicleRepository,
	private val organizationRepository: OrganizationRepository,
	private val vehicleMapper: VehicleMapper,
) {

	fun create(request: VehicleCreateRequest): VehicleResponse {
		val tenantId = currentUser.getTenantId()
		if (currentUser.getPrincipal().role != Role.FLEET_MANAGER) throw AccessDeniedException("Fleet Manager role required")
		if (vehicleRepository.existsByOrganizationIdAndPlate(tenantId, request.plate)) {
			throw ConflictException("A vehicle with this plate already exists")
		}
		if (vehicleRepository.existsByOrganizationIdAndVin(tenantId, request.vin)) {
			throw ConflictException("A vehicle with this VIN already exists")
		}
		val vehicle = vehicleRepository.save(
			Vehicle(
				organization = organizationRepository.getReferenceById(tenantId),
				plate = request.plate,
				vin = request.vin,
				make = request.make,
				model = request.model,
				fuelType = request.fuelType,
				odometerKm = request.odometerKm,
				stkDueDate = request.stkDueDate,
				insuranceDueDate = request.insuranceDueDate,
				vignetteDueDate = request.vignetteDueDate,
			),
		)
		return vehicleMapper.toResponse(vehicle)
	}

	@Transactional(readOnly = true)
	fun list(): List<VehicleResponse> =
		vehicleRepository.findAllByOrganizationIdOrderByCreatedAt(currentUser.getTenantId()).map(vehicleMapper::toResponse)

}
