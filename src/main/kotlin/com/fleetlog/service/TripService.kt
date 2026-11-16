package com.fleetlog.service

import com.fleetlog.dto.TripCreateRequest
import com.fleetlog.dto.TripFuelDecisionRequest
import com.fleetlog.dto.TripFuelRequest
import com.fleetlog.dto.TripOdometerRequest
import com.fleetlog.dto.TripPlanDecisionRequest
import com.fleetlog.dto.TripResponse
import com.fleetlog.entity.FuelStatus
import com.fleetlog.entity.Role
import com.fleetlog.entity.Trip
import com.fleetlog.entity.TripStatus
import com.fleetlog.entity.Vehicle
import com.fleetlog.entity.VehicleStatus
import com.fleetlog.exception.BadRequestException
import com.fleetlog.exception.ConflictException
import com.fleetlog.exception.NotFoundException
import com.fleetlog.mapper.TripMapper
import com.fleetlog.repository.AppUserRepository
import com.fleetlog.repository.OrganizationRepository
import com.fleetlog.repository.TripRepository
import com.fleetlog.repository.VehicleRepository
import com.fleetlog.security.CurrentUser
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * Trip planning, plan approval, odometer logging and optional fuel logging/approval (SPEC-02).
 * An Accountant has no permissions here at all (SPEC-02 permission matrix).
 */
