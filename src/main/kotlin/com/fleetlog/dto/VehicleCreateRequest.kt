package com.fleetlog.dto

import com.fleetlog.entity.VehicleFuelType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class VehicleCreateRequest(
	@field:NotBlank @field:Size(max = 20)
	val plate: String,

	@field:NotBlank @field:Size(max = 32)
	val vin: String,

	@field:NotBlank @field:Size(max = 100)
	val make: String,

	@field:NotBlank @field:Size(max = 100)
	val model: String,

	@field:NotNull
	val fuelType: VehicleFuelType,

	@field:NotNull @field:PositiveOrZero
	val odometerKm: Int,

	val stkDueDate: LocalDate? = null,
	val insuranceDueDate: LocalDate? = null,
	val vignetteDueDate: LocalDate? = null,
)
