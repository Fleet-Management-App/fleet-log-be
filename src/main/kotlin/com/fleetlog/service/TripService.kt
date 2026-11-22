package com.fleetlog.service

import com.fleetlog.dto.ReceiptPhotoResponse
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
import com.fleetlog.exception.ErrorCode
import com.fleetlog.exception.NotFoundException
import com.fleetlog.mapper.TripMapper
import com.fleetlog.repository.AppUserRepository
import com.fleetlog.repository.FuelReceiptRepository
import com.fleetlog.repository.OrganizationRepository
import com.fleetlog.repository.TripRepository
import com.fleetlog.repository.VehicleRepository
import com.fleetlog.security.CurrentUser
import com.fleetlog.util.parseBase64DataUrl
import com.fleetlog.util.toBase64DataUrl
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.Base64
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
	private val fuelReceiptRepository: FuelReceiptRepository,
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
		if (trip.status != TripStatus.PLAN_REJECTED) throw ConflictException("Only a plan-rejected trip can be edited", ErrorCode.TRIP_NOT_PLAN_REJECTED)
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
		val principal = currentUser.getPrincipal()
		// A member holding several roles gets the widest view any of them grants.
		val trips = when {
			principal.hasRole(Role.FLEET_MANAGER) -> tripRepository.findAllByOrganizationIdOrderByCreatedAt(tenantId)
			principal.hasRole(Role.DRIVER) -> tripRepository.findAllByOrganizationIdAndDriverIdOrderByCreatedAt(tenantId, currentUser.getUserId())
			else -> throw AccessDeniedException("Not permitted to view trips")
		}
		return trips.filter { status == null || it.status == status }.map(tripMapper::toResponse)
	}

	/**
	 * Fleet Manager approves/rejects the plan, incl. the vehicle choice, before the trip happens. The
	 * vehicle is checked against BR-01 again on approval: it may have been suspended, or its STK may
	 * have run out, since the trip was planned.
	 */
	fun decidePlan(tripId: UUID, request: TripPlanDecisionRequest): TripResponse {
		requireFleetManager()
		val trip = findInTenant(tripId)
		if (trip.status != TripStatus.PLANNED) throw ConflictException("Trip plan is not awaiting approval", ErrorCode.TRIP_NOT_PLANNED)
		if (request.approved) {
			requireAvailable(trip.vehicle, field = null)
			trip.status = TripStatus.PLAN_APPROVED
			trip.rejectionReason = null
		} else {
			trip.rejectionReason = requireRejectionReason(request.rejectionReason)
			trip.status = TripStatus.PLAN_REJECTED
		}
		return tripMapper.toResponse(trip)
	}

	/** Completes the trip (BR-05); fuel, if any, is a separate concern and never blocks this. */
	fun logOdometer(tripId: UUID, request: TripOdometerRequest): TripResponse {
		val trip = findVisible(tripId)
		if (trip.status != TripStatus.PLAN_APPROVED) throw ConflictException("Trip plan has not been approved", ErrorCode.TRIP_NOT_PLAN_APPROVED) // BR-03
		if (request.odometerEndKm < request.odometerStartKm) throw BadRequestException("Odometer end must be >= start", ErrorCode.ODOMETER_END_BEFORE_START, "odometerEndKm") // BR-04
		trip.odometerStartKm = request.odometerStartKm
		trip.odometerEndKm = request.odometerEndKm
		trip.status = TripStatus.COMPLETED
		// The vehicle's reading is what the next trip is compared against (BR-10/11). Trips may be
		// logged out of order, so an older trip's lower reading must never wind it back.
		trip.vehicle.odometerKm = maxOf(trip.vehicle.odometerKm, request.odometerEndKm)
		return tripMapper.toResponse(trip)
	}

	/**
	 * Fuel can be logged once the plan is approved, before or after the odometer (BR-03). A rejected
	 * entry can be resubmitted through this same endpoint (BR-07); a pending/approved one can't be
	 * overwritten. A resubmission replaces the receipt photo too.
	 */
	fun logFuel(tripId: UUID, request: TripFuelRequest): TripResponse {
		val trip = findVisible(tripId)
		if (trip.status != TripStatus.PLAN_APPROVED && trip.status != TripStatus.COMPLETED) {
			throw ConflictException("Trip plan has not been approved", ErrorCode.TRIP_NOT_PLAN_APPROVED)
		}
		if (trip.fuelStatus == FuelStatus.PENDING || trip.fuelStatus == FuelStatus.APPROVED) {
			throw ConflictException("A fuel entry already exists for this trip", ErrorCode.FUEL_ENTRY_ALREADY_EXISTS)
		}
		val (contentType, photo) = decodeReceiptPhoto(request.receiptPhoto)
		fuelReceiptRepository.saveOrReplace(trip.id, contentType, photo)
		trip.hasFuelReceipt = true
		trip.fuelLiters = request.liters
		trip.fuelPrice = request.price
		trip.fuelStatus = FuelStatus.PENDING
		trip.fuelRejectionReason = null
		return tripMapper.toResponse(trip)
	}

	/** Independent of the trip's own status (BR-06); only a PENDING entry can be decided. */
	fun decideFuel(tripId: UUID, request: TripFuelDecisionRequest): TripResponse {
		requireFleetManager()
		val trip = findInTenant(tripId)
		when (trip.fuelStatus) {
			null -> throw ConflictException("No fuel entry to decide on", ErrorCode.NO_FUEL_ENTRY)
			FuelStatus.PENDING -> {}
			else -> throw ConflictException("The fuel entry has already been decided", ErrorCode.FUEL_ENTRY_NOT_PENDING)
		}
		if (request.approved) {
			trip.fuelStatus = FuelStatus.APPROVED
			trip.fuelRejectionReason = null
		} else {
			trip.fuelRejectionReason = requireRejectionReason(request.rejectionReason)
			trip.fuelStatus = FuelStatus.REJECTED
		}
		return tripMapper.toResponse(trip)
	}

	/** The fuel receipt photo, kept out of [TripResponse] because of its size; same visibility as [get]. */
	@Transactional(readOnly = true)
	fun getFuelReceipt(tripId: UUID): ReceiptPhotoResponse {
		val trip = findVisible(tripId)
		val receipt = fuelReceiptRepository.findByIdOrNull(trip.id)
			?: throw NotFoundException("This trip has no fuel receipt", ErrorCode.FUEL_RECEIPT_NOT_FOUND)
		return ReceiptPhotoResponse(toBase64DataUrl(receipt.contentType, receipt.photo))
	}

	fun cancel(tripId: UUID) {
		val trip = findVisible(tripId)
		if (trip.status != TripStatus.PLANNED) throw ConflictException("Only a still-planned trip can be cancelled", ErrorCode.TRIP_NOT_PLANNED)
		trip.status = TripStatus.CANCELLED
	}

	private fun findInTenant(tripId: UUID): Trip =
		tripRepository.findByIdAndOrganizationId(tripId, currentUser.getTenantId()) ?: throw NotFoundException("Trip not found", ErrorCode.TRIP_NOT_FOUND)

	/** Driver sees/acts on own trips only; Fleet Manager on any trip in the tenant; Accountant never. */
	private fun findVisible(tripId: UUID): Trip {
		val trip = findInTenant(tripId)
		val principal = currentUser.getPrincipal()
		when {
			principal.hasRole(Role.FLEET_MANAGER) -> {}
			principal.hasRole(Role.DRIVER) -> if (trip.driver.id != currentUser.getUserId()) throw AccessDeniedException("Not your trip")
			else -> throw AccessDeniedException("Not permitted on trips")
		}
		return trip
	}

	private fun requireDriverOrFleetManager() {
		val principal = currentUser.getPrincipal()
		if (!principal.hasRole(Role.DRIVER) && !principal.hasRole(Role.FLEET_MANAGER)) throw AccessDeniedException("Not permitted on trips")
	}

	private fun requireFleetManager() {
		if (!currentUser.getPrincipal().hasRole(Role.FLEET_MANAGER)) throw AccessDeniedException("Fleet Manager role required")
	}

	/** Looks the vehicle up in the tenant and enforces BR-01 on it. */
	private fun resolveAvailableVehicle(tenantId: UUID, vehicleId: UUID): Vehicle {
		val vehicle = vehicleRepository.findByIdAndOrganizationId(vehicleId, tenantId)
			?: throw NotFoundException("Vehicle not found", ErrorCode.VEHICLE_NOT_FOUND, "vehicleId")
		return requireAvailable(vehicle, field = "vehicleId")
	}

	/** BR-01: a suspended or STK-expired vehicle can't be selected for, or approved on, a trip. */
	private fun requireAvailable(vehicle: Vehicle, field: String?): Vehicle {
		if (vehicle.status == VehicleStatus.SUSPENDED || isStkExpired(vehicle)) {
			throw BadRequestException("Vehicle is suspended or its STK has expired", ErrorCode.VEHICLE_UNAVAILABLE, field)
		}
		return vehicle
	}

	/** A rejection (plan or fuel) must tell the driver why, so they can fix and resubmit it (BR-07). */
	private fun requireRejectionReason(rejectionReason: String?): String =
		rejectionReason?.trim()?.takeIf { it.isNotEmpty() }
			?: throw BadRequestException("A rejection needs a reason", ErrorCode.REJECTION_REASON_REQUIRED, "rejectionReason")

	private fun isStkExpired(vehicle: Vehicle): Boolean = vehicle.stkDueDate?.isBefore(LocalDate.now()) ?: false

	/**
	 * The receipt photo (BR-14) comes as a base64 data URL; it is stored as its media type plus the decoded
	 * bytes. Only an accepted image type that decodes to 1 byte .. [MAX_RECEIPT_PHOTO_BYTES] is taken. The
	 * size is checked before decoding, so an oversized upload is never decoded.
	 */
	private fun decodeReceiptPhoto(receiptPhoto: String): Pair<String, ByteArray> {
		val dataUrl = parseBase64DataUrl(receiptPhoto)
		if (dataUrl == null || dataUrl.mediaType !in RECEIPT_PHOTO_TYPES) {
			throw BadRequestException("The receipt photo must be a JPEG, PNG, WebP or HEIC/HEIF image as a base64 data URL", ErrorCode.RECEIPT_PHOTO_INVALID, RECEIPT_PHOTO_FIELD)
		}
		if (dataUrl.getDecodedSize() > MAX_RECEIPT_PHOTO_BYTES) {
			throw BadRequestException("The receipt photo is larger than $MAX_RECEIPT_PHOTO_BYTES bytes", ErrorCode.RECEIPT_PHOTO_TOO_LARGE, RECEIPT_PHOTO_FIELD)
		}
		val photo = try {
			Base64.getDecoder().decode(dataUrl.base64)
		} catch (_: IllegalArgumentException) {
			null
		}
		if (photo == null || photo.isEmpty()) throw BadRequestException("The receipt photo is empty or isn't valid base64", ErrorCode.RECEIPT_PHOTO_INVALID, RECEIPT_PHOTO_FIELD)
		return dataUrl.mediaType to photo
	}

	companion object {

		/** The largest receipt photo accepted, decoded: 5 MiB. */
		const val MAX_RECEIPT_PHOTO_BYTES = 5 * 1024 * 1024

		/** What phone cameras and browsers produce; HEIC/HEIF is the iPhone camera's default. */
		val RECEIPT_PHOTO_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")

		private const val RECEIPT_PHOTO_FIELD = "receiptPhoto"

	}

}