@Service
@Transactional
class TripService(
	private val currentUser: CurrentUser,
	private val tripRepository: TripRepository,
	private val vehicleRepository: VehicleRepository,
	private val organizationRepository: OrganizationRepository,
	private val userRepository: AppUserRepository,
	private val tripMapper: TripMapper,
) {

	/** The caller always plans/drives their own trip; the request carries no separate driver field. */
	fun create(request: TripCreateRequest): TripResponse {
		val tenantId = currentUser.getTenantId()
		requireDriverOrFleetManager()
		val trip = tripRepository.save(
			Trip(
				organization = organizationRepository.getReferenceById(tenantId),
				vehicle = resolveAvailableVehicle(tenantId, request.vehicleId),
				driver = userRepository.getReferenceById(currentUser.getUserId()),
				dateTime = request.dateTime,
				fromLocation = request.fromLocation,
				toLocation = request.toLocation,
				purpose = request.purpose,
			),
		)
		return tripMapper.toResponse(trip)
	}

	/** Edits and resubmits a plan-rejected trip's plan, incl. the vehicle choice (BR-07); returns it to PLANNED. */
	fun updatePlan(tripId: UUID, request: TripCreateRequest): TripResponse {
		val trip = findVisible(tripId)
		if (trip.status != TripStatus.PLAN_REJECTED) throw ConflictException("Only a plan-rejected trip can be edited")
		trip.vehicle = resolveAvailableVehicle(trip.organization.id, request.vehicleId)
		trip.dateTime = request.dateTime
		trip.fromLocation = request.fromLocation
		trip.toLocation = request.toLocation
		trip.purpose = request.purpose
		trip.status = TripStatus.PLANNED
		trip.rejectionReason = null
		return tripMapper.toResponse(trip)
	}

	@Transactional(readOnly = true)
	fun get(tripId: UUID): TripResponse = tripMapper.toResponse(findVisible(tripId))

	@Transactional(readOnly = true)
	fun list(status: TripStatus?): List<TripResponse> {
		val tenantId = currentUser.getTenantId()
		val trips = when (currentUser.getPrincipal().role) {
			Role.FLEET_MANAGER -> tripRepository.findAllByOrganizationIdOrderByCreatedAt(tenantId)
			Role.DRIVER -> tripRepository.findAllByOrganizationIdAndDriverIdOrderByCreatedAt(tenantId, currentUser.getUserId())
			Role.ACCOUNTANT, null -> throw AccessDeniedException("Not permitted to view trips")
		}
		return trips.filter { status == null || it.status == status }.map(tripMapper::toResponse)
	}

	/** Fleet Manager approves/rejects the plan, incl. the vehicle choice, before the trip happens. */
	fun decidePlan(tripId: UUID, request: TripPlanDecisionRequest): TripResponse {
		requireFleetManager()
		val trip = findInTenant(tripId)
		if (trip.status != TripStatus.PLANNED) throw ConflictException("Trip plan is not awaiting approval")
		if (request.approved) {
			trip.status = TripStatus.PLAN_APPROVED
			trip.rejectionReason = null
		} else {
			trip.status = TripStatus.PLAN_REJECTED
			trip.rejectionReason = request.rejectionReason
		}
		return tripMapper.toResponse(trip)
	}

	/** Completes the trip (BR-05); fuel, if any, is a separate concern and never blocks this. */
	fun logOdometer(tripId: UUID, request: TripOdometerRequest): TripResponse {
		val trip = findVisible(tripId)
		if (trip.status != TripStatus.PLAN_APPROVED) throw ConflictException("Trip plan has not been approved") // BR-03
		if (request.odometerEndKm < request.odometerStartKm) throw BadRequestException("Odometer end must be >= start") // BR-04
		trip.odometerStartKm = request.odometerStartKm
		trip.odometerEndKm = request.odometerEndKm
		trip.status = TripStatus.COMPLETED
		return tripMapper.toResponse(trip)
	}

	/** A rejected fuel entry can be resubmitted through this same endpoint (BR-07); a pending/approved one can't be overwritten. */
	fun logFuel(tripId: UUID, request: TripFuelRequest): TripResponse {
		val trip = findVisible(tripId)
		if (trip.fuelStatus == FuelStatus.PENDING || trip.fuelStatus == FuelStatus.APPROVED) {
			throw ConflictException("A fuel entry already exists for this trip")
		}
		trip.fuelLiters = request.liters
		trip.fuelPrice = request.price
		trip.fuelReceiptPhoto = request.receiptPhoto
		trip.fuelStatus = FuelStatus.PENDING
		trip.fuelRejectionReason = null
		return tripMapper.toResponse(trip)
	}

	/** Independent of the trip's own status (BR-06). */
	fun decideFuel(tripId: UUID, request: TripFuelDecisionRequest): TripResponse {
		requireFleetManager()
		val trip = findInTenant(tripId)
		if (trip.fuelStatus == null) throw ConflictException("No fuel entry to decide on")
		if (request.approved) {
			trip.fuelStatus = FuelStatus.APPROVED
			trip.fuelRejectionReason = null
		} else {
			trip.fuelStatus = FuelStatus.REJECTED
			trip.fuelRejectionReason = request.rejectionReason
		}
		return tripMapper.toResponse(trip)
	}

	fun cancel(tripId: UUID) {
		val trip = findVisible(tripId)
		if (trip.status != TripStatus.PLANNED) throw ConflictException("Only a still-planned trip can be cancelled")
		trip.status = TripStatus.CANCELLED
	}

	private fun findInTenant(tripId: UUID): Trip =
		tripRepository.findByIdAndOrganizationId(tripId, currentUser.getTenantId()) ?: throw NotFoundException("Trip not found")

	/** Driver sees/acts on own trips only; Fleet Manager on any trip in the tenant; Accountant never. */
	private fun findVisible(tripId: UUID): Trip {
		val trip = findInTenant(tripId)
		when (currentUser.getPrincipal().role) {
			Role.FLEET_MANAGER -> {}
			Role.DRIVER -> if (trip.driver.id != currentUser.getUserId()) throw AccessDeniedException("Not your trip")
			Role.ACCOUNTANT, null -> throw AccessDeniedException("Not permitted on trips")
		}
		return trip
	}

	private fun requireDriverOrFleetManager() {
		val role = currentUser.getPrincipal().role
		if (role != Role.DRIVER && role != Role.FLEET_MANAGER) throw AccessDeniedException("Not permitted on trips")
	}

	private fun requireFleetManager() {
		if (currentUser.getPrincipal().role != Role.FLEET_MANAGER) throw AccessDeniedException("Fleet Manager role required")
	}

	/** Looks the vehicle up in the tenant and enforces BR-01 (suspended or STK-expired vehicles can't be (re)selected). */
	private fun resolveAvailableVehicle(tenantId: UUID, vehicleId: UUID): Vehicle {
		val vehicle = vehicleRepository.findByIdAndOrganizationId(vehicleId, tenantId) ?: throw NotFoundException("Vehicle not found")
		if (vehicle.status == VehicleStatus.SUSPENDED || isStkExpired(vehicle)) {
			throw BadRequestException("Vehicle is suspended or its STK has expired")
		}
		return vehicle
	}

	private fun isStkExpired(vehicle: Vehicle): Boolean = vehicle.stkDueDate?.isBefore(LocalDate.now()) ?: false

}
