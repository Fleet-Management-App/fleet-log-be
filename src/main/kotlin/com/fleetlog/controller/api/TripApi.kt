package com.fleetlog.controller.api

import com.fleetlog.dto.TripCreateRequest
import com.fleetlog.dto.TripFuelDecisionRequest
import com.fleetlog.dto.TripFuelRequest
import com.fleetlog.dto.TripOdometerRequest
import com.fleetlog.dto.TripPlanDecisionRequest
import com.fleetlog.dto.TripResponse
import com.fleetlog.entity.TripStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import java.util.UUID

@Tag(name = "Trips", description = "Trip planning, approval, odometer and fuel logging (SPEC-02)")
@RequestMapping("/api/trips")
interface TripApi {

	@Operation(summary = "Plan a trip, picking a pool vehicle")
	@ApiResponse(responseCode = "201")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	fun createTrip(@Valid @RequestBody request: TripCreateRequest): TripResponse

	@Operation(summary = "Trip detail; a Driver only sees their own, a Fleet Manager sees all in the tenant")
	@GetMapping("/{tripId}")
	fun getTrip(@PathVariable tripId: UUID): TripResponse

	@Operation(summary = "List trips, optionally filtered by status; same visibility as the detail view")
	@GetMapping
	fun listTrips(@RequestParam(required = false) status: TripStatus?): List<TripResponse>

	@Operation(summary = "Fleet Manager approves or rejects the trip plan, incl. the vehicle choice")
	@PatchMapping("/{tripId}/plan-approval")
	fun decideTripPlan(@PathVariable tripId: UUID, @Valid @RequestBody request: TripPlanDecisionRequest): TripResponse

	@Operation(summary = "Edit and resubmit a plan-rejected trip's plan (vehicle, date/time, route, purpose); returns it to PLANNED")
	@PatchMapping("/{tripId}/plan")
	fun updateTripPlan(@PathVariable tripId: UUID, @Valid @RequestBody request: TripCreateRequest): TripResponse

	@Operation(summary = "Log odometer start/end on a plan-approved trip; completes it")
	@PatchMapping("/{tripId}/odometer")
	fun logOdometer(@PathVariable tripId: UUID, @Valid @RequestBody request: TripOdometerRequest): TripResponse

	@Operation(summary = "Log (or, if rejected, resubmit) the trip's optional fuel entry")
	@PostMapping("/{tripId}/fuel")
	fun logFuel(@PathVariable tripId: UUID, @Valid @RequestBody request: TripFuelRequest): TripResponse

	@Operation(summary = "Fleet Manager approves or rejects the fuel entry, independent of trip completion")
	@PatchMapping("/{tripId}/fuel-approval")
	fun decideFuel(@PathVariable tripId: UUID, @Valid @RequestBody request: TripFuelDecisionRequest): TripResponse

	@Operation(summary = "Cancel a trip; only while it is still PLANNED")
	@PatchMapping("/{tripId}/cancel")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun cancelTrip(@PathVariable tripId: UUID)

}
