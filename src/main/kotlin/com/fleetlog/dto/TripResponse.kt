package com.fleetlog.dto

import com.fleetlog.entity.TripStatus
import java.time.Instant
import java.util.UUID

data class TripResponse(
	val id: UUID,
	val vehicleId: UUID,
	val dateTime: Instant,
	val fromLocation: String,
	val toLocation: String,
	val purpose: String,
	val status: TripStatus,
	val rejectionReason: String?,
	val odometerStartKm: Int?,
	val odometerEndKm: Int?,
	val fuel: FuelEntryResponse?,
	val createdAt: Instant,
)
