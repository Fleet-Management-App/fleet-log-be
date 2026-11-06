package com.fleetlog.fixtures

import com.fleetlog.dto.VehicleCreateRequest
import com.fleetlog.dto.VehicleResponse
import com.fleetlog.entity.VehicleFuelType
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import java.time.LocalDate
import java.util.UUID

/** Registers vehicles through the real HTTP API. */
object Vehicles {

	fun uniquePlate() = "P-${UUID.randomUUID()}".take(20)

	fun uniqueVin() = "VIN-${UUID.randomUUID()}".take(32)

	fun vehicleRequest(
		plate: String = uniquePlate(),
		vin: String = uniqueVin(),
		stkDueDate: String? = null,
	) = VehicleCreateRequest(
		plate = plate,
		vin = vin,
		make = "Skoda",
		model = "Octavia",
		fuelType = VehicleFuelType.DIESEL,
		odometerKm = 10_000,
		stkDueDate = stkDueDate?.let(LocalDate::parse),
	)

	/** Registers a vehicle as [token]'s active organization's Fleet Manager. */
	fun createVehicle(
		token: String,
		plate: String = uniquePlate(),
		vin: String = uniqueVin(),
		stkDueDate: String? = null,
	): VehicleResponse =
		Given {
			withBearer(token)
			body(vehicleRequest(plate, vin, stkDueDate))
		} When {
			post("/api/vehicles")
		} Then {
			statusCode(201)
		} Extract {
			`as`(VehicleResponse::class.java)
		}

	fun createVehicleId(token: String, stkDueDate: String? = null): String =
		createVehicle(token, stkDueDate = stkDueDate).id.toString()

}
