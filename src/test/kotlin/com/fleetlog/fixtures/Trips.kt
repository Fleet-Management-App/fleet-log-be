package com.fleetlog.fixtures

import com.fleetlog.dto.TripCreateRequest
import com.fleetlog.dto.TripResponse
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import java.time.Instant
import java.util.UUID

/** Plans trips through the real HTTP API. */
object Trips {

	// Not named "body": inside a `Given { }` block that name resolves to RequestSpecification's own
	// member instead of this one, silently sending the wrong thing as the request body.
	fun tripBody(vehicleId: String) = TripCreateRequest(
		vehicleId = UUID.fromString(vehicleId),
		dateTime = Instant.now(),
		fromLocation = "Prague",
		toLocation = "Brno",
		purpose = "Delivery",
	)

	/** Plans a trip for [token]'s own account on [vehicleId]. */
	fun createTrip(token: String, vehicleId: String): TripResponse =
		Given { withBearer(token).body(tripBody(vehicleId)) } When {
			post("/api/trips")
		} Then { statusCode(201) } Extract { `as`(TripResponse::class.java) }

	fun createTripId(token: String, vehicleId: String): String = createTrip(token, vehicleId).id.toString()

}
