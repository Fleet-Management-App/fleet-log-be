package com.fleetlog.fixtures

import com.fleetlog.dto.TripCreateRequest
import com.fleetlog.dto.TripFuelRequest
import com.fleetlog.dto.TripOdometerRequest
import com.fleetlog.dto.TripPlanDecisionRequest
import com.fleetlog.dto.TripResponse
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Plans trips and moves them through their lifecycle via the real HTTP API. */
object Trips {

	/** A 1x1 PNG as a data URL: a fuel entry always comes with a receipt (BR-14). */
	const val RECEIPT_PHOTO = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="

	/** A second, different receipt (the start of a JPEG), for replacing the first one. */
	const val OTHER_RECEIPT_PHOTO = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2w=="

	// Not named "body": inside a `Given { }` block that name resolves to RequestSpecification's own
	// member instead of this one, silently sending the wrong thing as the request body.
	fun tripBody(vehicleId: String) = TripCreateRequest(
		vehicleId = UUID.fromString(vehicleId),
		dateTime = Instant.now(),
		fromLocation = "Prague",
		toLocation = "Brno",
		purpose = "Delivery",
	)

	fun fuelBody(liters: String = "40.5", price: String = "55.2", receiptPhoto: String = RECEIPT_PHOTO) =
		TripFuelRequest(liters = BigDecimal(liters), price = BigDecimal(price), receiptPhoto = receiptPhoto)

	/** Plans a trip for [token]'s own account on [vehicleId]. */
	fun createTrip(token: String, vehicleId: String): TripResponse =
		Given { withBearer(token).body(tripBody(vehicleId)) } When {
			post("/api/trips")
		} Then { statusCode(201) } Extract { `as`(TripResponse::class.java) }

	fun createTripId(token: String, vehicleId: String): String = createTrip(token, vehicleId).id.toString()

	fun approvePlan(fleetManagerToken: String, tripId: String): TripResponse =
		Given { withBearer(fleetManagerToken).body(TripPlanDecisionRequest(approved = true)) } When {
			patch("/api/trips/$tripId/plan-approval")
		} Then { statusCode(200) } Extract { `as`(TripResponse::class.java) }

	fun logOdometer(token: String, tripId: String, startKm: Int, endKm: Int): TripResponse =
		Given { withBearer(token).body(TripOdometerRequest(odometerStartKm = startKm, odometerEndKm = endKm)) } When {
			patch("/api/trips/$tripId/odometer")
		} Then { statusCode(200) } Extract { `as`(TripResponse::class.java) }

	fun logFuel(token: String, tripId: String, request: TripFuelRequest = fuelBody()): TripResponse =
		Given { withBearer(token).body(request) } When {
			post("/api/trips/$tripId/fuel")
		} Then { statusCode(200) } Extract { `as`(TripResponse::class.java) }

}
