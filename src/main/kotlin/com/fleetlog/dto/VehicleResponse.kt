package com.fleetlog.dto

import com.fleetlog.entity.VehicleFuelType
import com.fleetlog.entity.VehicleStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class VehicleResponse(
	val id: UUID,
	val plate: String,
	val vin: String,
	val make: String,
	val model: String,
	val fuelType: VehicleFuelType,
	val odometerKm: Int,
	val status: VehicleStatus,
	val stkDueDate: LocalDate?,
	val insuranceDueDate: LocalDate?,
	val vignetteDueDate: LocalDate?,
	val createdAt: Instant,
)
